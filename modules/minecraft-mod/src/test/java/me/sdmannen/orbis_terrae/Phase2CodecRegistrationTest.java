package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.stream.Collectors;
import me.sdmannen.orbis_terrae.worldgen.OrbisBiomeSource;
import me.sdmannen.orbis_terrae.worldgen.OrbisChunkGenerator;
import me.sdmannen.orbis_terrae.worldgen.OrbisWorldgenRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class Phase2CodecRegistrationTest {
    @Test
    void registryIdsAreStableAndNamespaced() {
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(OrbisTerrae.MOD_ID, "earth");

        assertEquals(expected, OrbisWorldgenRegistries.EARTH_BIOME_SOURCE_ID);
        assertEquals(expected, OrbisWorldgenRegistries.EARTH_CHUNK_GENERATOR_ID);
        assertEquals(expected, OrbisWorldgenRegistries.EARTH_BIOME_SOURCE.getId());
        assertEquals(expected, OrbisWorldgenRegistries.EARTH_CHUNK_GENERATOR.getId());
    }

    @Test
    void codecFieldsRemainExplicitAndVersionable() {
        assertEquals(Set.of("biome"), codecKeys(OrbisBiomeSource.CODEC));
        assertEquals(
                Set.of("biome_source", "profile"),
                codecKeys(OrbisChunkGenerator.CODEC));
    }

    private static Set<String> codecKeys(MapCodec<?> codec) {
        return codec.keys(JsonOps.INSTANCE)
                .map(JsonElement::getAsString)
                .collect(Collectors.toUnmodifiableSet());
    }
}
