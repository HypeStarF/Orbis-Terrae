package me.sdmannen.orbis_terrae.worldgen.atlas;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.sdmannen.orbis_terrae.atlas.selection.AtlasStack;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.profile.WorldProfile;

/** Open atlas directories and their deterministic per-layer selection stack. */
public final class OrbisAtlasRuntime {
    public static final String RUNTIME_DIRECTORY_NAME = "orbis_terrae/atlases";

    private final List<AtlasDirectory> atlases;
    private final AtlasStack atlasStack;

    private OrbisAtlasRuntime(List<AtlasDirectory> atlases) {
        this.atlases = List.copyOf(atlases);
        this.atlasStack = new AtlasStack(this.atlases);
    }

    /** Opens an explicit ordered list of installed atlas directories. */
    public static OrbisAtlasRuntime open(
            List<Path> atlasDirectories,
            int maximumCachedTilesPerAtlas) throws IOException {
        Objects.requireNonNull(atlasDirectories, "atlasDirectories");
        if (atlasDirectories.isEmpty()) {
            throw new IllegalArgumentException("At least one atlas directory is required");
        }
        if (maximumCachedTilesPerAtlas < 1) {
            throw new IllegalArgumentException("maximumCachedTilesPerAtlas must be positive");
        }

        List<AtlasDirectory> opened = new ArrayList<>(atlasDirectories.size());
        for (Path directory : atlasDirectories) {
            opened.add(AtlasDirectory.open(
                    Objects.requireNonNull(directory, "atlasDirectories contains null"),
                    maximumCachedTilesPerAtlas));
        }
        return new OrbisAtlasRuntime(opened);
    }

    /** Installs and opens the reviewed Bergen atlas bundled with the mod. */
    public static OrbisAtlasRuntime openBundled(Path gameDirectory) throws IOException {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Path atlasRoot = gameDirectory.toAbsolutePath()
                .normalize()
                .resolve(RUNTIME_DIRECTORY_NAME)
                .normalize();
        AtlasDirectory bundled = BundledAtlasInstaller.install(atlasRoot);
        return new OrbisAtlasRuntime(List.of(bundled));
    }

    public List<String> atlasIds() {
        return atlasStack.atlasIds();
    }

    public List<Path> atlasDirectories() {
        return atlases.stream().map(AtlasDirectory::rootDirectory).toList();
    }

    public EarthAtlasSampler sampler(WorldProfile profile) {
        return new EarthAtlasSampler(profile, atlasStack);
    }
}
