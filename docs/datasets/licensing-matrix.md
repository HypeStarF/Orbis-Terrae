# Dataset licensing matrix

No dataset is approved for bundling merely because it appears in this table. Phase 0 establishes the
review process; legal terms, attribution, redistribution rights, modification rights, and download
provenance must be verified against the exact product and release used.

| Layer | Candidate named in master plan | Exact product/version | Source URL | License/terms captured | Redistribution reviewed | Attribution drafted | Status | Reviewer notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Elevation | Copernicus DEM | TBD | TBD | No | No | No | UNREVIEWED | Surface-model artefact handling also required. |
| Land cover | ESA WorldCover | TBD | TBD | No | No | No | UNREVIEWED | Used as an input, not as final natural vegetation. |
| Hydrology | HydroSHEDS family | TBD | TBD | No | No | No | UNREVIEWED | Select exact product before download. |
| Climate | WorldClim | TBD | TBD | No | No | No | UNREVIEWED | Record resolution and release. |
| Soil | SoilGrids | TBD | TBD | No | No | No | UNREVIEWED | Record model version and depth layers. |
| Global bathymetry | GEBCO | TBD | TBD | No | No | No | UNREVIEWED | Record grid edition. |
| European bathymetry | EMODnet | TBD | TBD | No | No | No | UNREVIEWED | Candidate regional override. |
| European geology | EGDI | TBD | TBD | No | No | No | UNREVIEWED | Country-level rights may differ. |
| Global geology fallback | USGS world geology | TBD | TBD | No | No | No | UNREVIEWED | Identify exact publication. |
| Mineral occurrences | Geological surveys / USGS | TBD | TBD | No | No | No | UNREVIEWED | Review each survey separately. |

## Approval states

- `UNREVIEWED`: named as a candidate only; do not download into the distributable project.
- `RESEARCH_ONLY`: may be used locally for evaluation but not redistributed.
- `APPROVED_INPUT_ONLY`: may be processed, but raw files may not be redistributed.
- `APPROVED_BUNDLE`: derived atlas output may be bundled under documented conditions.
- `REJECTED`: terms or quality are incompatible with the project.

## Required evidence

For every approved dataset, store a provenance record in `atlas/provenance/` containing the exact
product name, release date, retrieval date, source, checksum, license/terms snapshot reference,
required attribution, permitted transformations, and redistribution decision.
