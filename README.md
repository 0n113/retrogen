# Retrogen

Server-side Fabric 26.2 mod for controlled, idempotent retroactive placed-feature
generation in existing Minecraft chunks.

Retrogen re-runs selected registered `PlacedFeature` entries in already generated
chunks. Each named migration pass is recorded per dimension and chunk, making
subsequent loads idempotent. Structure placement is explicitly suppressed during
a retroactive pass.

The mod defaults to disabled, requires an already loaded 3x3 chunk neighborhood,
limits work per tick, writes its ledger atomically, and blocks automatic retries
after interrupted passes.

## Requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 or newer
- Fabric API 0.158.0+26.2 or newer
- Gradle 9.5.1 through the included wrapper

## Build

```bash
./gradlew clean build
```

The mod JAR is written to `build/libs/retrogen-1.1.1.jar`.

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 26.2.
2. Copy `retrogen-1.1.1.jar` into the server's `mods` directory.
3. Start the server once to create `config/retrogen.json`.
4. Stop the server, configure exact placed-feature identifiers, and make a world
   backup.
5. Start with `dryRun` enabled before allowing world changes.

## Configuration

On first server start the mod writes `config/retrogen.json` and remains disabled.
Back up the world, replace the example feature namespace, then set `enabled` to
`true`.

```json
{
  "schemaVersion": 1,
  "enabled": true,
  "dryRun": false,
  "chunksPerTick": 1,
  "saveIntervalTicks": 100,
  "requireLoaded3x3": true,
  "passes": [
    {
      "id": "new_ores_v1",
      "enabled": true,
      "dimensions": ["minecraft:overworld"],
      "includePlacedFeatures": [
        "examplemod:tin_ore",
        "examplemod:lead_ore"
      ],
      "excludePlacedFeatures": [],
      "markNewChunksComplete": true,
      "retryFailed": false
    }
  ]
}
```

Patterns may be exact identifiers, namespace wildcards such as `examplemod:*`,
or the global wildcard `*`. Prefer exact identifiers. A changed feature set
must use a new pass ID; pass IDs are the migration/version boundary. Pass IDs
must be unique, and startup fails safely if duplicate or null pass entries are
found.

## State format

Progress is stored inside the world at
`retrogen/retrogen-state-v1.json`. Writes use a temporary file and an atomic
rename where supported.

```json
{
  "schemaVersion": 1,
  "dimensions": {
    "minecraft:overworld": {
      "passes": {
        "new_ores_v1": {
          "completed": ["-2,7", "0,0", "4,11"],
          "failed": {
            "8,3": "ReportedException: Feature placement"
          },
          "inProgress": {
            "12,-5": "2026-09-01T14:44:58Z"
          },
          "lastUpdated": "2026-09-01T14:45:00Z"
        }
      }
    }
  }
}
```

- `completed` prevents duplicate execution of a pass for the same chunk.
- `failed` prevents a crash loop unless `retryFailed` is enabled.
- `inProgress` is flushed before world mutation. If the server stops mid-pass,
  the marker blocks an automatic retry because the chunk may be partly changed.
  Inspect a backup and remove that one marker only if a retry is intentional.
- If that pre-mutation write fails, Retrogen aborts the pass without changing
  the world and records an error instead of running untracked.
- Newly generated chunks are marked complete by the population hook when
  `markNewChunksComplete` is true.
- Do not delete the state file while any pass remains enabled.

## Execution model

Configuration and state are loaded before server worlds, allowing the Fabric
chunk-load event to observe spawn and persistent force-loaded chunks during
startup. The load event only enqueues work. At the end of a server-world tick,
the runtime processes at most `chunksPerTick` entries. With
`requireLoaded3x3`, all nine chunks needed by vanilla decoration must already be
loaded; Retrogen never loads or generates neighbors merely to satisfy a pass.

`ChunkGeneratorMixin` observes completion of normal biome decoration.
`PlacedFeatureMixin` filters vanilla's decoration loop to the configured feature
IDs while a retrogen context is active. `StructureStartMixin` blocks structure
pieces from being placed a second time.

## Server commands

All commands require Minecraft's gamemaster permission level. Pass IDs support
tab completion.

```text
/retrogen status
/retrogen status <pass>
/retrogen retry <pass>
/retrogen retry <pass> <chunkX> <chunkZ>
/retrogen clear <pass> <chunkX> <chunkZ> confirm
```

- `status` prints whether Retrogen is enabled, the current dimension, queue
  length, and the `completed`, `failed`, and `inProgress` counts. Without a pass
  argument it prints one line for every configured pass.
- `retry` removes only `failed` and crash-left `inProgress` markers, then queues
  the chunk. With no coordinates, the command source's current chunk is used.
  A completed pass is not reset by this command. If an explicitly addressed
  chunk is not loaded, it remains pending and runs when that chunk is next
  loaded.
- `clear` removes every ledger entry for exactly one pass and chunk, including
  `completed`. The final `confirm` literal is mandatory. If the pass is active
  in the current dimension, a loaded chunk is queued immediately; an unloaded
  chunk runs on its next load.

Chunk coordinates are chunk coordinates, not block coordinates. For example,
block position `160, -32` is chunk `10, -2`.

## Operational cautions

Retrogen cannot know whether a feature from an older mod version already exists
in a chunk. The pass ledger prevents Retrogen from running the same migration
twice, but the first pass can still overlap old blocks or player builds
according to the selected feature's own placement rules. Always test a copied
world and use `dryRun` before enabling writes.

The JSON ledger is intentionally human-readable but is rewritten atomically.
Very large migrations can therefore cause increasing save latency as the
completed set grows. Roll out large worlds in measured batches and monitor tick
time and ledger size.
