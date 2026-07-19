# Folia production risk audit

## Цель аудита

Bolt хранит приваты блоков и сущностей, поэтому оптимизация под Folia не должна снижать
гарантии сохранности данных. Для этого приоритеты такие:

1. **Durability first:** подтвержденный приват не должен потеряться после штатного рестарта
   или обычного падения JVM/процесса после DB flush.
2. **Region safety:** Bukkit/Folia API вызывается только из корректного region/entity/global
   context.
3. **Hot-path latency:** частые игровые события не должны выполнять SQL, сетевые запросы,
   full scans чанков/мира или тяжелые matcher-операции.
4. **Compatibility:** существующий публичный `BoltAPI`, команды, миграции и storage format
   сохраняются; рискованные изменения делаются через compatibility layer и feature flags.

## Что уже хорошо для надежности

- `SimpleProtectionCache` держит hot-path состояние в памяти и возвращает completed futures,
  поэтому обычные проверки защит не ждут SQL worker.
- `SQLStore` сохраняет defensive snapshots перед постановкой в очередь записи, поэтому поздние
  мутации in-memory объекта не меняют уже поставленную запись.
- SQLite включен в WAL mode с `synchronous=FULL`; это правильный default для приоритета
  сохранности приватных записей над максимальной скоростью записи.
- `onDisable()` явно вызывает `flush().join()`, то есть штатное выключение дожидается
  записи очереди в backing store.
- Для Folia уже есть единый scheduler layer (`FoliaSchedulerService` / `SchedulerUtil`),
  который нужно продолжать использовать вместо прямых scheduler calls в бизнес-логике.

## Узкие места и рекомендуемые оптимизации

