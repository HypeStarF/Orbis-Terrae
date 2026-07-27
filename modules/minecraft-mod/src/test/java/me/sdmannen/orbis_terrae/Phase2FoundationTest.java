package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.manifest.WorldManifest;
import me.sdmannen.orbis_terrae.manifest.WorldManifestJson;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.spawn.SpawnConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Phase2FoundationTest {
    @Test
    void builtInScalesUseCenteredEvenEarthDimensions() {
        assertEquals(20_038, WorldProfile.HorizontalScale.GLOBAL_COMPACT.projectedWidthBlocks());
        assertEquals(10_002, WorldProfile.HorizontalScale.GLOBAL_COMPACT.projectedHeightBlocks());
        assertEquals(40_076, WorldProfile.HorizontalScale.GLOBAL_SURVIVAL.projectedWidthBlocks());
        assertEquals(20_004, WorldProfile.HorizontalScale.GLOBAL_SURVIVAL.projectedHeightBlocks());
        assertEquals(
                WorldProfiles.GLOBAL_SURVIVAL,
                WorldProfiles.require(WorldProfiles.GLOBAL_SURVIVAL.id()));
    }

    @Test
    void coordinateMappingRoundTripsAndWrapsLongitude() {
        EarthCoordinateMapper mapper = new EarthCoordinateMapper(WorldProfiles.GLOBAL_SURVIVAL);
        GeoCoordinate stockholm = new GeoCoordinate(59.3293, 18.0686);

        EarthCoordinateMapper.BlockCoordinate projected = mapper.toBlock(stockholm);
        GeoCoordinate sampled = mapper.toGeographic(
                Math.round(projected.x()),
                Math.round(projected.z()));

        assertEquals(stockholm.latitude(), sampled.latitude(), 0.01);
        assertEquals(stockholm.longitude(), sampled.longitude(), 0.01);
        assertEquals(mapper.minimumX(), mapper.wrapBlockX(mapper.maximumXExclusive()));
        assertEquals(-180.0, mapper.toGeographic(mapper.maximumXExclusive(), 0).longitude());
        assertTrue(mapper.containsProjectedLatitude(mapper.minimumZ()));
        assertEquals(90.0, mapper.toGeographic(0, mapper.minimumZ() - 1_000L).latitude());
    }

    @Test
    void verticalCurveInterpolatesAndClampsToDimension() {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        WorldProfile.PiecewiseLinearVerticalCurve simpleCurve =
                new WorldProfile.PiecewiseLinearVerticalCurve(List.of(
                        new WorldProfile.ControlPoint(0.0, 0.0),
                        new WorldProfile.ControlPoint(100.0, 50.0)));

        assertEquals(25.0, simpleCurve.transform(50.0));
        assertEquals(100.0, simpleCurve.transform(200.0));
        assertEquals(63, profile.terrainY(0.0));
        assertEquals(118, profile.terrainY(500.0));
        assertEquals(7, profile.terrainY(-11_000.0));
        assertEquals(profile.maximumY(), profile.terrainY(100_000.0));
    }

    @Test
    void manifestRoundTripIsStrictAndConfigurationHashIsDeterministic(
            @TempDir Path temporary) throws Exception {
        SpawnConfiguration bergen = SpawnConfiguration.BUNDLED_BERGEN;
        SpawnConfiguration stockholm = new SpawnConfiguration(59.3293, 18.0686, 96);
        WorldManifest first = WorldManifest.create(
                "0.2.0-SNAPSHOT",
                "global-coarse-v1",
                WorldProfiles.GLOBAL_SURVIVAL,
                bergen,
                42L,
                "2026-07-27T00:00:00Z");
        WorldManifest second = WorldManifest.create(
                "0.2.0-SNAPSHOT",
                "global-coarse-v1",
                WorldProfiles.GLOBAL_SURVIVAL,
                bergen,
                42L,
                "2026-07-27T00:00:00Z");
        WorldManifest compact = WorldManifest.create(
                "0.2.0-SNAPSHOT",
                "global-coarse-v1",
                WorldProfiles.GLOBAL_COMPACT,
                bergen,
                42L,
                "2026-07-27T00:00:00Z");
        WorldManifest differentSeed = WorldManifest.create(
                "0.2.0-SNAPSHOT",
                "global-coarse-v1",
                WorldProfiles.GLOBAL_SURVIVAL,
                bergen,
                43L,
                "2026-07-27T00:00:00Z");
        WorldManifest differentSpawn = WorldManifest.create(
                "0.2.0-SNAPSHOT",
                "global-coarse-v1",
                WorldProfiles.GLOBAL_SURVIVAL,
                stockholm,
                42L,
                "2026-07-27T00:00:00Z");

        Path manifestPath = temporary.resolve("world").resolve("orbis_terrae").resolve("manifest.json");
        WorldManifestJson.write(manifestPath, first);
        String encoded = WorldManifestJson.encode(first);
        int finalBrace = encoded.lastIndexOf('}');
        String unknownField = encoded.substring(0, finalBrace)
                + ",\n  \"unexpected\" : true\n}\n";
        String tamperedSeed = encoded.replace("\"worldSeed\" : 42", "\"worldSeed\" : 43");

        assertEquals(first, second);
        assertEquals(first, WorldManifestJson.decode(encoded));
        assertEquals(first, WorldManifestJson.read(manifestPath));
        assertEquals(bergen, first.spawn().toConfiguration());
        assertNotEquals(first.configurationHash(), compact.configurationHash());
        assertNotEquals(first.configurationHash(), differentSeed.configurationHash());
        assertNotEquals(first.configurationHash(), differentSpawn.configurationHash());
        assertThrows(JsonProcessingException.class, () -> WorldManifestJson.decode(unknownField));
        assertThrows(JsonProcessingException.class, () -> WorldManifestJson.decode(tamperedSeed));
    }

    @Test
    void invalidProfileAndManifestValuesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldProfile.PiecewiseLinearVerticalCurve(List.of(
                        new WorldProfile.ControlPoint(1.0, 0.0),
                        new WorldProfile.ControlPoint(1.0, 2.0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldManifest(
                        99,
                        "0.2.0-SNAPSHOT",
                        "atlas-v1",
                        WorldManifest.EQUIRECTANGULAR_PROJECTION,
                        WorldManifest.ProfileSnapshot.from(WorldProfiles.GLOBAL_SURVIVAL),
                        WorldManifest.SpawnSnapshot.from(SpawnConfiguration.BUNDLED_BERGEN),
                        0L,
                        "0".repeat(64),
                        "2026-07-27T00:00:00Z"));
    }
}
