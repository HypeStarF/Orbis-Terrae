# Atlas manifest version 1

The atlas manifest is the authoritative catalogue for one compiled Orbis Terrae atlas. It records
which geographic area the atlas covers, which binary tile layers exist, how those tile files are
encoded and where the source data came from.

The JSON Schema at `docs/schemas/atlas-manifest.schema.json`, the Java model in `AtlasManifest`, the
strict `AtlasManifestJson` codec and the fixture at
`atlas/test-fixtures/manifest-v1/atlas-manifest.json` are required to describe the same contract.
Tests fail when their property sets or enum values drift apart.

## Top-level fields

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Manifest contract version. Version 1 requires the value `1`. |
| `atlasId` | Stable lowercase atlas identifier. |
| `atlasVersion` | Version of the compiled atlas contents. |
| `compilerVersion` | Atlas compiler version that produced the manifest. |
| `projection` | Geographic projection. Version 1 supports only `equirectangular`. |
| `bounds` | West, south, east and north latitude/longitude limits. |
| `layers` | One or more tile-layer declarations. Layer IDs must be unique. |
| `provenance` | One or more source-data records. Source IDs must be unique. |

Unknown properties are rejected. This prevents misspelled or future fields from being silently
ignored by an older runtime.

## Layer fields

| Field | Meaning |
| --- | --- |
| `id` | Stable lowercase layer identifier. |
| `type` | `elevation` or `land_mask`. |
| `formatVersion` | OTAT tile format version. Version 1 requires `1`. |
| `encoding` | Binary payload encoding used by the layer. |
| `tileSize` | Tile width and height in samples, from 2 through 4096. |
| `zoom` | Non-negative detail level. Phase 1 currently uses level 0. |
| `gridWidthSamples` | Total number of samples from west to east. |
| `gridHeightSamples` | Total number of samples from north to south. |
| `noDataValue` | Missing-data marker when the layer type requires one. |
| `pathTemplate` | Relative OTAT tile path containing `{z}`, `{x}` and `{y}`. |

Version 1 fixes these type-specific combinations:

- Elevation uses `signed_int16_le` and requires `noDataValue: -32768`.
- Land mask uses `packed_bitset_lsb0` and must omit `noDataValue`.

Tile paths must use forward slashes, must remain relative to the atlas directory, must not contain
`..` traversal segments and must end in `.otat`.

## Provenance fields

Each provenance entry records the exact source identity, dataset version, licence, attribution,
absolute HTTP or HTTPS source URL, ISO-8601 retrieval date and an ordered list of processing steps.
A candidate dataset name alone is not sufficient provenance.

The included example is synthetic and does not approve any third-party dataset for redistribution.

## Reading and writing

`AtlasManifestJson.read` performs strict JSON decoding and domain validation. It rejects unknown
properties, null primitive fields, trailing JSON content, invalid identifiers, duplicate IDs,
incompatible type/encoding pairs, unsafe paths and malformed provenance metadata.

`AtlasManifestJson.write` emits deterministic, human-readable JSON with a final newline. The compiler
commands `validate-manifest` and `canonicalize-manifest` use this same codec so compiler output and
runtime input cannot develop separate interpretations.

## Compatibility policy

Version 1 manifests are immutable contracts. Adding a new required field, changing an enum value or
changing a field's meaning requires a new manifest schema version. Optional implementation features
must not silently reinterpret an existing version 1 file.
