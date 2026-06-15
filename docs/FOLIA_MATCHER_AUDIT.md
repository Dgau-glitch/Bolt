# Folia matcher and hot-path audit

## Scheduler context contract

- Block matchers are synchronous region operations. Callers must already own the region containing the input block.
- Entity matchers are synchronous entity operations. Callers must already own the entity scheduler context for the input entity.
- Matchers must not load chunks, scan all entities in a chunk, block on async work, or cross to another region.
- Matchers that need attached entities must use `NearbyEntityLookup`, which performs a bounded bounding-box lookup and delegates to the server API instead of `Chunk#getEntities()`.

## Redstone path

`BlockRedstoneEvent` uses direct block protection lookup by default. Extended matcher lookup for supporting blocks/entities is disabled unless `settings.redstone-extended-protection-lookup` is enabled. This avoids item-frame/painting/leash-knot nearby entity lookups in the hot redstone tick path.

## Nearby entity matchers

| Matcher | Lookup | Contract |
| --- | --- | --- |
| `ItemFrameMatcher` | `NearbyEntityLookup` with a 0.5-block expanded bounding box | Region-owned block context; bounded attached-entity lookup only |
| `PaintingMatcher` | `NearbyEntityLookup` with a horizontal 0.5-block expanded bounding box | Region-owned block context; bounded attached-entity lookup only |
| `LeashKnotMatcher` | `NearbyEntityLookup` on the fence block bounding box | Region-owned block context; bounded attached-entity lookup only |

## Block-only matchers

The remaining block matchers only inspect the input block, adjacent blocks, block data, tags, or bounded block structures. They remain synchronous region operations and must not be called from async code.

## Static regression checks

The Gradle `checkFoliaMigration` task fails the build when production source reintroduces direct Bukkit scheduler usage outside `DefaultFoliaSchedulerService` or `Chunk#getEntities()`-style scans.
