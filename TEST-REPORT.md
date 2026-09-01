# Retrogen 1.1.0 Integration Test Report

Test date: 2026-09-01  
Target: Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2,
Java 25, Fabric Loom 1.17.20

## Result

All tested mod functions passed. The final Gradle run completed successfully
with 9 tests, 0 failures, 0 errors, and 0 skipped tests. Eight isolated
dedicated-server phases completed without a server error, uncaught exception,
or Mixin application failure.

One command UX defect was found and fixed during testing:
`/retrogen status <unknown-pass>` previously returned a synthetic all-zero
status. It now reports `Unknown Retrogen pass`.

## Dedicated-server matrix

| Area | Test | Result |
| --- | --- | --- |
| Bootstrap | Start with no config and Retrogen disabled | Pass; default config created |
| Mixins | Dedicated-server startup and all three Mixin classes | Pass; no application failures |
| Status | Global and per-pass counters | Pass |
| Status error | Unknown pass ID | Pass after local fix |
| Retry errors | Unknown, inactive-dimension, and completed pass | Pass; rejected safely |
| Clear safety | Missing final `confirm` literal | Pass; command rejected by parser |
| Overworld generation | `minecraft:ore_coal_upper` in a stone-filled old chunk | Pass; 347 blocks generated |
| Feature exclusion | Same feature present in include and exclude lists | Pass; 0 blocks generated |
| Duplicate prevention | Reload completed chunk and retry completed pass | Pass; 0 duplicate blocks |
| Clear execution | Confirmed clear of a completed chunk | Pass; state reset and 347 blocks regenerated |
| Queue throttling | Four chunks per tick | Pass; queue drained and counters converged |
| 3x3 safety | Missing fully loaded neighborhood | Pass; work remained queued |
| Natural population | Newly generated chunks with `markNewChunksComplete` | Pass; ledger updated |
| Dimension filter | Overworld-only pass invoked in Nether | Pass; rejected |
| Nether generation | `minecraft:ore_quartz_nether` after clearing old quartz | Pass; 47 blocks generated |
| Failure recovery | Persisted synthetic `failed` marker | Pass; visible after restart and cleared by retry |
| Crash recovery | Persisted synthetic `inProgress` marker | Pass; blocked automatic work and cleared by retry |
| Atomic persistence | Save, stop, reload, clear, and retry cycles | Pass |
| Dry run | Cleared pending chunk with `dryRun=true` | Pass; logged intent, changed 0 blocks, wrote no completion |
| Clean shutdown | RCON `stop` and final state save | Pass in every phase |

Block counts are deterministic for the fixed test seed and prepared chunks;
they are evidence that the selected placed feature executed, not compatibility
guarantees for other seeds or datapacks.

## Automated regression suite

- `RetrogenConfigTest`: limit clamping, schema/pass validation, namespace
  wildcards, exact matches, and exclusions.
- `ChunkKeyTest`: positive and negative chunk-coordinate packing.
- `RetrogenStateStoreTest`: completed-state persistence, clear behavior,
  failed markers, and crash-left in-progress markers.

Run the complete suite and build with:

```bash
./gradlew clean test build
```

## Test-server notes

The server binds only to `127.0.0.1`, runs in offline mode, and uses a
test-only RCON password. Do not expose this configuration to a network.
`pause-when-empty-seconds=-1` is intentional so a headless server continues
processing queues without a connected player.

Start the prepared server from the project root:

```bash
./gradlew runServer
```

Send a local command with:

```bash
python test-tools/rcon.py "retrogen status"
```

The retained test world is `run/retrogen-test-world`. Its current config is
enabled with `dryRun=false` and `requireLoaded3x3=false` to support automated
headless execution. Production deployments should normally restore
`requireLoaded3x3=true`, use exact feature IDs, enable dry-run first, and work
from a world backup.

## Non-failing build notices

- Gradle reports plugin/API deprecations relevant to a future Gradle 10
  migration.
- `RetrogenHooks` currently calls a Minecraft API marked deprecated by the
  mappings. It compiles and passed the Minecraft 26.2 dedicated-server tests.

