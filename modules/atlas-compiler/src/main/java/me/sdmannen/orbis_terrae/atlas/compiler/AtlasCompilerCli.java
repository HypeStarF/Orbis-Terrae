package me.sdmannen.orbis_terrae.atlas.compiler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.sampling.LandMaskSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;

public final class AtlasCompilerCli {
    private AtlasCompilerCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usageAndExit();
        }
        switch (args[0]) {
            case "--version" -> printVersion(args);
            case "pack-elevation" -> packElevation(args);
            case "pack-land-mask" -> packLandMask(args);
            case "validate-manifest" -> validateManifest(args);
            case "canonicalize-manifest" -> canonicalizeManifest(args);
            case "sample-elevation" -> sampleElevation(args);
            case "sample-land" -> sampleLand(args);
            default -> usageAndExit();
        }
    }

    private static void printVersion(String[] args) {
        if (args.length != 1) {
            usageAndExit();
        }
        System.out.println("Orbis Terrae Atlas Compiler 0.1.0-SNAPSHOT");
    }

    private static void packElevation(String[] args) throws IOException {
        if (args.length != 4) {
            usageAndExit();
        }
        int tileSize = parseTileSize(args[1]);
        Path input = Path.of(args[2]);
        Path output = Path.of(args[3]);
        byte[] raw = Files.readAllBytes(input);
        int expected = Math.multiplyExact(
                Math.multiplyExact(tileSize, tileSize),
                Short.BYTES);
        if (raw.length != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " raw bytes, got " + raw.length);
        }
        short[] samples = new short[Math.multiplyExact(tileSize, tileSize)];
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort();
        }
        write(output, new AtlasTileWriter().encodeElevation(tileSize, samples));
    }

    private static void packLandMask(String[] args) throws IOException {
        if (args.length != 4) {
            usageAndExit();
        }
        int tileSize = parseTileSize(args[1]);
        Path input = Path.of(args[2]);
        Path output = Path.of(args[3]);
        byte[] raw = Files.readAllBytes(input);
        int expected = Math.multiplyExact(tileSize, tileSize);
        if (raw.length != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " mask bytes, got " + raw.length);
        }
        BitSet land = new BitSet(expected);
        for (int i = 0; i < raw.length; i++) {
            int value = Byte.toUnsignedInt(raw[i]);
            if (value == 1) {
                land.set(i);
            } else if (value != 0) {
                throw new IllegalArgumentException("Land-mask samples must be 0 or 1");
            }
        }
        write(output, new AtlasTileWriter().encodeLandMask(tileSize, land));
    }

    private static void validateManifest(String[] args) throws IOException {
        if (args.length != 2) {
            usageAndExit();
        }
        AtlasManifest manifest = AtlasManifestJson.read(Path.of(args[1]));
        System.out.println(
                "Valid atlas manifest " + manifest.atlasId() + " version " + manifest.atlasVersion());
    }

    private static void canonicalizeManifest(String[] args) throws IOException {
        if (args.length != 3) {
            usageAndExit();
        }
        AtlasManifest manifest = AtlasManifestJson.read(Path.of(args[1]));
        AtlasManifestJson.write(Path.of(args[2]), manifest);
    }

    private static void sampleElevation(String[] args) throws IOException {
        if (args.length != 6) {
            usageAndExit();
        }
        AtlasDirectory atlas = AtlasDirectory.open(Path.of(args[1]));
        ElevationSampler sampler = new ElevationSampler(atlas, args[2]);
        double latitude = parseCoordinate(args[3], "latitude");
        double longitude = parseCoordinate(args[4], "longitude");
        switch (args[5]) {
            case "nearest" -> printNearestElevation(
                    sampler.sampleNearestMetres(latitude, longitude));
            case "bilinear" -> printBilinearElevation(
                    sampler.sampleBilinearMetres(latitude, longitude));
            default -> throw new IllegalArgumentException(
                    "Elevation sampling mode must be nearest or bilinear");
        }
    }

    private static void sampleLand(String[] args) throws IOException {
        if (args.length != 5) {
            usageAndExit();
        }
        AtlasDirectory atlas = AtlasDirectory.open(Path.of(args[1]));
        LandMaskSampler sampler = new LandMaskSampler(atlas, args[2]);
        double latitude = parseCoordinate(args[3], "latitude");
        double longitude = parseCoordinate(args[4], "longitude");
        System.out.println("land=" + sampler.isLand(latitude, longitude));
    }

    private static void printNearestElevation(OptionalInt elevation) {
        if (elevation.isPresent()) {
            System.out.println("elevation_metres=" + elevation.getAsInt());
        } else {
            System.out.println("elevation_metres=no-data");
        }
    }

    private static void printBilinearElevation(OptionalDouble elevation) {
        if (elevation.isPresent()) {
            System.out.println("elevation_metres=" + elevation.getAsDouble());
        } else {
            System.out.println("elevation_metres=no-data");
        }
    }

    private static double parseCoordinate(String value, String name) {
        double coordinate = Double.parseDouble(value);
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return coordinate;
    }

    private static int parseTileSize(String value) {
        int tileSize = Integer.parseInt(value);
        if (tileSize < 2 || tileSize > 4096) {
            throw new IllegalArgumentException("Tile size must be between 2 and 4096");
        }
        return tileSize;
    }

    private static void write(Path output, byte[] bytes) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, bytes);
    }

    private static void usageAndExit() {
        System.err.println("Usage:");
        System.err.println("  --version");
        System.err.println("  pack-elevation <tileSize> <raw-int16le> <output.otat>");
        System.err.println("  pack-land-mask <tileSize> <raw-0-or-1-bytes> <output.otat>");
        System.err.println("  validate-manifest <atlas-manifest.json>");
        System.err.println("  canonicalize-manifest <input.json> <output.json>");
        System.err.println(
                "  sample-elevation <atlas-directory> <layer-id> <latitude> <longitude> <nearest|bilinear>");
        System.err.println(
                "  sample-land <atlas-directory> <layer-id> <latitude> <longitude>");
        System.exit(2);
    }
}
