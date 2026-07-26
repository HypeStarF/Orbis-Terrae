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
./gradlew clean phase0Check
```

## IDE import

Open the repository root as a Gradle project. Allow Gradle to finish downloading dependencies and
sources. Do not import an individual module by itself.

## Run configurations

```bash
./gradlew :modules:minecraft-mod:runClient
./gradlew :modules:minecraft-mod:runServer
./gradlew :modules:minecraft-mod:runData
```

The first dedicated-server launch may create an EULA file and stop. Read it, set `eula=true` only
when you accept the Minecraft EULA, and launch again.

## Formatting and checks

```bash
./gradlew format
./gradlew phase0Check
```

`format` removes trailing whitespace and ensures final newlines. Checkstyle and compiler linting run
as part of `phase0Check`.