| Приоритет | Зона | Риск | Безопасная оптимизация без поломки функционала |
| --- | --- | --- | --- |
| P0 | Storage durability vs write coalescing | Debounce/отложенный flush может потерять последние приваты при hard crash до записи в БД. Для «как банк» это неприемлемо как default. | Не откладывать durable flush после save/remove по умолчанию. Если когда-либо нужен coalescing, делать только opt-in режимом с явным предупреждением и метрикой `pendingSave`; default должен оставаться immediate DB-worker flush. |
| P0 | Error handling в `SQLStore` | Сейчас SQL exceptions печатаются, но caller не получает сигнал о деградации backing store. In-memory cache может считать приват сохраненным, хотя БД запись не прошла. | Добавить health-state storage service: счетчик failed flush, last error, read-only/degraded mode, admin alert, `/bolt admin storage status`. При ошибке flush не удалять запись из очереди до успешной записи или явного quarantine. |
| P0 | Shutdown flush | `onDisable()` блокирует до завершения flush без timeout/диагностики. Если DB зависла, shutdown может зависнуть; если убить процесс — можно потерять очередь. | Добавить bounded wait с подробным логом, повтором, pending counts и emergency dump очереди в файл для восстановления. Не молча продолжать при ошибке. |
| P0 | Region ownership публичного API | `BoltAPI` методы принимают `Block`/`Entity`; сторонний плагин может вызвать их из чужого региона/async и получить Folia crash. | Документировать context contract и в debug/strict режиме включить `FoliaRegionGuard.requireOwned` для публичных block/entity методов. Для async callers добавить отдельные scheduler-aware фасады без изменения старого API. |
| P1 | `findProtections(World, BoundingBox)` | Метод проходит все block protections и entity protections, а для entity вызывает `getServer().getEntity`. На больших базах это O(N) и потенциально небезопасно для Folia context. | Добавить spatial index по world/chunk/region для block protections и отдельный entity index/cache. Entity lookup выполнять только из корректного entity/global context или возвращать DTO без Bukkit entity access. |
| P1 | Команды admin cleanup/expire/find/nearby | Массовые операции могут перебирать большие коллекции защит и/или обращаться к миру. Даже если cache быстрый, работа может занять длинный tick. | Перевести в batch pipeline: async DTO этап + region/entity scheduled batches с configurable batch size/delay и progress/cancel. |
| P1 | Profile lookup в командах | `Profiles` может делать async lookup, а `.join()` в command flow рискует блокировать sender/entity thread при cache miss. | Для команд использовать async profile resolution, а результат отправлять через `scheduler.sender(...)`; табы не должны делать network/profile lookup. |
| P1 | Access resolution для groups/trust | `canAccess` вызывает `loadAccessList(...).join()` и resolver может грузить группы. Сейчас cache completed, но контракт скрывает потенциальное blocking I/O при другой реализации Store. | Разделить hot-path API на sync-cache-only access check и async/full access check. В hot path запрещать backing-store I/O; Store implementations должны явно маркировать cache hits. |
| P1 | Entity lookup/display helpers | `Bukkit.getServer().getEntity(...)` встречается в pagination/display/admin nearby. На Folia это требует осторожного context и может быть дорогим для массовых списков. | Хранить display DTO в `EntityProtection`; для live entity данных делать lazy lookup только по клику/teleport и через entity scheduler/global-safe API. |
| P1 | Matchers/nearby entity lookup | Block matchers в hot events не должны сканировать chunk entities или пересекать regions. | Сохранить `NearbyEntityLookup` как единую точку, не возвращать к `Chunk#getEntities`. Для Folia либо использовать безопасный bounded API после проверки документации, либо оставлять old behavior disabled behind feature flag. |
| P2 | Redstone/hopper hot paths | Частые события могут вызывать extended matcher lookup и создавать allocations. | Оставить direct protection lookup default; extended lookup только opt-in. Добавить micro counters/metrics для hit/miss и duration sampling. |
| P2 | Startup full load | `SimpleProtectionCache` синхронно грузит все защиты/группы/access lists при enable. Большая база удлиняет старт; watchdog обычно еще не ticking, но рестарт будет долгим. | Добавить startup progress logging, sanity checks, optional preflight validation и fail-fast при ошибке БД. Lazy loading допустим только после проектирования durability/consistency модели. |
| P2 | Save queue memory pressure | При недоступной БД очереди `ConcurrentHashMap` могут расти без bound. | Ввести backpressure/degraded mode: лимит pending operations, warning thresholds, admin status, emergency dump. Не отбрасывать приваты. |
| P2 | Static regression coverage | Folia-safe правила легко нарушить новым кодом. | Расширить `checkFoliaMigration`: запрет direct scheduler вне adapter, `Chunk#getEntities`, Bukkit world/entity API в async continuations и blocking `.join()` в listeners/commands hot path. |

## Что делать в первую очередь

1. **Не использовать delayed flush как default.** Для приватного плагина надежность важнее
   уменьшения количества транзакций. Оптимизировать write path нужно через batch внутри уже
   запрошенного immediate DB-worker flush, transaction reuse, prepared statement reuse и health
   monitoring, а не через окно потери данных.
2. **Добавить storage health/degraded mode.** Это главный gap: если БД перестала принимать
   записи, сервер должен loudly fail/alert, а не незаметно жить только на in-memory cache.
3. **Убрать O(N) world/entity scans из интерактивных команд и API.** Нужны индексы и batch
   workflows.
4. **Зафиксировать Folia context contracts.** Public API должен явно говорить, где его можно
   вызывать, а debug guard должен ловить нарушения до production.
5. **Перевести массовые команды в cancellable batch jobs.** Это снизит риск долгих region ticks
   без изменения результата операций.

## Чего не делать

- Не задерживать сохранение новых приватных записей по умолчанию ради throughput.
- Не переносить Bukkit API calls в async executor.
- Не заменять bounded lookup полным сканированием чанков/мира.
- Не менять формат/семантику storage в одном PR с Folia scheduler refactor.
- Не делать оптимизации, которые молча пропускают проверку защиты при ошибке.
