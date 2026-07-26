# Generated scaffold validation

- Date: 2026-07-26
- Environment Java: OpenJDK 21.0.10
- Scope: checks possible without downloading Gradle or Minecraft dependencies

## Passed

- POSIX shell syntax for `gradlew` and `scripts/verify-phase0.sh`.
- JSON parsing for all schema and resource JSON files.
- TOML parsing for the Gradle version catalog.
- No trailing whitespace or missing final newlines in tracked text files.
- Java 21 compilation with `-Xlint:all -Werror` for `atlas-api`, `atlas-compiler`,
  `compatibility-api`, both compatibility placeholders, and `test-support`.
- Java syntax compilation for the mod entry points against minimal API stubs.

## Not run in this environment

- Gradle dependency resolution.
- `phase0Check` through Gradle.
- Checkstyle and JUnit execution through Gradle.
- NeoForge `runClient`, `runServer`, `runGameTestServer`, or `runData`.

These remaining checks require a normal development machine with network access; client startup also
requires a graphical environment.
