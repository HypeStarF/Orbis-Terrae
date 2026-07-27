package me.sdmannen.orbis_terrae.worldgen.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;

/** Immutable exact-coordinate spawn settings serialized with the Earth chunk generator. */
public record SpawnConfiguration(double latitude, double longitude, int searchRadiusBlocks) {
    public static final String COORDINATE_MODE = "coordinates";
    public static final int DEFAULT_SEARCH_RADIUS_BLOCKS = 64;
    public static final int MAX_SEARCH_RADIUS_BLOCKS = 256;
    public static final SpawnConfiguration BUNDLED_BERGEN = new SpawnConfiguration(
            60.3913,
            5.3221,
            DEFAULT_SEARCH_RADIUS_BLOCKS);

    public static final Codec<SpawnConfiguration> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("mode", COORDINATE_MODE)
                            .forGetter(configuration -> COORDINATE_MODE),
                    Codec.DOUBLE.fieldOf("latitude")
                            .forGetter(SpawnConfiguration::latitude),
                    Codec.DOUBLE.fieldOf("longitude")
                            .forGetter(SpawnConfiguration::longitude),
                    Codec.intRange(0, MAX_SEARCH_RADIUS_BLOCKS)
                            .optionalFieldOf("search_radius_blocks", DEFAULT_SEARCH_RADIUS_BLOCKS)
                            .forGetter(SpawnConfiguration::searchRadiusBlocks))
                    .apply(instance, SpawnConfiguration::decode));

    public SpawnConfiguration {
        new GeoCoordinate(latitude, longitude);
        if (searchRadiusBlocks < 0 || searchRadiusBlocks > MAX_SEARCH_RADIUS_BLOCKS) {
            throw new IllegalArgumentException(
                    "Spawn search radius must be between 0 and " + MAX_SEARCH_RADIUS_BLOCKS);
        }
    }

    public GeoCoordinate coordinate() {
        return new GeoCoordinate(latitude, longitude);
    }

    private static SpawnConfiguration decode(
            String mode,
            double latitude,
            double longitude,
            int searchRadiusBlocks) {
        if (!COORDINATE_MODE.equals(mode)) {
            throw new IllegalArgumentException("Unsupported spawn mode: " + mode);
        }
        return new SpawnConfiguration(latitude, longitude, searchRadiusBlocks);
    }
}
