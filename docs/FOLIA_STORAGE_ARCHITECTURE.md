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
- `SQLStore` save/remove methods only enqueue snapshots in concurrent maps; they do not schedule extra tiny tasks per save.
- The single DB worker performs reads, periodic flushes, reconnects, and explicit `flush()` calls.
- Enqueued values are defensive copies so later in-memory mutations cannot race with the DB worker serialization step.
- `pendingSave()` reads queue sizes directly and does not block a region thread waiting on the DB worker.
