package me.sdmannen.orbis_terrae.worldgen.validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;

/** Produces a stable content fingerprint for the deterministic terrain plan of one chunk. */
public final class TerrainChunkFingerprint {
    private static final int CHUNK_SIZE = 16;

    private TerrainChunkFingerprint() {
    }

    public static String compute(
            int chunkX,
            int chunkZ,
            ColumnPlanner planner) {
        Objects.requireNonNull(planner, "planner");
        MessageDigest digest = sha256();
        updateInt(digest, chunkX);
        updateInt(digest, chunkZ);

        int minimumBlockX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int minimumBlockZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int blockX = minimumBlockX + localX;
                int blockZ = minimumBlockZ + localZ;
                TerrainColumnPlan plan = Objects.requireNonNull(
                        planner.plan(blockX, blockZ),
                        "terrainColumnPlan");

                updateInt(digest, blockX);
                updateInt(digest, blockZ);
                digest.update((byte) (plan.land() ? 1 : 0));
                updateInt(digest, plan.solidTopY());
                updateInt(digest, plan.seaLevel());
                updateInt(digest, plan.minimumY());
                updateInt(digest, plan.maximumY());
                digest.update((byte) plan.dataAvailability().ordinal());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value)
                .array());
    }

    @FunctionalInterface
    public interface ColumnPlanner {
        TerrainColumnPlan plan(int blockX, int blockZ);
    }
}
