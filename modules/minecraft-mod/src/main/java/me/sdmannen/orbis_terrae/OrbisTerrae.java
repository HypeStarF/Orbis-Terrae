package me.sdmannen.orbis_terrae;

import com.mojang.logging.LogUtils;
import me.sdmannen.orbis_terrae.worldgen.OrbisWorldgenRegistries;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntime;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntimeManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Common Orbis Terrae mod entry point. This class must remain dedicated-server safe. */
@Mod(OrbisTerrae.MOD_ID)
public final class OrbisTerrae {
    public static final String MOD_ID = "orbis_terrae";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OrbisTerrae(IEventBus modEventBus, ModContainer modContainer) {
        OrbisWorldgenRegistries.register(modEventBus);
        OrbisAtlasRuntime atlasRuntime = OrbisAtlasRuntimeManager.initialize();
        LOGGER.info("Orbis Terrae common entry point loaded with atlases {}", atlasRuntime.atlasIds());
    }
}
