package me.sdmannen.orbis_terrae.worldgen;

import com.mojang.serialization.MapCodec;
import me.sdmannen.orbis_terrae.OrbisTerrae;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Owns the vanilla world-generation codec registrations used by Orbis Terrae. */
public final class OrbisWorldgenRegistries {
    public static final ResourceLocation EARTH_BIOME_SOURCE_ID =
            ResourceLocation.fromNamespaceAndPath(OrbisTerrae.MOD_ID, "earth");
    public static final ResourceLocation EARTH_CHUNK_GENERATOR_ID =
            ResourceLocation.fromNamespaceAndPath(OrbisTerrae.MOD_ID, "earth");

    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCE_CODECS =
            DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, OrbisTerrae.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATOR_CODECS =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, OrbisTerrae.MOD_ID);

    public static final DeferredHolder<
            MapCodec<? extends BiomeSource>,
            MapCodec<OrbisBiomeSource>> EARTH_BIOME_SOURCE = BIOME_SOURCE_CODECS.register(
                    EARTH_BIOME_SOURCE_ID.getPath(),
                    () -> OrbisBiomeSource.CODEC);

    public static final DeferredHolder<
            MapCodec<? extends ChunkGenerator>,
            MapCodec<OrbisChunkGenerator>> EARTH_CHUNK_GENERATOR = CHUNK_GENERATOR_CODECS.register(
                    EARTH_CHUNK_GENERATOR_ID.getPath(),
                    () -> OrbisChunkGenerator.CODEC);

    private OrbisWorldgenRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCE_CODECS.register(modEventBus);
        CHUNK_GENERATOR_CODECS.register(modEventBus);
    }
}
