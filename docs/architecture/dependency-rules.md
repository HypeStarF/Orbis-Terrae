# Module dependency rules

Dependencies point toward stable abstractions.

```text
minecraft-mod ───────────────► atlas-api
minecraft-mod ───────────────► compatibility-api
atlas-compiler ──────────────► atlas-api
compatibility-mekanism ──────► compatibility-api
compatibility-immersive-engineering ─► compatibility-api
test-support ────────────────► atlas-api
```

Forbidden dependencies:

- `atlas-api` to Minecraft, NeoForge, GIS libraries, or another project module.
- `atlas-compiler` to Minecraft or NeoForge.
- `compatibility-api` to optional mods.
- Runtime mod code to compiler-only GIS libraries.
- Common runtime packages to `net.minecraft.client`.
- Compatibility modules to each other.

The rule is intentionally stricter than convenience. Violations require an architecture decision
record before implementation.
