package me.sdmannen.orbis_terrae.client;

import me.sdmannen.orbis_terrae.OrbisTerrae;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client-only initialization isolated by NeoForge's physical-side mod loading. */
@Mod(value = OrbisTerrae.MOD_ID, dist = Dist.CLIENT)
public final class OrbisTerraeClient {
    public OrbisTerraeClient(IEventBus modEventBus) {
        OrbisTerrae.LOGGER.info("Orbis Terrae client setup complete");
    }
}
