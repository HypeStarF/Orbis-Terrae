package me.sdmannen.orbis_terrae.atlas.compiler;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;

/** Compiles normalized row-major rasters into a complete deterministic OTAT atlas directory. */
public final class RasterAtlasCompiler {
    private static final int ELEVATION_BYTES_PER_SAMPLE = Short.BYTES;
    private static final int LAND_MASK_BYTES_PER_SAMPLE = 1;

    private RasterAtlasCompiler() {
    }

    /**
     * Compiles one elevation layer and one land-mask layer declared by the supplied manifest.
     *
     * <p>Elevation input is signed int16 little-endian. Land-mask input contains one byte per sample,
     * where zero is water and one is land. Both rasters are row-major with north at the first row.
     */
    public static CompilationResult compile(
            Path manifestTemplate,
            Path elevationRaster,
            Path landMaskRaster,
            Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(manifestTemplate, "manifestTemplate");
        AtlasManifest manifest = AtlasManifestJson.read(manifestTemplate);
        return compile(manifest, elevationRaster, landMaskRaster, outputDirectory);
    }

    /** Compiles normalized raster files using an already validated manifest model. */
    public static CompilationResult compile(
            AtlasManifest manifest,
            Path elevationRaster,
            Path landMaskRaster,
            Path outputDirectory)
            throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Path elevationInput = requireRegularFile(elevationRaster, "elevationRaster");
        Path landMaskInput = requireRegularFile(landMaskRaster, "landMaskRaster");
        Path outputRoot = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();

