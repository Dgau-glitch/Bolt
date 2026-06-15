# План полного перехода Bolt на Folia API 1.21.11

## Цель

Перевести Bukkit/Paper-слой Bolt на полноценную компиляцию и работу через Folia API `1.21.11-R0.1-SNAPSHOT`, сохранив текущий функционал защит, команд, миграций и хранилищ. Миграция должна убрать прямую зависимость от Bukkit main-thread модели и заменить ее явной моделью Folia: global region, region scheduler, entity scheduler и async scheduler.

Целевая зависимость:

```kotlin
compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
```

## Опорные правила Folia для этой миграции

- Не использовать Bukkit scheduler как универсальный «main thread» executor.
- Любой доступ к `Block`, `World`, `Chunk`, `Entity`, `Inventory`, `BlockState` и соседним игровым объектам выполнять в корректном регионе или через `EntityScheduler`.
- Глобальные операции без конкретной локации выполнять только через `GlobalRegionScheduler`, а I/O и CPU-heavy работу — через async executor без доступа к Bukkit API.
- Результаты async-операций возвращать в игру через region/entity/global scheduler в зависимости от владельца данных.
- Не сканировать чанки вручную через `Chunk#getEntities()` в горячих обработчиках событий; для точечных поисков использовать API поиска по bounding box или специализированный индекс/кэш защит.

## Текущая картина проекта

- Модули проекта: `bolt-common`, `bolt-bukkit`, `bolt-paper`, `bolt-folia`.
- Основной runnable jar собирается из `bolt-bukkit`; `bolt-folia` сейчас подключается как implementation-зависимость и содержит только утилиты Folia (`FoliaUtil`, `SchedulerUtil`).
- `plugin.yml` уже содержит `folia-supported: true`, но проект пока не является полностью Folia-first, потому что значительная часть кода остается Bukkit/Paper-oriented.
- `bolt-folia` сейчас компилируется против устаревшего `dev.folia:folia-api:1.20.4-R0.1-SNAPSHOT`.
- `bolt-bukkit` сейчас компилируется против `io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT`.
- В коде есть смешение игровых операций, async storage/profile operations и callback/scheduler логики, что осложняет гарантию region-correct доступа.

## Найденные зоны риска

| Зона | Файлы/пакеты | Риск для Folia | Что нужно сделать |
| --- | --- | --- | --- |
| Сборка и зависимости | `settings.gradle.kts`, `bukkit/build.gradle.kts`, `folia/build.gradle.kts`, `plugin.yml` | Folia API устарел, Bukkit module остается Paper-first | Обновить Folia API до 1.21.11, решить структуру модулей: Folia-first jar или отдельный совместимый слой |
| Scheduler abstraction | `folia/src/main/java/org/popcraft/bolt/util/SchedulerUtil.java` | API слишком узкий: нет cancel handle, async scheduler, entity-owned scheduling, region assertions | Ввести сервис планирования с понятными методами `global`, `region`, `entity`, `async`, `backToSender` |
| Entity/block matchers | `bukkit/src/main/java/org/popcraft/bolt/matcher/**` | Поиск entity/block рядом может выполняться в горячих событиях и пересекать регионы | Разделить быстрые matcher-проверки и region-safe nearby lookup; добавить тесты/бенчмарки для redstone path |
| Redstone/block events | `BlockListener` | Частые события вызывают `findProtection` и matchers; любой тяжелый lookup блокирует region tick | Оптимизировать путь redstone: прямые проверки кэша, отсутствие чанковых сканов, минимизация allocation |
| Entity events | `EntityListener`, `InteractionListener`, `InventoryListener`, adapters | События могут включать entity access, inventory access и callbacks | Проверить каждый handler на owner-region correctness и перенос продолжений в entity scheduler |
| Commands | `command/**` | Async `CompletableFuture` продолжения иногда возвращаются к sender через общий scheduler, иногда нет | Унифицировать command execution pipeline и tab-completion с permission-first фильтрацией |
| Async storage | `SQLStore`, `SimpleProtectionCache`, migrations | I/O выполняется async, но completion paths могут обращаться к Bukkit API не из региона | Ввести явный boundary: storage DTO async, Bukkit API только после scheduler hop |
| Migrations/cleanup | `AdminCleanup`, `LocketteMigration`, `LWCMigration`, `BoltMigration` | Массовые обходы чанков и world access потенциально опасны для Folia | Перевести chunk/world обходы на region scheduler batches и throttle-конфиги |
| Door utility | `Doors` | Использует delayed Bukkit scheduler после изменения двери | Перенести на region scheduler по location с отменяемым task handle |
| Profiles/cache | `Profiles`, `SimpleProfileCache`, `PlayerListener` | Profile lookup async, cache writes async; важно не трогать Bukkit API в async continuation | Разделить network/profile lookup, cache I/O и game-thread delivery |
| API/events Bolt | `BoltAPI`, `BoltPlugin`, `event/**` | Внутренние события Bolt могут быть вызваны из разных scheduler contexts | Документировать context каждого API метода и добавить scheduler-aware фасад |

