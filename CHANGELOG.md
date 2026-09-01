# Changelog

All notable changes to Retrogen are documented in this file.

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
