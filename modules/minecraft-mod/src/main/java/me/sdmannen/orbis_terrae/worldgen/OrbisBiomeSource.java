package me.sdmannen.orbis_terrae.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Serializable biome-source boundary for the first Orbis Terrae dimension. */
public final class OrbisBiomeSource extends BiomeSource {
    public static final MapCodec<OrbisBiomeSource> CODEC = Biome.CODEC
            .fieldOf("biome")
            .xmap(OrbisBiomeSource::new, OrbisBiomeSource::biome);

    private final Holder<Biome> biome;

    public OrbisBiomeSource(Holder<Biome> biome) {
        this.biome = Objects.requireNonNull(biome, "biome");
    }

    public Holder<Biome> biome() {
        return biome;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(biome);
    }

    @Override
    public Holder<Biome> getNoiseBiome(
            int quartX,
            int quartY,
            int quartZ,
            Climate.Sampler sampler) {
        return biome;
    }
}
