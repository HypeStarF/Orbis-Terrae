package me.sdmannen.orbis_terrae.atlas.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.sdmannen.orbis_terrae.atlas.cache.BoundedTileCache;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;

/** An opened compiled atlas directory backed by a strict manifest and cached tile store. */
public final class AtlasDirectory {
    public static final String MANIFEST_FILE_NAME = "atlas-manifest.json";
    public static final int DEFAULT_MAXIMUM_CACHED_TILES = 64;

    private final Path rootDirectory;
    private final AtlasManifest manifest;
    private final Map<String, AtlasLayer> layersById;
    private final List<AtlasLayer> layers;
    private final AtlasTileStore tileStore;

    private AtlasDirectory(
            Path rootDirectory,
            AtlasManifest manifest,
            Map<String, AtlasLayer> layersById,
            AtlasTileStore tileStore) {
        this.rootDirectory = rootDirectory;
        this.manifest = manifest;
        this.layersById = Collections.unmodifiableMap(new LinkedHashMap<>(layersById));
        this.layers = List.copyOf(layersById.values());
        this.tileStore = tileStore;
    }

    public static AtlasDirectory open(Path rootDirectory) throws IOException {
        return open(rootDirectory, DEFAULT_MAXIMUM_CACHED_TILES);
    }

    public static AtlasDirectory open(Path rootDirectory, int maximumCachedTiles) throws IOException {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        if (maximumCachedTiles < 1) {
            throw new IllegalArgumentException("maximumCachedTiles must be positive");
        }

        Path requestedRoot = rootDirectory.toAbsolutePath().normalize();
        if (!Files.exists(requestedRoot)) {
            throw new NoSuchFileException(requestedRoot.toString());
        }
        if (!Files.isDirectory(requestedRoot)) {
            throw new NotDirectoryException(requestedRoot.toString());
        }

        Path realRoot = requestedRoot.toRealPath();
        Path manifestPath = requireManifestFile(realRoot);
        AtlasManifest manifest = AtlasManifestJson.read(manifestPath);
        AtlasTileStore tileStore = new AtlasTileStore(realRoot, maximumCachedTiles);
        Map<String, AtlasLayer> layersById = new LinkedHashMap<>();
        for (AtlasManifest.Layer definition : manifest.layers()) {
            layersById.put(definition.id(), new AtlasLayer(definition, tileStore));
        }
        return new AtlasDirectory(realRoot, manifest, layersById, tileStore);
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public AtlasManifest manifest() {
        return manifest;
    }

    public List<AtlasLayer> layers() {
        return layers;
    }

    public Optional<AtlasLayer> findLayer(String id) {
        return Optional.ofNullable(layersById.get(Objects.requireNonNull(id, "id")));
    }

    public AtlasLayer requireLayer(String id) {
        return findLayer(id).orElseThrow(() -> new IllegalArgumentException(
                "Atlas " + manifest.atlasId() + " does not declare layer " + id
                        + "; available layers: " + layersById.keySet()));
    }

    public AtlasLayer requireElevationLayer(String id) {
        return requireLayerType(id, AtlasManifest.LayerType.ELEVATION);
    }

    public AtlasLayer requireLandMaskLayer(String id) {
        return requireLayerType(id, AtlasManifest.LayerType.LAND_MASK);
    }

    public BoundedTileCache.CacheStats cacheStats() {
        return tileStore.cacheStats();
    }

    public void clearCache() {
        tileStore.clearCache();
    }

    private AtlasLayer requireLayerType(String id, AtlasManifest.LayerType type) {
        AtlasLayer layer = requireLayer(id);
        if (layer.type() != type) {
            throw new IllegalArgumentException(
                    "Layer " + id + " has type " + layer.type() + ", not " + type);
        }
        return layer;
    }

    private static Path requireManifestFile(Path realRoot) throws IOException {
        Path declaredPath = realRoot.resolve(MANIFEST_FILE_NAME);
        if (!Files.exists(declaredPath)) {
            throw new NoSuchFileException(
                    declaredPath.toString(), null, "Atlas manifest is missing");
        }
        Path realPath = declaredPath.toRealPath();
        if (!realPath.startsWith(realRoot)) {
            throw new AtlasAccessException(
                    "Atlas manifest resolves outside the atlas directory: " + declaredPath);
        }
        if (!Files.isRegularFile(realPath)) {
            throw new AtlasAccessException(
                    "Atlas manifest is not a regular file: " + realPath);
        }
        return realPath;
    }
}
