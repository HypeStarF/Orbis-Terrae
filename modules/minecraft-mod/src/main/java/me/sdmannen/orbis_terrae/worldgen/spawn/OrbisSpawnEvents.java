package me.sdmannen.orbis_terrae.worldgen.spawn;

import me.sdmannen.orbis_terrae.OrbisTerrae;
import me.sdmannen.orbis_terrae.worldgen.OrbisChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Common-side event bridge that replaces vanilla spawn selection for Orbis Terrae worlds. */
public final class OrbisSpawnEvents {
    private OrbisSpawnEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(OrbisSpawnEvents::createSpawnPosition);
    }

    private static void createSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getChunkSource().getGenerator() instanceof OrbisChunkGenerator generator)) {
            return;
        }

        GeographicSpawnResolver.SpawnResolution resolution = generator.resolveSpawn();
        BlockPos spawnPosition = new BlockPos(
                resolution.blockX(),
                resolution.blockY(),
                resolution.blockZ());
        level.setDefaultSpawnPos(spawnPosition, 0.0F);
        event.setCanceled(true);

        boolean usedFallback = !resolution.configuration().equals(generator.spawnConfiguration());
        OrbisTerrae.LOGGER.info(
                "Selected Orbis Terrae spawn at {} from {} target after {} blocks of search",
                spawnPosition,
                usedFallback ? "bundled Bergen fallback" : "configured geographic",
                resolution.searchDistanceBlocks());
    }
}
