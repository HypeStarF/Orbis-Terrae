package me.sdmannen.orbis_terrae.atlas.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import me.sdmannen.orbis_terrae.atlas.cache.BoundedTileCache;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTile;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileHeader;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileReader;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;

final class AtlasTileStore {
    private final Path rootDirectory;
    private final AtlasTileReader reader;
    private final BoundedTileCache<TileKey, AtlasTile> cache;

    AtlasTileStore(Path rootDirectory, int maximumCachedTiles) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        this.reader = new AtlasTileReader();
        this.cache = new BoundedTileCache<>(maximumCachedTiles);
    }

    AtlasTile read(AtlasManifest.Layer layer, int tileX, int tileY) throws IOException {
        TileKey key = new TileKey(layer.id(), layer.zoom(), tileX, tileY);
        Optional<AtlasTile> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        Path declaredPath = tilePath(layer, tileX, tileY);
        Path readablePath = requireReadableTile(declaredPath, layer, tileX, tileY);
        AtlasTile tile = reader.read(readablePath);
        validateAgainstManifest(layer, tile, readablePath);
        cache.put(key, tile);
        return tile;
    }

    Path tilePath(AtlasManifest.Layer layer, int tileX, int tileY) throws AtlasAccessException {
        String rendered = layer.pathTemplate()
                .replace("{z}", Integer.toString(layer.zoom()))
                .replace("{x}", Integer.toString(tileX))
                .replace("{y}", Integer.toString(tileY));
        Path relative = Path.of(rendered);
        if (relative.isAbsolute()) {
            throw new AtlasAccessException(
                    "Layer " + layer.id() + " resolved an absolute tile path: " + rendered);
        }
        Path resolved = rootDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(rootDirectory)) {
            throw new AtlasAccessException(
                    "Layer " + layer.id() + " resolved outside the atlas directory: " + rendered);
        }
        return resolved;
    }

    BoundedTileCache.CacheStats cacheStats() {
        return cache.stats();
    }

    void clearCache() {
        cache.clear();
    }

    private Path requireReadableTile(
            Path declaredPath,
            AtlasManifest.Layer layer,
            int tileX,
            int tileY) throws IOException {
        if (!Files.exists(declaredPath)) {
            throw new NoSuchFileException(
                    declaredPath.toString(),
                    null,
                    "Missing tile for layer " + layer.id() + " at " + tileX + "," + tileY);
        }
        Path realPath = declaredPath.toRealPath();
        if (!realPath.startsWith(rootDirectory)) {
            throw new AtlasAccessException(
                    "Tile for layer " + layer.id() + " resolves outside the atlas directory: "
                            + declaredPath);
        }
        if (!Files.isRegularFile(realPath)) {
            throw new AtlasAccessException(
                    "Tile for layer " + layer.id() + " is not a regular file: " + realPath);
        }
        return realPath;
    }

    private static void validateAgainstManifest(
            AtlasManifest.Layer layer,
            AtlasTile tile,
            Path path) throws AtlasAccessException {
        AtlasTileHeader header = tile.header();
        if (header.formatVersion() != layer.formatVersion()) {
            throw mismatch(layer, path, "format version", layer.formatVersion(), header.formatVersion());
        }
        if (header.tileSize() != layer.tileSize()) {
            throw mismatch(layer, path, "tile size", layer.tileSize(), header.tileSize());
        }

        AtlasTileHeader.LayerType expectedType = expectedLayerType(layer.type());
        if (header.layerType() != expectedType) {
            throw mismatch(layer, path, "layer type", expectedType, header.layerType());
        }

        AtlasTileHeader.Encoding expectedEncoding = expectedEncoding(layer.encoding());
        if (header.encoding() != expectedEncoding) {
            throw mismatch(layer, path, "encoding", expectedEncoding, header.encoding());
        }

        boolean expectedClass = switch (layer.type()) {
            case ELEVATION -> tile instanceof ElevationTile;
            case LAND_MASK -> tile instanceof LandMaskTile;
        };
        if (!expectedClass) {
            throw new AtlasAccessException(
                    "Tile " + path + " does not decode to the declared layer type " + layer.type());
        }
    }

    private static AtlasTileHeader.LayerType expectedLayerType(AtlasManifest.LayerType type) {
        return switch (type) {
            case ELEVATION -> AtlasTileHeader.LayerType.ELEVATION;
            case LAND_MASK -> AtlasTileHeader.LayerType.LAND_MASK;
        };
    }

    private static AtlasTileHeader.Encoding expectedEncoding(AtlasManifest.Encoding encoding) {
        return switch (encoding) {
            case SIGNED_INT16_LE -> AtlasTileHeader.Encoding.SIGNED_INT16_LE;
            case PACKED_BITSET_LSB0 -> AtlasTileHeader.Encoding.PACKED_BITSET_LSB0;
        };
    }

    private static AtlasAccessException mismatch(
            AtlasManifest.Layer layer,
            Path path,
            String field,
            Object expected,
            Object actual) {
        return new AtlasAccessException(
                "Tile " + path + " for layer " + layer.id() + " has " + field + " " + actual
                        + ", expected " + expected);
    }

    private record TileKey(String layerId, int zoom, int tileX, int tileY) {
    }
}
