# Schema documents

The schema directory contains versioned data contracts introduced across the project phases.

- `world-profile.schema.json`: editable pre-world-creation profile envelope.
- `world-manifest.schema.json`: canonical immutable world identity schema.
- `world-manifest-v1.schema.json`: strict Phase 2 manifest contract, including profile, seed, and spawn settings.
- `atlas-manifest.schema.json`: early atlas identity envelope.
- `atlas-manifest-v1.schema.json`: strict Phase 1 atlas manifest contract.
- `dataset-provenance.schema.json`: evidence required for dataset use.

World-defining values must be copied into the immutable manifest. Reloadable profile or datapack changes must not
silently alter an existing world's generator identity.
