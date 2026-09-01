# Changelog

All notable changes to Retrogen are documented in this file.

## 1.1.1 - 2026-09-01

### Fixed

- Load configuration and state during `SERVER_STARTING` so spawn and persistent
  force-loaded chunks are visible to the chunk-load hook.
- Abort a generation pass when its pre-mutation in-progress marker cannot be
  persisted.
- Reject duplicate and null migration-pass entries during config validation.
- Report unloaded retry/clear targets as waiting for the next chunk load instead
  of claiming they were added to the in-memory queue.
- Remove conflicting failed or in-progress markers even when a completed chunk
  was already present in the in-memory index.
- Do not swallow JVM-level `Error` conditions raised during world generation.

### Changed

- Pin Fabric Loom to 1.17.20 and restrict mod metadata to exactly Minecraft
  26.2.
- Use a primitive long set for the in-memory completion index.
- Expand the automated suite to 12 tests, including failed persistence,
  duplicate pass IDs, null passes, and conflicting ledger markers.

## 1.1.0 - 2026-09-01

### Added

- `/retrogen status [pass]` for queue and ledger summaries.
- `/retrogen retry <pass> [chunkX chunkZ]` to unlock failed or interrupted
  chunks and queue another attempt.
- `/retrogen clear <pass> <chunkX> <chunkZ> confirm` to reset all ledger state
  for one pass and chunk.
- Tab completion for configured pass IDs.
- Persistent state-store tests covering retry and clear behavior.

### Safety

- Commands require Minecraft's gamemaster permission level.
- Completed chunks cannot be reset through `retry`.
- Clearing completed state requires explicit chunk coordinates and the final
  `confirm` literal.
- State changes are flushed before a cleared or retried chunk is queued.

## 1.0.0 - 2026-09-01

### Added

- Controlled retroactive generation of selected registered placed features.
- Per-dimension, per-pass, per-chunk completion ledger.
- Atomic JSON state persistence with completed, failed, and in-progress states.
- Crash-safe duplicate protection through pre-generation in-progress markers.
- Loaded 3x3-neighborhood requirement and configurable chunks-per-tick limit.
- Dry-run mode, new-chunk population hook, and structure-placement suppression.