## Пошаговый план миграции

Каждый пункт ниже рассчитан как отдельная задача на одно сообщение пользователя. Не переходить к следующему пункту, пока предыдущий не собран и не проверен.

### 1. Обновить build-конфигурацию под Folia API 1.21.11

**Задача:** обновить Gradle-зависимости и metadata без изменения runtime-логики.

- Заменить Folia dependency на `compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")`.
- Проверить, нужен ли `paper-api` в `bolt-bukkit` после перехода или Folia API должен стать compileOnly в Folia-oriented модуле.
- Обновить `api-version`/game versions, если это требуется целевым Minecraft/Folia 1.21.11.
- Запустить `./gradlew clean build`.

**Критерий готовности:** проект компилируется с Folia API 1.21.11, без функциональных изменений.

### 2. Спроектировать и добавить FoliaSchedulerService

**Задача:** заменить статическую утилиту планирования расширяемым сервисом.

- Создать интерфейс планировщика в модульном стиле: `global`, `region(Location)`, `entity(Entity)`, `async`, `delayed`, `repeating`, `cancel`.
- Реализацию Folia держать отдельно от общей бизнес-логики.
- Сохранить compatibility facade для текущего `SchedulerUtil`, чтобы не переписывать весь код сразу.
- Добавить единый способ возвращать completion команд к `CommandSender`.

**Критерий готовности:** все текущие вызовы `SchedulerUtil` продолжают работать, но новая логика доступна через сервис.

### 3. Перевести все текущие вызовы Bukkit scheduler на новый сервис

**Задача:** убрать прямые `Bukkit.getScheduler()` / `scheduleSync*` из production-кода.

- Переписать `Doors` delayed task на region scheduling по location.
- Переписать fallback paths в `SchedulerUtil`/facade.
- Найти и устранить все прямые вызовы scheduler API, кроме единственной реализации compatibility layer.

**Критерий готовности:** поиск по `Bukkit.getScheduler`, `getServer().getScheduler`, `scheduleSync` не находит вызовов вне scheduler adapter.

### 4. Разделить async I/O и Bukkit API boundaries

**Задача:** гарантировать, что async continuation не трогает Bukkit API напрямую.

- Проверить `SQLStore`, `SimpleProfileCache`, migration classes, command classes с `CompletableFuture`.
- Ввести helper `thenAcceptRegion`, `thenAcceptEntity`, `thenAcceptSender`, `exceptionallyToSender`.
- Все сообщения игрокам, доступ к миру, блокам, entity и inventory выполнять после scheduler hop.

**Критерий готовности:** все `CompletableFuture.thenRun/thenAccept/whenComplete` с Bukkit side effects явно используют scheduler service.

### 5. Переработать поиск защит для горячего redstone path

**Задача:** исключить тяжелые nearby entity/block операции из `BlockRedstoneEvent`.

- Разделить `findProtection` на cheap direct lookup и optional extended matcher lookup.
- Для redstone сначала проверять direct block protection cache без entity scan.
- Для item frame/painting/leash knot добавить bounded region-safe lookup только там, где он реально нужен.
- Добавить конфиг/throttle для extended lookup в redstone path, если сохранение старого поведения требует дорогой проверки.

**Критерий готовности:** redstone path не вызывает chunk entity scan и не делает дорогостоящий lookup без необходимости.

### 6. Провести аудит block matchers и entity matchers

**Задача:** сделать matchers Folia-safe и быстрыми.

- Проверить каждый `matcher/block/*` на доступ к соседним блокам, bounding boxes, entity lookup.
- Вынести повторяемые nearby entity операции в отдельный `NearbyEntityLookup` service.
- Добавить ограничения: не грузить чанки, не переходить в чужой регион, не выполнять full chunk scans.
- Задокументировать для каждого matcher: ожидаемый scheduler context и допустимая стоимость.

