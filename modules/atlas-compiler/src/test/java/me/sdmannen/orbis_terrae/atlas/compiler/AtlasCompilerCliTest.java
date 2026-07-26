package me.sdmannen.orbis_terrae.atlas.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileReader;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtlasCompilerCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void packsElevationDeterministically() throws Exception {
        Path input = temporaryDirectory.resolve("elevation.raw");
        Path first = temporaryDirectory.resolve("first.otat");
        Path second = temporaryDirectory.resolve("second.otat");
        ByteBuffer raw = ByteBuffer.allocate(Short.BYTES * 4).order(ByteOrder.LITTLE_ENDIAN);
        raw.putShort((short) 10).putShort((short) 20).putShort((short) 30).putShort((short) 40);
        Files.write(input, raw.array());

        AtlasCompilerCli.main(
                new String[] {"pack-elevation", "2", input.toString(), first.toString()});
        AtlasCompilerCli.main(
                new String[] {"pack-elevation", "2", input.toString(), second.toString()});

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        ElevationTile tile = (ElevationTile) new AtlasTileReader().read(first);
        assertEquals(40, tile.elevationMetres(1, 1));
    }

    @Test
    void packsLandMask() throws Exception {
        Path input = temporaryDirectory.resolve("land-mask.raw");
        Path output = temporaryDirectory.resolve("land-mask.otat");
        Files.write(input, new byte[] {1, 0, 0, 1});

        AtlasCompilerCli.main(
                new String[] {"pack-land-mask", "2", input.toString(), output.toString()});

        LandMaskTile tile = (LandMaskTile) new AtlasTileReader().read(output);
        assertTrue(tile.isLand(0, 0));
        assertFalse(tile.isLand(1, 0));
        assertTrue(tile.isLand(1, 1));
    }
}
