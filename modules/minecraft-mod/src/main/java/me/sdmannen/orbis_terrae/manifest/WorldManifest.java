package me.sdmannen.orbis_terrae.manifest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.worldgen.spawn.SpawnConfiguration;

/** Version-locked settings that define one generated Orbis Terrae world. */
public record WorldManifest(
        int schemaVersion,
        String generatorVersion,
        String atlasVersion,
        String projection,
        ProfileSnapshot profile,
        SpawnSnapshot spawn,
        long worldSeed,
        String configurationHash,
        String createdAtUtc) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String EQUIRECTANGULAR_PROJECTION = "equirectangular";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public WorldManifest {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world manifest schema: " + schemaVersion);
        }
        generatorVersion = requireText(generatorVersion, "generatorVersion");
        atlasVersion = requireText(atlasVersion, "atlasVersion");
        projection = requireText(projection, "projection");
        if (!EQUIRECTANGULAR_PROJECTION.equals(projection)) {
            throw new IllegalArgumentException("Unsupported projection: " + projection);
        }
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(spawn, "spawn");
        configurationHash = requireText(configurationHash, "configurationHash");
        if (!SHA_256.matcher(configurationHash).matches()) {
            throw new IllegalArgumentException("Configuration hash must be lowercase SHA-256");
        }
        String expectedHash = computeConfigurationHash(
                generatorVersion,
                atlasVersion,
                projection,
                profile,
                spawn,
                worldSeed);
        if (!configurationHash.equals(expectedHash)) {
            throw new IllegalArgumentException("Configuration hash does not match world-defining settings");
        }
        createdAtUtc = requireText(createdAtUtc, "createdAtUtc");
        try {
            Instant.parse(createdAtUtc);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("createdAtUtc must be an ISO-8601 instant", exception);
        }
    }

    public static WorldManifest create(
            String generatorVersion,
            String atlasVersion,
            WorldProfile profile,
            long worldSeed,
            String createdAtUtc) {
        return create(
                generatorVersion,
                atlasVersion,
                profile,
                SpawnConfiguration.BUNDLED_BERGEN,
                worldSeed,
                createdAtUtc);
    }

    public static WorldManifest create(
            String generatorVersion,
            String atlasVersion,
            WorldProfile profile,
            SpawnConfiguration spawnConfiguration,
            long worldSeed,
            String createdAtUtc) {
        ProfileSnapshot profileSnapshot = ProfileSnapshot.from(profile);
        SpawnSnapshot spawnSnapshot = SpawnSnapshot.from(spawnConfiguration);
        String configurationHash = computeConfigurationHash(
                generatorVersion,
                atlasVersion,
                EQUIRECTANGULAR_PROJECTION,
                profileSnapshot,
                spawnSnapshot,
                worldSeed);
        return new WorldManifest(
                CURRENT_SCHEMA_VERSION,
                generatorVersion,
                atlasVersion,
                EQUIRECTANGULAR_PROJECTION,
                profileSnapshot,
                spawnSnapshot,
                worldSeed,
                configurationHash,
                createdAtUtc);
    }

    private static String computeConfigurationHash(
            String generatorVersion,
            String atlasVersion,
            String projection,
            ProfileSnapshot profile,
            SpawnSnapshot spawn,
            long worldSeed) {
        StringBuilder canonical = new StringBuilder()
                .append("schemaVersion=").append(CURRENT_SCHEMA_VERSION).append('\n')
                .append("generatorVersion=").append(requireText(generatorVersion, "generatorVersion")).append('\n')
                .append("atlasVersion=").append(requireText(atlasVersion, "atlasVersion")).append('\n')
                .append("projection=").append(requireText(projection, "projection")).append('\n')
                .append("worldSeed=").append(worldSeed).append('\n')
                .append("profile.id=").append(profile.id()).append('\n')
                .append("profile.horizontalScaleId=").append(profile.horizontalScaleId()).append('\n')
                .append("profile.horizontalMetresPerBlock=")
                .append(profile.horizontalMetresPerBlock()).append('\n')
                .append("profile.projectedWidthBlocks=").append(profile.projectedWidthBlocks()).append('\n')
                .append("profile.projectedHeightBlocks=").append(profile.projectedHeightBlocks()).append('\n')
                .append("profile.minimumY=").append(profile.minimumY()).append('\n')
                .append("profile.height=").append(profile.height()).append('\n')
                .append("profile.seaLevel=").append(profile.seaLevel()).append('\n')
                .append("spawn.mode=").append(spawn.mode()).append('\n')
                .append("spawn.latitude=").append(Double.toHexString(spawn.latitude())).append('\n')
                .append("spawn.longitude=").append(Double.toHexString(spawn.longitude())).append('\n')
                .append("spawn.searchRadiusBlocks=").append(spawn.searchRadiusBlocks()).append('\n');
        for (WorldProfile.ControlPoint point : profile.verticalControlPoints()) {
            canonical.append("profile.vertical=")
                    .append(Double.toHexString(point.realMetres()))
                    .append(':')
                    .append(Double.toHexString(point.blocks()))
                    .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** Exact profile values copied into the manifest so later preset edits cannot alter existing worlds. */
    public record ProfileSnapshot(
            String id,
            String horizontalScaleId,
            int horizontalMetresPerBlock,
            int projectedWidthBlocks,
            int projectedHeightBlocks,
            int minimumY,
            int height,
            int seaLevel,
            List<WorldProfile.ControlPoint> verticalControlPoints) {
        public ProfileSnapshot {
            id = requireText(id, "id");
            horizontalScaleId = requireText(horizontalScaleId, "horizontalScaleId");
            if (horizontalMetresPerBlock <= 0) {
                throw new IllegalArgumentException("Horizontal metres per block must be positive");
            }
            if (projectedWidthBlocks <= 0 || projectedWidthBlocks % 2 != 0) {
                throw new IllegalArgumentException("Projected width must be positive and even");
            }
            if (projectedHeightBlocks <= 0 || projectedHeightBlocks % 2 != 0) {
                throw new IllegalArgumentException("Projected height must be positive and even");
            }
            if (height <= 0 || height % 16 != 0) {
                throw new IllegalArgumentException("Height must be a positive multiple of 16");
            }
            long maximumExclusive = (long) minimumY + height;
            if (seaLevel < minimumY || seaLevel >= maximumExclusive) {
                throw new IllegalArgumentException("Sea level must be inside the configured dimension");
            }
            verticalControlPoints = List.copyOf(Objects.requireNonNull(
                    verticalControlPoints,
                    "verticalControlPoints"));
            validateControlPoints(verticalControlPoints);
        }

        public static ProfileSnapshot from(WorldProfile profile) {
            Objects.requireNonNull(profile, "profile");
            return new ProfileSnapshot(
                    profile.id(),
                    profile.horizontalScale().id(),
                    profile.horizontalScale().metresPerBlock(),
                    profile.horizontalScale().projectedWidthBlocks(),
                    profile.horizontalScale().projectedHeightBlocks(),
                    profile.minimumY(),
                    profile.height(),
                    profile.seaLevel(),
                    profile.verticalCurve().controlPoints());
        }

        private static void validateControlPoints(List<WorldProfile.ControlPoint> points) {
            if (points.size() < 2) {
                throw new IllegalArgumentException("At least two vertical control points are required");
            }
            for (int index = 0; index < points.size(); index++) {
                WorldProfile.ControlPoint point = Objects.requireNonNull(points.get(index), "controlPoint");
                if (index > 0 && points.get(index - 1).realMetres() >= point.realMetres()) {
                    throw new IllegalArgumentException("Vertical control points must be strictly ordered");
                }
            }
        }
    }

    /** Exact spawn settings copied into the manifest so later preset edits cannot alter existing worlds. */
    public record SpawnSnapshot(
            String mode,
            double latitude,
            double longitude,
            int searchRadiusBlocks) {
        public SpawnSnapshot {
            mode = requireText(mode, "mode");
            if (!SpawnConfiguration.COORDINATE_MODE.equals(mode)) {
                throw new IllegalArgumentException("Unsupported spawn mode: " + mode);
            }
            new SpawnConfiguration(latitude, longitude, searchRadiusBlocks);
        }

        public static SpawnSnapshot from(SpawnConfiguration configuration) {
            Objects.requireNonNull(configuration, "configuration");
            return new SpawnSnapshot(
                    SpawnConfiguration.COORDINATE_MODE,
                    configuration.latitude(),
                    configuration.longitude(),
                    configuration.searchRadiusBlocks());
        }

        public SpawnConfiguration toConfiguration() {
            return new SpawnConfiguration(latitude, longitude, searchRadiusBlocks);
        }
    }
}
