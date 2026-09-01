# Retrogen 1.1.1

Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25,
Fabric Loom 1.17.20. Server-side.

This is a hardening release. It contains no schema, configuration, or command
compatibility break relative to 1.1.0. Existing worlds and existing
`retrogen.json` files continue to work without changes. Existing
`world/retrogen/retrogen-state-v1.json` ledgers continue to work without
changes.

## Fixed

- Load configuration and state during `SERVER_STARTING`, before server worlds
  are loaded, so spawn and persistent force-loaded chunks are visible to the
  chunk-load hook on startup.
- Abort a generation pass when its pre-mutation `inProgress` marker cannot be
  persisted, instead of continuing without a durable marker.
- Reject duplicate and null migration-pass entries during configuration
  validation.
- Report unloaded retry and clear targets as waiting for the next chunk load
  instead of claiming they were added to the in-memory queue.
- Remove conflicting `failed` or `inProgress` markers even when a completed
  chunk was already present in the in-memory completion index.
- Do not swallow JVM-level `Error` conditions raised during world generation.

## Changed

- Pin Fabric Loom to `1.17.20` and restrict `fabric.mod.json`'s Minecraft
  predicate to `=26.2` for reproducible builds.
- Use a primitive long set for the in-memory completion index.
- Expand the automated suite to 12 tests, including failed persistence,
  duplicate pass IDs, null passes, and conflicting ledger markers.

## Verification

- `./gradlew clean test build` succeeds.
- 12 automated tests pass (`RetrogenConfigTest`, `RetrogenStateStoreTest`,
  `ChunkKeyTest`).
- Fresh dedicated-server smoke test on Minecraft 26.2 succeeds: startup-loaded
  persistent chunks are picked up automatically, a prepared coal-ore target is
  refilled without a manual retry, and an unloaded retry target is reported as
  waiting for the next chunk load. No Retrogen error or Mixin failure in the
  server log. Clean shutdown.

## Downloads

- [Fabric server mod JAR](https://github.com/0n113/retrogen/raw/v1.1.1/dist/v1.1.1/retrogen-1.1.1.jar)
- [Sources JAR](https://github.com/0n113/retrogen/raw/v1.1.1/dist/v1.1.1/retrogen-1.1.1-sources.jar)
- [Complete source archive](https://github.com/0n113/retrogen/raw/v1.1.1/dist/v1.1.1/retrogen-fabric-26.2-source.zip)
- [SHA-256 checksums](https://github.com/0n113/retrogen/raw/v1.1.1/dist/v1.1.1/SHA256SUMS)

## Known limitations

See `REVIEW-2026-09-01.md` for the full risk register. The most operationally
relevant items still open in 1.1.1:

- The JSON ledger is rewritten in full before each pass execution. Very large
  migrations across hundreds of thousands or millions of chunks can therefore
  create measurable save latency.
- When `requireLoaded3x3` is enabled, blocked chunks are re-added every tick
  without back-off, which can consume the per-tick budget continuously on
  large deferred queues.
- `/retrogen status <pass>` prints the dimension-wide queue length, not a
  pass-scoped one.
- Retrogen does not currently expose commands to list failed or in-progress
  chunks; ledger inspection still requires stopping the server and reading
  the JSON.
- No `fsync` is performed on the ledger or its parent directory around the
  atomic rename, so a host power loss can still lose the most recent write on
  some filesystems.
- `WorldGenRegion#getLevel()` is deprecated in Minecraft 26.2 but is retained
  in this release because the deprecated access remains functionally correct.
