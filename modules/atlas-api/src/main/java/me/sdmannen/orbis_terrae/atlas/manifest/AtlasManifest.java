package me.sdmannen.orbis_terrae.atlas.manifest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;

@JsonPropertyOrder({
        "schemaVersion",
        "atlasId",
        "atlasVersion",
        "compilerVersion",
        "projection",
        "bounds",
        "layers",
        "provenance"
})
public record AtlasManifest(
        int schemaVersion,
        String atlasId,
        String atlasVersion,
        String compilerVersion,
        Projection projection,
        GeoBounds bounds,
        List<Layer> layers,
        List<Provenance> provenance) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public AtlasManifest {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported manifest schema: " + schemaVersion);
        }
        atlasId = requireIdentifier(atlasId, "atlasId");
        atlasVersion = requireNonBlank(atlasVersion, "atlasVersion");
        compilerVersion = requireNonBlank(compilerVersion, "compilerVersion");
        projection = Objects.requireNonNull(projection, "projection");
        bounds = Objects.requireNonNull(bounds, "bounds");
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("Manifest requires at least one layer");
        }
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("Manifest requires at least one provenance entry");
        }
        requireUniqueLayerIds(layers);
        requireUniqueSourceIds(provenance);
    }

    private static void requireUniqueLayerIds(List<Layer> layers) {
        Set<String> ids = new HashSet<>();
        for (Layer layer : layers) {
            Objects.requireNonNull(layer, "layers contains null");
            if (!ids.add(layer.id())) {
                throw new IllegalArgumentException("Duplicate layer id: " + layer.id());
            }
        }
    }

    private static void requireUniqueSourceIds(List<Provenance> provenance) {
        Set<String> ids = new HashSet<>();
        for (Provenance source : provenance) {
            Objects.requireNonNull(source, "provenance contains null");
            if (!ids.add(source.sourceId())) {
                throw new IllegalArgumentException(
                        "Duplicate provenance source id: " + source.sourceId());
            }
        }
    }

    private static String requireIdentifier(String value, String name) {
        value = requireNonBlank(value, name);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    name + " must match " + IDENTIFIER.pattern() + ": " + value);
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public enum Projection {
        EQUIRECTANGULAR("equirectangular");

        private final String jsonValue;

        Projection(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @JsonValue
        public String jsonValue() {
            return jsonValue;
        }

        @JsonCreator
        public static Projection fromJson(String value) {
            return parseEnumValue(values(), value, Projection::jsonValue, "projection");
        }
    }

    @JsonPropertyOrder({
            "id",
            "type",
            "formatVersion",
            "encoding",
            "tileSize",
            "zoom",
            "gridWidthSamples",
            "gridHeightSamples",
            "noDataValue",
            "pathTemplate"
    })
    public record Layer(
            String id,
            LayerType type,
            int formatVersion,
            Encoding encoding,
            int tileSize,
            int zoom,
            int gridWidthSamples,
            int gridHeightSamples,
            Integer noDataValue,
            String pathTemplate) {
        public Layer {
            id = requireIdentifier(id, "layer.id");
            type = Objects.requireNonNull(type, "layer.type");
            if (formatVersion != 1) {
                throw new IllegalArgumentException(
                        "Unsupported tile format version: " + formatVersion);
            }
            encoding = Objects.requireNonNull(encoding, "layer.encoding");
            if (tileSize < 2 || tileSize > 4096) {
                throw new IllegalArgumentException("layer.tileSize must be between 2 and 4096");
            }
            if (zoom < 0) {
                throw new IllegalArgumentException("layer.zoom must not be negative");
            }
            if (gridWidthSamples < 2 || gridHeightSamples < 2) {
                throw new IllegalArgumentException("Layer grid must contain at least 2x2 samples");
            }
            pathTemplate = requirePathTemplate(pathTemplate);
            validateLayerEncoding(type, encoding, noDataValue);
        }

        private static String requirePathTemplate(String value) {
            value = requireNonBlank(value, "layer.pathTemplate");
            if (value.startsWith("/") || value.contains("\\")) {
                throw new IllegalArgumentException(
                        "layer.pathTemplate must be a forward-slash relative path");
            }
            for (String segment : value.split("/")) {
                if (segment.equals("..")) {
                    throw new IllegalArgumentException(
                            "layer.pathTemplate must not contain parent traversal");
                }
            }
            if (!value.contains("{z}") || !value.contains("{x}") || !value.contains("{y}")) {
                throw new IllegalArgumentException(
                        "layer.pathTemplate must contain {z}, {x}, and {y}");
            }
            if (!value.endsWith(".otat")) {
                throw new IllegalArgumentException("layer.pathTemplate must end with .otat");
            }
            return value;
        }

        private static void validateLayerEncoding(
                LayerType type,
                Encoding encoding,
                Integer noDataValue) {
            switch (type) {
                case ELEVATION -> {
                    if (encoding != Encoding.SIGNED_INT16_LE) {
                        throw new IllegalArgumentException(
                                "Elevation layers require signed_int16_le encoding");
                    }
                    if (!Objects.equals(noDataValue, (int) Short.MIN_VALUE)) {
                        throw new IllegalArgumentException(
                                "Elevation layers require noDataValue " + Short.MIN_VALUE);
                    }
                }
                case LAND_MASK -> {
                    if (encoding != Encoding.PACKED_BITSET_LSB0) {
                        throw new IllegalArgumentException(
                                "Land-mask layers require packed_bitset_lsb0 encoding");
                    }
                    if (noDataValue != null) {
                        throw new IllegalArgumentException(
                                "Land-mask layers must not define noDataValue");
                    }
                }
                default -> throw new IllegalStateException("Unsupported layer type: " + type);
            }
        }
    }

    public enum LayerType {
        ELEVATION("elevation"),
        LAND_MASK("land_mask");

        private final String jsonValue;

        LayerType(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @JsonValue
        public String jsonValue() {
            return jsonValue;
        }

        @JsonCreator
        public static LayerType fromJson(String value) {
            return parseEnumValue(values(), value, LayerType::jsonValue, "layer type");
        }
    }

    public enum Encoding {
        SIGNED_INT16_LE("signed_int16_le"),
        PACKED_BITSET_LSB0("packed_bitset_lsb0");

        private final String jsonValue;

        Encoding(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @JsonValue
        public String jsonValue() {
            return jsonValue;
        }

        @JsonCreator
        public static Encoding fromJson(String value) {
            return parseEnumValue(values(), value, Encoding::jsonValue, "encoding");
        }
    }

    @JsonPropertyOrder({
            "sourceId",
            "title",
            "datasetVersion",
            "licence",
            "attribution",
            "sourceUrl",
            "retrievedDate",
            "processing"
    })
    public record Provenance(
            String sourceId,
            String title,
            String datasetVersion,
            String licence,
            String attribution,
            String sourceUrl,
            String retrievedDate,
            List<String> processing) {
        public Provenance {
            sourceId = requireIdentifier(sourceId, "provenance.sourceId");
            title = requireNonBlank(title, "provenance.title");
            datasetVersion = requireNonBlank(datasetVersion, "provenance.datasetVersion");
            licence = requireNonBlank(licence, "provenance.licence");
            attribution = requireNonBlank(attribution, "provenance.attribution");
            sourceUrl = requireAbsoluteHttpUrl(sourceUrl);
            retrievedDate = requireIsoDate(retrievedDate);
            processing = List.copyOf(Objects.requireNonNull(processing, "provenance.processing"));
            if (processing.isEmpty()) {
                throw new IllegalArgumentException(
                        "provenance.processing requires at least one operation");
            }
            for (String operation : processing) {
                requireNonBlank(operation, "provenance.processing entry");
            }
        }

        private static String requireAbsoluteHttpUrl(String value) {
            value = requireNonBlank(value, "provenance.sourceUrl");
            URI uri;
            try {
                uri = URI.create(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid provenance.sourceUrl: " + value,
                        exception);
            }
            String scheme = uri.getScheme();
            if (scheme == null
                    || uri.getHost() == null
                    || !(scheme.equals("http") || scheme.equals("https"))) {
                throw new IllegalArgumentException(
                        "provenance.sourceUrl must be an absolute HTTP(S) URL: " + value);
            }
            return value;
        }

        private static String requireIsoDate(String value) {
            value = requireNonBlank(value, "provenance.retrievedDate");
            try {
                LocalDate.parse(value);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "provenance.retrievedDate must use ISO-8601 YYYY-MM-DD: " + value,
                        exception);
            }
            return value;
        }
    }

    private static <E> E parseEnumValue(
            E[] values,
            String value,
            java.util.function.Function<E, String> jsonValue,
            String label) {
        Objects.requireNonNull(value, label);
        for (E candidate : values) {
            if (jsonValue.apply(candidate).equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported " + label + ": " + value);
    }
}
