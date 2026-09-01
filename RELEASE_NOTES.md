# Retrogen 1.1.0

Retrogen is a server-side Fabric 26.2 mod for controlled, idempotent
retroactive placed-feature generation in existing Minecraft chunks.

## Highlights

- Select exact placed-feature IDs or namespace wildcards per migration pass.
- Track completed, failed, and interrupted chunks independently per dimension.
- Prevent duplicate automatic retries with an atomically persisted in-progress
  marker.
- Suppress structure placement during retroactive feature generation.
- Require an already loaded 3x3 neighborhood and throttle work per tick.
- Inspect and administer the runtime with `/retrogen status`, `retry`, and
  confirmation-protected `clear` commands.

## Commands

```text
/retrogen status [pass]
/retrogen retry <pass> [chunkX chunkZ]
/retrogen clear <pass> <chunkX> <chunkZ> confirm
```

All commands require Minecraft's gamemaster permission level.

## Requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 or newer
- Fabric API 0.158.0+26.2 or newer

## Upgrade

Replace the previous JAR while the server is stopped. Configuration and
`retrogen-state-v1.json` remain compatible with version 1.0.0.

Back up the world and use `dryRun` before enabling a new migration pass.
