package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class Phase2WorldPresetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void earthDimensionTypeMatchesBuiltInWorldProfiles() throws IOException {
        JsonNode dimensionType = readResource(
                "data", "orbis_terrae", "dimension_type", "earth.json");

        assertEquals(-64, dimensionType.path("min_y").intValue());
        assertEquals(384, dimensionType.path("height").intValue());
        assertEquals(384, dimensionType.path("logical_height").intValue());
        assertEquals("minecraft:overworld", dimensionType.path("effects").textValue());
        assertEquals("#minecraft:infiniburn_overworld", dimensionType.path("infiniburn").textValue());
        assertTrue(dimensionType.path("has_skylight").booleanValue());
        assertTrue(dimensionType.path("natural").booleanValue());
        assertFalse(dimensionType.path("has_ceiling").booleanValue());
        assertFalse(dimensionType.path("ultrawarm").booleanValue());
    }

    @Test
    void earthPresetInstallsOrbisTerraeAsTheOverworldStem() throws IOException {
        JsonNode preset = readResource(
                "data", "orbis_terrae", "worldgen", "world_preset", "earth.json");
        JsonNode dimensions = preset.path("dimensions");

        assertEquals(
                Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                fieldNames(dimensions));

        JsonNode overworld = dimensions.path("minecraft:overworld");
        JsonNode generator = overworld.path("generator");
        JsonNode biomeSource = generator.path("biome_source");
        JsonNode spawn = generator.path("spawn");

        assertEquals("orbis_terrae:earth", overworld.path("type").textValue());
        assertEquals("orbis_terrae:earth", generator.path("type").textValue());
        assertEquals("global-survival", generator.path("profile").textValue());
        assertEquals("orbis_terrae:earth", biomeSource.path("type").textValue());
        assertEquals("minecraft:plains", biomeSource.path("biome").textValue());
        assertEquals("coordinates", spawn.path("mode").textValue());
        assertEquals(60.3913, spawn.path("latitude").doubleValue());
        assertEquals(5.3221, spawn.path("longitude").doubleValue());
        assertEquals(64, spawn.path("search_radius_blocks").intValue());

        assertVanillaNoiseStem(
                dimensions.path("minecraft:the_nether"),
                "minecraft:the_nether",
                "minecraft:nether");
        assertVanillaNoiseStem(
                dimensions.path("minecraft:the_end"),
                "minecraft:the_end",
                "minecraft:end");
    }

    @Test
    void earthPresetIsExposedAndLocalized() throws IOException {
        JsonNode normalPresets = readResource(
                "data", "minecraft", "tags", "worldgen", "world_preset", "normal.json");
        JsonNode language = readResource(
                "assets", "orbis_terrae", "lang", "en_us.json");

        assertFalse(normalPresets.path("replace").booleanValue());
        assertEquals(1, normalPresets.path("values").size());
        assertEquals("orbis_terrae:earth", normalPresets.path("values").get(0).textValue());
        assertEquals("Orbis Terrae", language.path("generator.orbis_terrae.earth").textValue());
    }

    private static void assertVanillaNoiseStem(
            JsonNode stem,
            String dimensionType,
            String settings) {
        assertEquals(dimensionType, stem.path("type").textValue());
        assertEquals("minecraft:noise", stem.path("generator").path("type").textValue());
        assertEquals(settings, stem.path("generator").path("settings").textValue());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static JsonNode readResource(String first, String... more) throws IOException {
        Path path = RESOURCES.resolve(Path.of(first, more));
        return JSON.readTree(Files.readString(path));
    }
}