        AtlasManifest.Layer elevationLayer = requireSingleLayer(
                manifest.layers(), AtlasManifest.LayerType.ELEVATION);
        AtlasManifest.Layer landMaskLayer = requireSingleLayer(
                manifest.layers(), AtlasManifest.LayerType.LAND_MASK);
        requireRasterSize(elevationInput, elevationLayer, ELEVATION_BYTES_PER_SAMPLE);
        requireRasterSize(landMaskInput, landMaskLayer, LAND_MASK_BYTES_PER_SAMPLE);
        if (Files.exists(outputRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(outputRoot.toString());
        }

        AtlasTileWriter tileWriter = new AtlasTileWriter();
        int tileCount = 0;
        long sourceSamples = 0;
        boolean outputCreated = false;
        try {
            Files.createDirectories(outputRoot);
            outputCreated = true;
            AtlasManifestJson.write(
                    outputRoot.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest);
            for (AtlasManifest.Layer layer : manifest.layers()) {
                switch (layer.type()) {
                    case ELEVATION -> tileCount += compileElevationLayer(
                            outputRoot, layer, elevationInput, tileWriter);
                    case LAND_MASK -> tileCount += compileLandMaskLayer(
                            outputRoot, layer, landMaskInput, tileWriter);
                    default -> throw new IllegalStateException(
                            "Unsupported layer type: " + layer.type());
                }
                sourceSamples = Math.addExact(sourceSamples, sampleCount(layer));
            }
            return new CompilationResult(
                    manifest.atlasId(),
                    manifest.layers().size(),
                    tileCount,
                    sourceSamples,
                    outputRoot);
        } catch (IOException | RuntimeException exception) {
            if (outputCreated) {
                try {
                    deleteRecursively(outputRoot);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    private static int compileElevationLayer(
            Path outputRoot,
            AtlasManifest.Layer layer,
            Path input,
            AtlasTileWriter writer)
            throws IOException {
        int tileCount = 0;
        try (FileChannel channel = FileChannel.open(input, StandardOpenOption.READ)) {
            for (int tileY = 0; tileY < tileRows(layer); tileY++) {
                for (int tileX = 0; tileX < tileColumns(layer); tileX++) {
                    short[] samples = new short[Math.multiplyExact(layer.tileSize(), layer.tileSize())];
                    Arrays.fill(samples, ElevationTile.NO_DATA);
                    readElevationTile(channel, layer, tileX, tileY, samples);
                    writeTile(
                            outputRoot,
                            layer,
                            tileX,
                            tileY,
                            writer.encodeElevation(layer.tileSize(), samples));
                    tileCount++;
                }
            }
        }
        return tileCount;
    }

    private static int compileLandMaskLayer(
            Path outputRoot,
            AtlasManifest.Layer layer,
            Path input,
            AtlasTileWriter writer)
            throws IOException {
        int tileCount = 0;
        try (FileChannel channel = FileChannel.open(input, StandardOpenOption.READ)) {
            for (int tileY = 0; tileY < tileRows(layer); tileY++) {
                for (int tileX = 0; tileX < tileColumns(layer); tileX++) {
                    BitSet land = new BitSet(Math.multiplyExact(layer.tileSize(), layer.tileSize()));
                    readLandMaskTile(channel, layer, tileX, tileY, land);
                    writeTile(
                            outputRoot,
                            layer,
                            tileX,
                            tileY,
                            writer.encodeLandMask(layer.tileSize(), land));
                    tileCount++;
                }
            }
        }
        return tileCount;
    }

    private static void readElevationTile(
            FileChannel channel,
            AtlasManifest.Layer layer,
            int tileX,
            int tileY,
            short[] output)
            throws IOException {
        int startX = Math.multiplyExact(tileX, layer.tileSize());
        int startY = Math.multiplyExact(tileY, layer.tileSize());
        int validWidth = Math.min(layer.tileSize(), layer.gridWidthSamples() - startX);
        int validHeight = Math.min(layer.tileSize(), layer.gridHeightSamples() - startY);
        ByteBuffer row = ByteBuffer.allocate(Math.multiplyExact(validWidth, Short.BYTES))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int localY = 0; localY < validHeight; localY++) {
            row.clear();
            long sourceOffset = elevationOffset(layer, startX, startY + localY);
            readFully(channel, row, sourceOffset);
            row.flip();
            int outputOffset = Math.multiplyExact(localY, layer.tileSize());
            for (int localX = 0; localX < validWidth; localX++) {
                output[outputOffset + localX] = row.getShort();
            }
        }
    }

    private static void readLandMaskTile(
            FileChannel channel,
            AtlasManifest.Layer layer,
            int tileX,
            int tileY,
            BitSet output)
            throws IOException {
        int startX = Math.multiplyExact(tileX, layer.tileSize());
        int startY = Math.multiplyExact(tileY, layer.tileSize());
        int validWidth = Math.min(layer.tileSize(), layer.gridWidthSamples() - startX);
        int validHeight = Math.min(layer.tileSize(), layer.gridHeightSamples() - startY);
        ByteBuffer row = ByteBuffer.allocate(validWidth);
        for (int localY = 0; localY < validHeight; localY++) {
            row.clear();
            long sourceOffset = landMaskOffset(layer, startX, startY + localY);
            readFully(channel, row, sourceOffset);
            row.flip();
            int outputOffset = Math.multiplyExact(localY, layer.tileSize());
            for (int localX = 0; localX < validWidth; localX++) {
                int value = Byte.toUnsignedInt(row.get());
                if (value == 1) {
                    output.set(outputOffset + localX);
                } else if (value != 0) {
                    throw new IOException(
                            "Land-mask sample at " + (startX + localX) + "," + (startY + localY)
                                    + " must be 0 or 1, got " + value);
                }
            }
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer output, long sourceOffset)
            throws IOException {
        long position = sourceOffset;
        while (output.hasRemaining()) {
            int read = channel.read(output, position);
            if (read < 0) {
                throw new EOFException("Unexpected end of normalized raster at byte " + position);
            }
            if (read == 0) {
                throw new IOException("Could not make progress reading normalized raster");
            }
            position += read;
        }
    }

    private static void writeTile(
            Path outputRoot,
            AtlasManifest.Layer layer,
            int tileX,
            int tileY,
            byte[] bytes)
            throws IOException {
        String relative = layer.pathTemplate()
                .replace("{z}", Integer.toString(layer.zoom()))
                .replace("{x}", Integer.toString(tileX))
                .replace("{y}", Integer.toString(tileY));
        Path output = outputRoot.resolve(relative).normalize();
        if (!output.startsWith(outputRoot)) {
            throw new IOException("Rendered tile path escapes atlas directory: " + relative);
        }
        Files.createDirectories(Objects.requireNonNull(output.getParent()));
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW);
    }

    private static AtlasManifest.Layer requireSingleLayer(
            List<AtlasManifest.Layer> layers,
            AtlasManifest.LayerType type) {
        AtlasManifest.Layer match = null;
        for (AtlasManifest.Layer layer : layers) {
            if (layer.type() == type) {
                if (match != null) {
                    throw new IllegalArgumentException(
                            "Full-raster compilation requires exactly one " + type.jsonValue()
                                    + " layer");
                }
                match = layer;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException(
                    "Full-raster compilation requires exactly one " + type.jsonValue() + " layer");
        }
        return match;
    }

    private static Path requireRegularFile(Path input, String name) throws IOException {
        Path normalized = Objects.requireNonNull(input, name).toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException(name + " is not a regular file: " + normalized);
        }
        return normalized.toRealPath();
    }

    private static void requireRasterSize(
            Path input,
            AtlasManifest.Layer layer,
            int bytesPerSample)
            throws IOException {
        long expected = Math.multiplyExact(sampleCount(layer), bytesPerSample);
        long actual = Files.size(input);
        if (actual != expected) {
            throw new IOException(
                    "Normalized raster for layer " + layer.id() + " must contain " + expected
                            + " bytes, got " + actual);
        }
    }

    private static long sampleCount(AtlasManifest.Layer layer) {
        return Math.multiplyExact(
                (long) layer.gridWidthSamples(), (long) layer.gridHeightSamples());
    }

    private static int tileColumns(AtlasManifest.Layer layer) {
        return Math.floorDiv(layer.gridWidthSamples() - 1, layer.tileSize()) + 1;
    }

    private static int tileRows(AtlasManifest.Layer layer) {
        return Math.floorDiv(layer.gridHeightSamples() - 1, layer.tileSize()) + 1;
    }

    private static long elevationOffset(AtlasManifest.Layer layer, int sampleX, int sampleY) {
        long sampleIndex = Math.addExact(
                Math.multiplyExact((long) sampleY, layer.gridWidthSamples()), sampleX);
        return Math.multiplyExact(sampleIndex, Short.BYTES);
    }

    private static long landMaskOffset(AtlasManifest.Layer layer, int sampleX, int sampleY) {
        return Math.addExact(
                Math.multiplyExact((long) sampleY, layer.gridWidthSamples()), sampleX);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Summary of one successful full-raster compilation. */
    public record CompilationResult(
            String atlasId,
            int layerCount,
            int tileCount,
            long sourceSampleCount,
            Path outputDirectory) {
        public CompilationResult {
            atlasId = Objects.requireNonNull(atlasId, "atlasId");
            outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            if (layerCount < 1 || tileCount < 1 || sourceSampleCount < 1) {
                throw new IllegalArgumentException("Compilation result counts must be positive");
            }
        }
    }
}
