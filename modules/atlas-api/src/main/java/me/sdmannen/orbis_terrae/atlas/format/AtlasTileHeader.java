package me.sdmannen.orbis_terrae.atlas.format;

public record AtlasTileHeader(
        int formatVersion,
        LayerType layerType,
        Encoding encoding,
        int flags,
        int tileSize,
        int minimumValue,
        int maximumValue,
        int payloadLength,
        long crc32) {

    public enum LayerType {
        ELEVATION(1), LAND_MASK(2);

        private final int id;

        LayerType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static LayerType fromId(int id) throws AtlasFormatException {
            return switch (id) {
                case 1 -> ELEVATION;
                case 2 -> LAND_MASK;
                default -> throw new AtlasFormatException("Unsupported layer type: " + id);
            };
        }
    }

    public enum Encoding {
        SIGNED_INT16_LE(1), PACKED_BITSET_LSB0(2);

        private final int id;

        Encoding(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Encoding fromId(int id) throws AtlasFormatException {
            return switch (id) {
                case 1 -> SIGNED_INT16_LE;
                case 2 -> PACKED_BITSET_LSB0;
                default -> throw new AtlasFormatException("Unsupported encoding: " + id);
            };
        }
    }
}
