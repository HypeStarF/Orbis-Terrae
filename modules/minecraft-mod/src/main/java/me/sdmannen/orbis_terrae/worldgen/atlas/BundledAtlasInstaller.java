package me.sdmannen.orbis_terrae.worldgen.atlas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;

/** Installs the small reviewed Bergen atlas bundled with the mod into the game directory. */
public final class BundledAtlasInstaller {
    public static final String BUNDLED_ATLAS_ID = "bergen-real-v1";
    public static final String BUNDLED_RESOURCE =
            "/data/orbis_terrae/atlas/bergen-real-v1.zip";

    private static final Object INSTALL_LOCK = new Object();

    private BundledAtlasInstaller() {
    }

    /** Installs or reuses the bundled atlas below the supplied atlas root. */
    public static AtlasDirectory install(Path atlasRoot) throws IOException {
        Objects.requireNonNull(atlasRoot, "atlasRoot");
        synchronized (INSTALL_LOCK) {
            Path root = atlasRoot.toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(BUNDLED_ATLAS_ID).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Bundled atlas target escapes atlas root: " + target);
            }

            AtlasDirectory existing = openUsableAtlas(target);
            if (existing != null) {
                return existing;
            }

            deleteRecursively(target);
            Path temporary = Files.createTempDirectory(root, BUNDLED_ATLAS_ID + "-");
            try {
                extractBundledZip(temporary);
                AtlasDirectory extracted = requireExpectedAtlas(temporary);
                extracted.clearCache();
                moveInstalledAtlas(temporary, target);
                return requireExpectedAtlas(target);
            } finally {
                deleteRecursively(temporary);
            }
        }
    }

    private static AtlasDirectory openUsableAtlas(Path target) {
        if (!Files.isDirectory(target)) {
            return null;
        }
        try {
            return requireExpectedAtlas(target);
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static AtlasDirectory requireExpectedAtlas(Path directory) throws IOException {
        AtlasDirectory atlas = AtlasDirectory.open(directory);
        if (!BUNDLED_ATLAS_ID.equals(atlas.manifest().atlasId())) {
            throw new IOException(
                    "Bundled atlas id mismatch: " + atlas.manifest().atlasId());
        }
        boolean hasElevation = atlas.manifest().layers().stream()
                .anyMatch(layer -> layer.type() == AtlasManifest.LayerType.ELEVATION);
        boolean hasLandMask = atlas.manifest().layers().stream()
                .anyMatch(layer -> layer.type() == AtlasManifest.LayerType.LAND_MASK);
        if (!hasElevation || !hasLandMask) {
            throw new IOException("Bundled atlas must contain elevation and land-mask layers");
        }
        return atlas;
    }

    private static void extractBundledZip(Path destination) throws IOException {
        try (InputStream resource = BundledAtlasInstaller.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (resource == null) {
                throw new IOException("Bundled atlas resource is missing: " + BUNDLED_RESOURCE);
            }
            try (ZipInputStream archive = new ZipInputStream(resource)) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    extractEntry(destination, archive, entry);
                    archive.closeEntry();
                }
            }
        }
    }

    private static void extractEntry(
            Path destination,
            ZipInputStream archive,
            ZipEntry entry) throws IOException {
        String normalizedName = entry.getName().replace('\\', '/');
        if (normalizedName.isBlank() || normalizedName.startsWith("/")) {
            throw new IOException("Invalid bundled atlas entry: " + entry.getName());
        }
        Path output = destination.resolve(normalizedName).normalize();
        if (!output.startsWith(destination)) {
            throw new IOException("Bundled atlas entry escapes destination: " + entry.getName());
        }
        if (entry.isDirectory()) {
            Files.createDirectories(output);
            return;
        }
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(archive, output, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void moveInstalledAtlas(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walked = Files.walk(root)) {
            paths = walked.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
