package me.sdmannen.orbis_terrae.worldgen.atlas;

import java.io.IOException;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import net.neoforged.fml.loading.FMLPaths;

/** Lazily initializes the one common atlas runtime used by world generation. */
public final class OrbisAtlasRuntimeManager {
    private static volatile OrbisAtlasRuntime runtime;

    private OrbisAtlasRuntimeManager() {
    }

    public static OrbisAtlasRuntime initialize() {
        OrbisAtlasRuntime current = runtime;
        if (current != null) {
            return current;
        }
        synchronized (OrbisAtlasRuntimeManager.class) {
            current = runtime;
            if (current == null) {
                try {
                    current = OrbisAtlasRuntime.openBundled(FMLPaths.GAMEDIR.get());
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to initialize Orbis Terrae atlas runtime", exception);
                }
                runtime = current;
            }
            return current;
        }
    }

    public static EarthAtlasSampler sampler(WorldProfile profile) {
        return initialize().sampler(profile);
    }
}
