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

Run the complete verification suite before pushing or merging:

```bash
./gradlew clean allChecks --no-configuration-cache --warning-mode=fail
```

Configuration caching is intentionally disabled for verification. Build caching remains enabled.
Deprecation warnings are treated as failures so Gradle 10 migration risks are caught early.

For focused feedback during development, use the narrowest applicable task:

```bash
./gradlew phase0Check --no-configuration-cache --warning-mode=fail
./gradlew phase1Check --no-configuration-cache --warning-mode=fail
./gradlew phase2FoundationCheck --no-configuration-cache --warning-mode=fail
```

`phase0Check` covers foundation regressions and server-safety boundaries. `phase1Check` covers the atlas API
and compiler. `phase2FoundationCheck` covers the current world-profile, projection, vertical-curve, and
manifest contracts. The standard Gradle `check` task and the explicit `allChecks` alias both run the complete
multi-module verification graph.

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
./gradlew allChecks --no-configuration-cache --warning-mode=fail
```

`format` removes trailing whitespace and ensures final newlines. Checkstyle, compiler linting, tests, and
all subproject verification tasks run as part of `allChecks`.