**Критерий готовности:** все matcher-операции имеют явный context contract и общий reusable lookup layer.

### 7. Перевести cleanup и migration workflows на batch region scheduling

**Задача:** безопасно выполнять массовый обход мира на Folia.

- `AdminCleanup`, `LocketteMigration`, `LWCMigration`, `BoltMigration` разделить на async I/O этап и region batch этап.
- Использовать ограниченный batch size и конфигурируемую задержку между batch.
- Не использовать `Thread.sleep` как механизм throttling gameplay work.
- Не обращаться к chunk/world API из common async pool.

**Критерий готовности:** массовые операции не держат region tick дольше одного batch и могут быть остановлены/возобновлены.

### 8. Унифицировать command pipeline и tab-completion

**Задача:** привести команды к Folia-safe исполнению и требованиям permission-first completion.

- Проверять permission до формирования tab suggestions.
- Скрывать недоступные команды и аргументы от игроков без прав.
- Асинхронные команды возвращать результат через sender/entity/global scheduler.
- Для команд, завязанных на позицию игрока, использовать player/entity scheduler.

**Критерий готовности:** все команды имеют быстрый context-aware tab-completion и не выполняют Bukkit API из async continuation.

### 9. Проверить listeners на region ownership и entity ownership

**Задача:** сделать обработчики событий безопасными для Folia region threading.

- Аудит `BlockListener`, `EntityListener`, `InteractionListener`, `InventoryListener`, `PlayerListener`.
- Любые delayed действия после события переносить в region/entity scheduler.
- Не сохранять mutable Bukkit objects для дальнейшего async использования; сохранять только immutable DTO: UUID, location snapshot, material/type names.

**Критерий готовности:** каждый listener либо полностью выполняется в event region context, либо явно reschedule-ит продолжение.

### 10. Определить публичный Bolt API contract для Folia

**Задача:** документировать и защитить API от неправильного вызова из чужого региона.

- Для `BoltAPI` и публичных методов `BoltPlugin` описать scheduler context: sync region, async-safe, global-only.
- Добавить guard/assert helper для dev/debug режима: проверка region ownership по location/entity.
- Не менять существующие интерфейсы без compatibility layer.

**Критерий готовности:** сторонний код понимает, где можно вызывать API, а debug режим ловит нарушения context.

### 11. Обновить тесты и статические проверки для Folia migration

**Задача:** добавить программные проверки, предотвращающие регрессии.

- Добавить Gradle task или simple script, который запрещает прямые `Bukkit.getScheduler()` вне adapter.
- Добавить проверку отсутствия `Chunk#getEntities()` в hot path.
- Добавить unit tests для scheduler facade там, где возможно без сервера.
- В CI запускать `./gradlew clean build`.

**Критерий готовности:** регрессии по scheduler и chunk/entity scan ловятся до PR review.

### 12. Провести runtime smoke-test на Folia 1.21.11

**Задача:** проверить миграцию в реальном серверном окружении.

- Собрать jar и запустить на Folia 1.21.11 с Java 21/25.
- Проверить: lock/unlock, redstone protected blocks, item frames, paintings, doors, hoppers, inventory access, admin cleanup dry-run, profile lookup.
- Проверить watchdog logs на отсутствие долгих region ticks.
- Зафиксировать сценарии в `docs/` как manual QA checklist.

**Критерий готовности:** базовые сценарии работают на Folia 1.21.11 без watchdog warnings.

## Рекомендуемый порядок PR

1. Build dependency update only.
2. Scheduler service abstraction.
3. Replace direct scheduler calls.
4. Async boundary cleanup for commands/profile/storage.
5. Redstone hot path optimization.
6. Matcher lookup service.
7. Migration/cleanup batching.
8. Command tab-completion permission audit.
9. Listener ownership audit.
10. API contract and debug guards.
11. Static checks/tests.
12. Runtime QA checklist.

## Что не делать в одном PR

- Не смешивать обновление зависимости, массовый scheduler refactor и оптимизацию redstone path в одном изменении.
- Не переносить Bukkit API calls в `CompletableFuture.runAsync` ради «быстродействия».
- Не добавлять новые прямые обращения к Folia scheduler по всему коду; все должно идти через один сервис/adapter.
- Не использовать ручные full chunk/entity scans как замену нативным region-safe API.
