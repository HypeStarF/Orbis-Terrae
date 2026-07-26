# Orbis Terrae

Orbis Terrae is a NeoForge 1.21.1 project for deterministic, offline Earth world generation.
This repository is the Phase 0 foundation: build tooling, module boundaries, CI, static checks,
architecture records, provenance templates, and an empty mod that is safe to load on a dedicated
server.

## Project identity

| Field | Value |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge requested | `21.1.244` |
| NeoForge temporary build pin | `21.1.243` |
| Java | `21` |
| Mod ID | `orbis_terrae` |
| Group | `me.sdmannen` |
| Artifact | `orbis-terrae` |
| Main class | `me.sdmannen.orbis_terrae.OrbisTerrae` |

`21.1.244` was not published in the official NeoForge Maven index when this scaffold was created
on 2026-07-26. ADR-0002 records the temporary pin to `21.1.243`.

## First commands

Linux/macOS:

```bash
chmod +x gradlew
./gradlew clean phase0Check
./gradlew :modules:minecraft-mod:runClient
./gradlew :modules:minecraft-mod:runServer
```

Windows:

```bat
gradlew.bat clean phase0Check
gradlew.bat :modules:minecraft-mod:runClient
gradlew.bat :modules:minecraft-mod:runServer
```

The temporary launcher downloads Gradle 9.2.1 on first use. After the first successful download,
generate the canonical wrapper with `./gradlew wrapper --gradle-version 9.2.1` and commit the
resulting wrapper JAR and scripts. See ADR-0003.

## Phase 0 status

Automated repository checks are implemented. Client and dedicated-server smoke tests must be run
on a development machine with network access and a graphical environment. Follow
[`docs/phase-0/acceptance-checklist.md`](docs/phase-0/acceptance-checklist.md).

## Modules

- `minecraft-mod`: NeoForge entry point and run configurations.
- `atlas-api`: Minecraft-independent atlas contracts and coordinate value objects.
- `atlas-compiler`: command-line preprocessing application placeholder.
- `compatibility-api`: stable compatibility contracts.
- `compatibility-mekanism`: optional integration placeholder.
- `compatibility-immersive-engineering`: optional integration placeholder.
- `test-support`: shared deterministic fixtures and test helpers.

## Rules

- Java 21 only.
- No Minecraft classes in `atlas-api` or `atlas-compiler`.
- No `net.minecraft.client` references outside the `client` package.
- No raw GIS processing in the runtime mod.
- No dataset is approved for redistribution until the licensing matrix says `APPROVED`.
