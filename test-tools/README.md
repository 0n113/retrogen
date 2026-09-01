# Retrogen local test server

This directory contains the minimal Source RCON client used by the integration
tests. The prepared Loom server lives in `../run`.

## Start

From the repository root:

```bash
./gradlew runServer
```

Wait for `Done`, then run:

```bash
python test-tools/rcon.py "retrogen status"
python test-tools/rcon.py "retrogen status coal_v1"
```

Stop cleanly with:

```bash
python test-tools/rcon.py "stop"
```

## Safety

The included server is strictly local:

- bind address: `127.0.0.1`
- Minecraft port: `25565`
- RCON port: `25575`
- authentication: disabled for the isolated integration test

Never expose these settings to another host. Change the RCON password and
enable online authentication before adapting the setup for any shared server.
The checked-in `run` directory is ignored by Git and is included only in the
separate test-server archive.

See `../TEST-REPORT.md` for the complete test matrix and expected results.
