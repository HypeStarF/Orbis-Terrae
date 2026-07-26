# ADR-0001: Use a multi-module Gradle repository

- Status: Accepted
- Date: 2026-07-26

## Context

Orbis Terrae needs a Minecraft runtime mod, a Minecraft-independent atlas reader, an external atlas
compiler, optional compatibility integrations, and reusable test fixtures. Runtime GIS dependencies
would increase startup cost, distribution size, licensing complexity, and failure modes.

## Decision

Use separate Gradle modules with dependency direction documented in
`docs/architecture/dependency-rules.md`. `atlas-api` must remain a pure Java library. The compiler
may later depend on GIS tooling, but those dependencies must never appear on the mod runtime
classpath.

## Consequences

- The repository has more build files and explicit interfaces.
- Pure atlas logic can be tested without launching Minecraft.
- Optional integrations do not force Mekanism or Immersive Engineering into the core artifact.
- Public APIs must be designed deliberately rather than exposing implementation classes.
