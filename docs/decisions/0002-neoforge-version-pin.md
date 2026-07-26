# ADR-0002: Temporarily pin NeoForge 21.1.243

- Status: Accepted, temporary
- Date: 2026-07-26

## Context

The approved project specification requests NeoForge `21.1.244`. The official NeoForge Maven index
contains 1.21.1 releases through `21.1.243`, but no `21.1.244` artifact. A Gradle dependency on an
unpublished version cannot resolve.

## Decision

Keep `requested_neo_version=21.1.244` for traceability and set the buildable `neo_version` to
`21.1.243`. Do not silently change this pin. Upgrade only after the requested artifact is published
or the project owner approves another version, and only after client/server smoke tests pass.

## Consequences

- Phase 0 can be built with a published NeoForge artifact.
- The repository visibly records the deviation from the approved specification.
- A later upgrade is a controlled change rather than an unnoticed version drift.
