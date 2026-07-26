package me.sdmannen.orbis_terrae.atlas.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.zip.CRC32;

public final class AtlasTileReader {
    public static final int HEADER_SIZE = 28;
    private static final byte[] MAGIC = {'O', 'T', 'A', 'T'};

    public AtlasTile read(Path path) throws IOException {
        return decode(Files.readAllBytes(path));
    }

    public AtlasTile decode(byte[] bytes) throws AtlasFormatException {
        if (bytes.length < HEADER_SIZE) {
            throw new AtlasFormatException("Tile is shorter than the OTAT header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new AtlasFormatException("Invalid OTAT magic");
        }

        int version = Byte.toUnsignedInt(buffer.get());
        if (version != 1) {
            throw new AtlasFormatException("Unsupported OTAT version: " + version);
        }

        AtlasTileHeader.LayerType layerType =
                AtlasTileHeader.LayerType.fromId(Byte.toUnsignedInt(buffer.get()));
        AtlasTileHeader.Encoding encoding =
                AtlasTileHeader.Encoding.fromId(Byte.toUnsignedInt(buffer.get()));
        int flags = Byte.toUnsignedInt(buffer.get());
        int tileSize = Short.toUnsignedInt(buffer.getShort());
        int reserved = Short.toUnsignedInt(buffer.getShort());
        int minimum = buffer.getInt();
        int maximum = buffer.getInt();
        int payloadLength = buffer.getInt();
        long expectedCrc = Integer.toUnsignedLong(buffer.getInt());

        if (flags != 0) {
            throw new AtlasFormatException("Unsupported OTAT flags: " + flags);
        }
        if (reserved != 0) {
            throw new AtlasFormatException("Reserved header field must be zero");
        }
        if (tileSize < 2) {
            throw new AtlasFormatException("Invalid tile size: " + tileSize);
        }
        if (payloadLength < 0 || bytes.length != HEADER_SIZE + payloadLength) {
            throw new AtlasFormatException("Payload length does not match file length");
        }

        byte[] payload = Arrays.copyOfRange(bytes, HEADER_SIZE, bytes.length);
        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != expectedCrc) {
            throw new AtlasFormatException("Tile CRC32 mismatch");
        }

        AtlasTileHeader header = new AtlasTileHeader(
                version, layerType, encoding, flags, tileSize,
                minimum, maximum, payloadLength, expectedCrc);

        return switch (layerType) {
            case ELEVATION -> decodeElevation(header, payload);
            case LAND_MASK -> decodeLandMask(header, payload);
        };
    }

    private ElevationTile decodeElevation(AtlasTileHeader header, byte[] payload)
            throws AtlasFormatException {
        if (header.encoding() != AtlasTileHeader.Encoding.SIGNED_INT16_LE) {
            throw new AtlasFormatException("Elevation tile has invalid encoding");
        }
        int sampleCount;
        try {
            sampleCount = Math.multiplyExact(header.tileSize(), header.tileSize());
        } catch (ArithmeticException exception) {
            throw new AtlasFormatException("Elevation tile dimensions overflow");
        }
        if (payload.length != sampleCount * Short.BYTES) {
            throw new AtlasFormatException("Elevation payload has invalid length");
        }
        short[] samples = new short[sampleCount];
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int computedMin = Integer.MAX_VALUE;
        int computedMax = Integer.MIN_VALUE;
        for (int i = 0; i < samples.length; i++) {
            short value = buffer.getShort();
            samples[i] = value;
            if (value != ElevationTile.NO_DATA) {
                computedMin = Math.min(computedMin, value);
                computedMax = Math.max(computedMax, value);
            }
        }
        if (computedMin == Integer.MAX_VALUE) {
            computedMin = ElevationTile.NO_DATA;
            computedMax = ElevationTile.NO_DATA;
        }
        if (computedMin != header.minimumValue() || computedMax != header.maximumValue()) {
            throw new AtlasFormatException("Elevation min/max metadata mismatch");
        }
        return new ElevationTile(header, samples);
    }

    private LandMaskTile decodeLandMask(AtlasTileHeader header, byte[] payload)
            throws AtlasFormatException {
        if (header.encoding() != AtlasTileHeader.Encoding.PACKED_BITSET_LSB0) {
            throw new AtlasFormatException("Land-mask tile has invalid encoding");
        }
        int sampleCount;
        try {
            sampleCount = Math.multiplyExact(header.tileSize(), header.tileSize());
        } catch (ArithmeticException exception) {
            throw new AtlasFormatException("Land-mask tile dimensions overflow");
        }
        int expectedBytes = (sampleCount + 7) / 8;
        if (payload.length != expectedBytes) {
            throw new AtlasFormatException("Land-mask payload has invalid length");
        }
        return new LandMaskTile(header, BitSet.valueOf(payload));
    }
}
