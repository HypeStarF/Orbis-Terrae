package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Phase2CodecRegistrationTest {
    private static final Path WORLDGEN_ROOT = Path.of(
            "src", "main", "java", "me", "sdmannen", "orbis_terrae", "worldgen");

    @Test
    void registryIdsAreStableAndNamespaced() throws IOException {
        String source = read("OrbisWorldgenRegistries.java");

        assertTrue(source.contains("ResourceLocation.fromNamespaceAndPath(OrbisTerrae.MOD_ID, \"earth\")"));
        assertTrue(source.contains("BuiltInRegistries.BIOME_SOURCE"));
        assertTrue(source.contains("BuiltInRegistries.CHUNK_GENERATOR"));
        assertTrue(source.contains("EARTH_BIOME_SOURCE_ID.getPath()"));
        assertTrue(source.contains("EARTH_CHUNK_GENERATOR_ID.getPath()"));
    }

    @Test
    void codecFieldsRemainExplicitAndVersionable() throws IOException {
        String biomeSource = read("OrbisBiomeSource.java");
        String chunkGenerator = read("OrbisChunkGenerator.java");

        assertTrue(biomeSource.contains("fieldOf(\"biome\")"));
        assertTrue(chunkGenerator.contains("fieldOf(\"biome_source\")"));
        assertTrue(chunkGenerator.contains("fieldOf(\"profile\")"));
        assertTrue(chunkGenerator.contains("codec().optionalFieldOf(\"spawn\""));
        assertTrue(chunkGenerator.contains("WorldProfiles.require(profileId)"));
    }

    private static String read(String filename) throws IOException {
        return Files.readString(WORLDGEN_ROOT.resolve(filename));
    }
}
