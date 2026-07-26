package me.sdmannen.orbis_terrae.atlas.format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Objects;
import java.util.zip.CRC32;

public final class AtlasTileWriter {
    private static final byte[] MAGIC = {'O', 'T', 'A', 'T'};

    public byte[] encodeElevation(int tileSize, short[] samples) {
        Objects.requireNonNull(samples, "samples");
        int expected = Math.multiplyExact(tileSize, tileSize);
        if (samples.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " elevation samples");
        }

        ByteBuffer payload = ByteBuffer.allocate(Math.multiplyExact(samples.length, Short.BYTES))
                .order(ByteOrder.LITTLE_ENDIAN);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (short sample : samples) {
            payload.putShort(sample);
            if (sample != ElevationTile.NO_DATA) {
                min = Math.min(min, sample);
                max = Math.max(max, sample);
            }
        }
        if (min == Integer.MAX_VALUE) {
            min = ElevationTile.NO_DATA;
            max = ElevationTile.NO_DATA;
        }
        return encodeHeaderAndPayload(
                AtlasTileHeader.LayerType.ELEVATION,
                AtlasTileHeader.Encoding.SIGNED_INT16_LE,
                tileSize,
                min,
                max,
                payload.array());
    }

    public byte[] encodeLandMask(int tileSize, BitSet landSamples) {
        Objects.requireNonNull(landSamples, "landSamples");
        int sampleCount = Math.multiplyExact(tileSize, tileSize);
        int payloadLength = Math.floorDiv(Math.addExact(sampleCount, 7), 8);
        byte[] payload = new byte[payloadLength];
        byte[] usedBits = landSamples.get(0, sampleCount).toByteArray();
        System.arraycopy(usedBits, 0, payload, 0, Math.min(usedBits.length, payload.length));
        return encodeHeaderAndPayload(
                AtlasTileHeader.LayerType.LAND_MASK,
                AtlasTileHeader.Encoding.PACKED_BITSET_LSB0,
                tileSize,
                0,
                1,
                payload);
    }

    private byte[] encodeHeaderAndPayload(
            AtlasTileHeader.LayerType type,
            AtlasTileHeader.Encoding encoding,
            int tileSize,
            int min,
            int max,
            byte[] payload) {
        if (tileSize < 2 || tileSize > 65535) {
            throw new IllegalArgumentException("Tile size outside unsigned-short range");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);

        ByteBuffer output = ByteBuffer.allocate(AtlasTileReader.HEADER_SIZE + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        output.put(MAGIC);
        output.put((byte) 1);
        output.put((byte) type.id());
        output.put((byte) encoding.id());
        output.put((byte) 0);
        output.putShort((short) tileSize);
        output.putShort((short) 0);
        output.putInt(min);
        output.putInt(max);
        output.putInt(payload.length);
        output.putInt((int) crc.getValue());
        output.put(payload);
        return output.array();
    }
}
