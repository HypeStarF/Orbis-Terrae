package me.sdmannen.orbis_terrae.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import me.sdmannen.orbis_terrae.atlas.cache.BoundedTileCache;
import me.sdmannen.orbis_terrae.atlas.format.AtlasFormatException;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileReader;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.geo.EquirectangularGrid;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.geo.GridCoordinate;
import me.sdmannen.orbis_terrae.atlas.geo.TileAddress;
import org.junit.jupiter.api.Test;

final class Phase1SelfTest {
    private final AtlasTileWriter writer = new AtlasTileWriter();
    private final AtlasTileReader reader = new AtlasTileReader();

    @Test
    void elevationRoundTripIsDeterministic() throws Exception {
        short[] samples = {100, 200, ElevationTile.NO_DATA, 400};

        byte[] first = writer.encodeElevation(2, samples);
        byte[] second = writer.encodeElevation(2, samples);
        ElevationTile tile = (ElevationTile) reader.decode(first);

        assertArrayEquals(first, second);
        assertArrayEquals(samples, tile.copySamples());
        assertEquals(100, tile.header().minimumValue());
        assertEquals(400, tile.header().maximumValue());
        assertFalse(tile.hasData(0, 1));
    }

    @Test
    void landMaskRoundTripPreservesSamples() throws Exception {
        BitSet land = new BitSet(4);
        land.set(0);
        land.set(3);

        LandMaskTile tile = (LandMaskTile) reader.decode(writer.encodeLandMask(2, land));

        assertTrue(tile.isLand(0, 0));
        assertFalse(tile.isLand(1, 0));
        assertFalse(tile.isLand(0, 1));
        assertTrue(tile.isLand(1, 1));
    }

    @Test
    void corruptPayloadIsRejected() {
        byte[] corrupt = writer.encodeElevation(2, new short[] {100, 200, 300, 400});
        corrupt[corrupt.length - 1] ^= 1;

        AtlasFormatException exception = assertThrows(
                AtlasFormatException.class,
                () -> reader.decode(corrupt));

        assertTrue(exception.getMessage().contains("CRC32"));
    }

    @Test
    void coordinateConversionAndTileAddressingAreStable() {
        EquirectangularGrid grid = new EquirectangularGrid(
                new GeoBounds(0.0, 0.0, 4.0, 2.0), 4, 2);

        GridCoordinate coordinate = grid.toGrid(2.0, 4.0);
        TileAddress address = TileAddress.fromSample(3, 1, 2);

        assertEquals(3.0, coordinate.sampleX());
        assertEquals(0.0, coordinate.sampleY());
        assertEquals(1, address.tileX());
        assertEquals(1, address.localX());
        assertEquals(2.0, grid.toGeographic(3.0, 0.0).latitude());
        assertEquals(4.0, grid.toGeographic(3.0, 0.0).longitude());
    }

    @Test
    void cacheUsesLeastRecentlyUsedEviction() {
        BoundedTileCache<String, String> cache = new BoundedTileCache<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.put("c", "C");

        assertTrue(cache.get("b").isEmpty());
        assertEquals(1, cache.stats().evictions());
    }
}
