package me.sdmannen.orbis_terrae.atlas.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import org.junit.jupiter.api.Test;

final class AtlasManifestJsonTest {
    @Test
    void exampleManifestLoadsThroughStrictCodec() throws Exception {
        AtlasManifest manifest = AtlasManifestJson.decode(readResource("atlas-manifest.json"));

        assertEquals(1, manifest.schemaVersion());
        assertEquals("phase1-northern-europe-fixture", manifest.atlasId());
        assertEquals(AtlasManifest.Projection.EQUIRECTANGULAR, manifest.projection());
        assertEquals(2, manifest.layers().size());
        assertEquals(1, manifest.provenance().size());
        assertEquals(new GeoBounds(-25.0, 54.0, 45.0, 72.0), manifest.bounds());
    }

    @Test
    void canonicalEncodingIsDeterministicAndRoundTrips() throws Exception {
        AtlasManifest manifest = AtlasManifestJson.decode(readResource("atlas-manifest.json"));

        String first = AtlasManifestJson.encode(manifest);
        String second = AtlasManifestJson.encode(manifest);

        assertEquals(first, second);
        assertEquals(manifest, AtlasManifestJson.decode(first));
        assertTrue(first.endsWith("\n"));
        assertFalse(first.contains("\"noDataValue\" : null"));
    }

    @Test
    void unknownPropertiesAreRejected() throws Exception {
        String invalid = readResource("atlas-manifest.json").replace(
                "\"schemaVersion\": 1,",
                "\"schemaVersion\": 1,\n  \"unexpected\": true,");

        assertThrows(JsonProcessingException.class, () -> AtlasManifestJson.decode(invalid));
    }

    @Test
    void incompatibleLayerEncodingIsRejected() throws Exception {
        String invalid = readResource("atlas-manifest.json").replaceFirst(
                "signed_int16_le",
                "packed_bitset_lsb0");

        assertThrows(JsonProcessingException.class, () -> AtlasManifestJson.decode(invalid));
    }

    @Test
    void duplicateLayerIdsAreRejected() throws Exception {
        String invalid = readResource("atlas-manifest.json").replace(
                "\"id\": \"land_mask\"",
                "\"id\": \"elevation\"");

        assertThrows(JsonProcessingException.class, () -> AtlasManifestJson.decode(invalid));
    }

    @Test
    void unsafeTilePathsAreRejected() throws Exception {
        String invalid = readResource("atlas-manifest.json").replace(
                "layers/elevation/{z}/{x}/{y}.otat",
                "../elevation/{z}/{x}/{y}.otat");

        assertThrows(JsonProcessingException.class, () -> AtlasManifestJson.decode(invalid));
    }

    @Test
    void enumValuesAreCaseSensitive() throws Exception {
        String invalid = readResource("atlas-manifest.json").replace(
                "\"equirectangular\"",
                "\"EQUIRECTANGULAR\"");

        assertThrows(JsonProcessingException.class, () -> AtlasManifestJson.decode(invalid));
    }

    @Test
    void schemaPropertiesAndEnumsMatchJavaRecords() throws Exception {
        JsonNode schema = new ObjectMapper().readTree(readResource("atlas-manifest.schema.json"));

        assertEquals(recordFields(AtlasManifest.class), propertyNames(schema.path("properties")));
        assertEquals(recordFields(AtlasManifest.class), textValues(schema.path("required")));

        JsonNode bounds = schema.at("/$defs/bounds");
        assertEquals(recordFields(GeoBounds.class), propertyNames(bounds.path("properties")));
        assertEquals(recordFields(GeoBounds.class), textValues(bounds.path("required")));

        JsonNode layer = schema.at("/$defs/layer");
        assertEquals(recordFields(AtlasManifest.Layer.class), propertyNames(layer.path("properties")));
        Set<String> requiredLayerFields = new HashSet<>(recordFields(AtlasManifest.Layer.class));
        requiredLayerFields.remove("noDataValue");
        assertEquals(requiredLayerFields, textValues(layer.path("required")));

        JsonNode provenance = schema.at("/$defs/provenance");
        assertEquals(
                recordFields(AtlasManifest.Provenance.class),
                propertyNames(provenance.path("properties")));
        assertEquals(
                recordFields(AtlasManifest.Provenance.class),
                textValues(provenance.path("required")));

        assertEquals(
                Set.of("elevation", "land_mask"),
                textValues(layer.path("properties").path("type").path("enum")));
        assertEquals(
                Set.of("signed_int16_le", "packed_bitset_lsb0"),
                textValues(layer.path("properties").path("encoding").path("enum")));
        assertEquals(
                "equirectangular",
                schema.path("properties").path("projection").path("const").textValue());
    }

    private static Set<String> recordFields(Class<?> recordType) {
        RecordComponent[] components = Objects.requireNonNull(recordType.getRecordComponents());
        return Arrays.stream(components)
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> propertyNames(JsonNode properties) {
        Set<String> names = new HashSet<>();
        properties.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new HashSet<>();
        for (JsonNode element : array) {
            values.add(element.textValue());
        }
        return Set.copyOf(values);
    }

    private static String readResource(String name) throws IOException {
        ClassLoader loader = AtlasManifestJsonTest.class.getClassLoader();
        try (InputStream stream = Objects.requireNonNull(
                loader.getResourceAsStream(name),
                "Missing test resource: " + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
