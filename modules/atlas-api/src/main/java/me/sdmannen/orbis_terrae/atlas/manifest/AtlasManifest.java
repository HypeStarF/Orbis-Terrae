package me.sdmannen.orbis_terrae.atlas.manifest;

import java.util.List;
import java.util.Objects;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;

public record AtlasManifest(
        int schemaVersion,
        String atlasId,
        String atlasVersion,
        String compilerVersion,
        String projection,
        GeoBounds bounds,
        List<Layer> layers,
        List<Provenance> provenance) {

    public AtlasManifest {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported manifest schema: " + schemaVersion);
        }
        atlasId = requireNonBlank(atlasId, "atlasId");
        atlasVersion = requireNonBlank(atlasVersion, "atlasVersion");
        compilerVersion = requireNonBlank(compilerVersion, "compilerVersion");
        projection = requireNonBlank(projection, "projection");
        bounds = Objects.requireNonNull(bounds, "bounds");
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
        if (layers.isEmpty() || provenance.isEmpty()) {
            throw new IllegalArgumentException("Manifest requires layers and provenance");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public record Layer(
            String id,
            LayerType type,
            int formatVersion,
            int tileSize,
            int zoom,
            int gridWidthSamples,
            int gridHeightSamples,
            Integer noDataValue,
            String pathTemplate,
            String sha256) {
    }

    public enum LayerType {
        ELEVATION,
        LAND_MASK
    }

    public record Provenance(
            String sourceId,
            String title,
            String datasetVersion,
            String licence,
            String attribution,
            String sourceUrl,
            String retrievedDate,
            List<String> processing) {
    }
}
