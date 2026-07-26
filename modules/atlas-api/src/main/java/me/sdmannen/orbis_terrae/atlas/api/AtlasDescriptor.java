package me.sdmannen.orbis_terrae.atlas.api;

import java.util.Objects;

/** Minimal immutable atlas identity used before the Phase 1 binary format is finalized. */
public record AtlasDescriptor(String atlasId, String atlasVersion, int schemaVersion) {
    public AtlasDescriptor {
        Objects.requireNonNull(atlasId, "atlasId");
        Objects.requireNonNull(atlasVersion, "atlasVersion");
        if (atlasId.isBlank()) {
            throw new IllegalArgumentException("atlasId must not be blank");
        }
        if (atlasVersion.isBlank()) {
            throw new IllegalArgumentException("atlasVersion must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }
}
