# Developer setup

## Requirements

- 64-bit Java 21 JDK
- Git
- Internet access for the first Gradle and dependency download
- IntelliJ IDEA recommended, although command-line builds are authoritative

## Verify Java

```bash
java -version
```

The reported major version must be 21.

## Build and test

```bash
./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail
```

Configuration caching is intentionally disabled for Phase 0 verification. Build caching remains
enabled. Deprecation warnings are treated as failures so Gradle 10 migration risks are caught early.

## IDE import

Open the repository root as a Gradle project. Allow Gradle to finish downloading dependencies and
sources. Do not import an individual module by itself.

## Run configurations

```bash
./gradlew :modules:minecraft-mod:runClient --no-configuration-cache
./gradlew :modules:minecraft-mod:runServer --no-configuration-cache
./gradlew :modules:minecraft-mod:runData --no-configuration-cache
```

The first dedicated-server launch may create an EULA file and stop. Read it, set `eula=true` only
when you accept the Minecraft EULA, and launch again.

## Formatting and checks

```bash
./gradlew format --no-configuration-cache
./gradlew phase0Check --no-configuration-cache --warning-mode=fail
```

`format` removes trailing whitespace and ensures final newlines. Checkstyle and compiler linting run
as part of `phase0Check`.
