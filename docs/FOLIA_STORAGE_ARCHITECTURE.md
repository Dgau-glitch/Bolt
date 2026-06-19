# Folia storage write path

Bolt's Folia-safe storage path follows this model:

```text
Folia region/entity thread
        ↓
mutates in-memory protection/group/access state
        ↓
enqueues immutable storage snapshots
        ↓
single Bolt DB Worker thread
        ↓
SQLite/MySQL flush batches
```

## Rules

- Region/entity threads must not perform SQL work.
- `SimpleProtectionCache` owns the in-memory state used by hot paths.
- `SQLStore` save/remove methods only enqueue snapshots in concurrent maps; they do not block the region thread on SQL.
- Every enqueue requests an immediate DB-worker flush; the periodic flush remains a safety net, not the primary durability path.
- SQLite uses WAL journaling with `synchronous=FULL` so committed flush batches survive normal process/server crashes.
- The single DB worker performs reads, immediate/periodic flushes, reconnects, and explicit `flush()` calls.
- Enqueued values are defensive copies so later in-memory mutations cannot race with the DB worker serialization step.
- `pendingSave()` reads queue sizes directly and does not block a region thread waiting on the DB worker.

## Durability policy

Protection writes are safety-critical. New lock/unlock/group/access mutations must request an
immediate DB-worker flush by default; the periodic flush is only a safety net. Do not introduce
default delayed/debounced flush windows for production storage, because a hard crash inside that
window can lose the most recent protection changes. Throughput optimizations should instead focus
on batching the already-requested flush, statement reuse, health reporting, and explicit opt-in
trade-offs with warnings.
