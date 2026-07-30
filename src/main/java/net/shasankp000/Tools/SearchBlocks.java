package net.shasankp000.Tools;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Efficient block searching tool with incremental radius expansion.
 * Searches for blocks in expanding spherical shells to avoid lag.
 */
public class SearchBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger("search-blocks");

    // Cache of already searched positions to avoid re-scanning
    private static final Map<UUID, Set<BlockPos>> searchedPositions = new ConcurrentHashMap<>();

    // Thread pool for parallel searching
    private static final ExecutorService searchExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        r -> {
            Thread t = new Thread(r, "BlockSearch-Worker");
            t.setDaemon(true);
            return t;
        }
    );

    // Maximum blocks to check per iteration to prevent lag
    private static final int MAX_BLOCKS_PER_ITERATION = 5000;

    // Clean up old search caches periodically
    private static long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL = 300000; // 5 minutes

    /**
     * Search for a specific block type in an expanding radius.
     *
     * @param bot The bot player
     * @param blockType Block identifier (e.g., "minecraft:oak_log")
     * @param initialRadius Starting search radius
     * @param maxRadius Maximum search radius
     * @param radiusIncrement How much to expand radius each iteration
     * @return BlockPos of nearest matching block, or null if not found
     */
    public static BlockPos searchBlock(
            ServerPlayerEntity bot,
            String blockType,
            int initialRadius,
            int maxRadius,
            int radiusIncrement
    ) {
        if (bot == null || blockType == null || blockType.isEmpty()) {
            LOGGER.error("Invalid search parameters");
            return null;
        }

        // Periodic cleanup of old caches
        cleanupOldCaches();

        ServerWorld world = bot.getServerWorld();
        BlockPos botPos = bot.getBlockPos();
        UUID botId = bot.getUuid();

        // Initialize search cache for this bot if needed
        searchedPositions.putIfAbsent(botId, ConcurrentHashMap.newKeySet());
        Set<BlockPos> searched = searchedPositions.get(botId);

        // Normalize block type
        String normalizedBlockType = normalizeBlockType(blockType);
        Block targetBlock = getBlockFromIdentifier(normalizedBlockType);

        if (targetBlock == null) {
            LOGGER.error("Unknown block type: {}", normalizedBlockType);
            return null;
        }

        LOGGER.info("Searching for {} within radius {}-{} blocks from {}",
            normalizedBlockType, initialRadius, maxRadius, botPos);

        // Search in expanding shells
        int currentRadius = initialRadius;

        while (currentRadius <= maxRadius) {
            int finalRadius = currentRadius;
            int prevRadius = Math.max(0, currentRadius - radiusIncrement);

            LOGGER.debug("Searching shell: inner={}, outer={}", prevRadius, finalRadius);

            // Use parallel search for this shell
            BlockPos result = searchShell(
                world, botPos, targetBlock, prevRadius, finalRadius, searched
            );

            if (result != null) {
                LOGGER.info("✓ Found {} at {} (distance: {} blocks)",
                    normalizedBlockType, result, botPos.getManhattanDistance(result));
                return result;
            }

            currentRadius += radiusIncrement;
        }

        LOGGER.warn("No {} found within {} blocks", normalizedBlockType, maxRadius);
        return null;
    }

    /**
     * Search a spherical shell (between inner and outer radius).
     * Uses parallel processing and respects max blocks per iteration.
     */
    private static BlockPos searchShell(
            ServerWorld world,
            BlockPos center,
            Block targetBlock,
            int innerRadius,
            int outerRadius,
            Set<BlockPos> alreadySearched
    ) {
        // Generate candidate positions in this shell
        List<BlockPos> candidates = generateShellPositions(center, innerRadius, outerRadius);

        // Filter out already searched positions
        candidates.removeIf(alreadySearched::contains);

        // Limit to prevent lag
        if (candidates.size() > MAX_BLOCKS_PER_ITERATION) {
            LOGGER.debug("Limiting search from {} to {} blocks",
                candidates.size(), MAX_BLOCKS_PER_ITERATION);
            candidates = candidates.subList(0, MAX_BLOCKS_PER_ITERATION);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Split work into chunks for parallel processing
        int numThreads = Math.min(4, candidates.size() / 500 + 1);
        int chunkSize = candidates.size() / numThreads;

        List<CompletableFuture<BlockPos>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = (i == numThreads - 1) ? candidates.size() : (i + 1) * chunkSize;

            List<BlockPos> chunk = candidates.subList(start, end);

            CompletableFuture<BlockPos> future = CompletableFuture.supplyAsync(() -> {
                for (BlockPos pos : chunk) {
                    alreadySearched.add(pos); // Mark as searched

                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() == targetBlock) {
                        return pos; // Found it!
                    }
                }
                return null;
            }, searchExecutor);

            futures.add(future);
        }

        // Wait for first match or all to complete
        try {
            for (CompletableFuture<BlockPos> future : futures) {
                BlockPos result = future.get(5, TimeUnit.SECONDS);
                if (result != null) {
                    // Cancel remaining searches
                    futures.forEach(f -> f.cancel(true));
                    return result;
                }
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error("Search interrupted", e);
            futures.forEach(f -> f.cancel(true));
        }

        return null;
    }

    /**
     * Generate positions in a spherical shell (between inner and outer radius).
     * Uses efficient iteration to minimize overhead.
     */
    private static List<BlockPos> generateShellPositions(BlockPos center, int innerRadius, int outerRadius) {
        List<BlockPos> positions = new ArrayList<>();

        int innerRadiusSq = innerRadius * innerRadius;
        int outerRadiusSq = outerRadius * outerRadius;

        // Iterate cube, filter to shell
        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int y = -outerRadius; y <= outerRadius; y++) {
                for (int z = -outerRadius; z <= outerRadius; z++) {
                    int distSq = x*x + y*y + z*z;

                    if (distSq > innerRadiusSq && distSq <= outerRadiusSq) {
                        positions.add(center.add(x, y, z));
                    }
                }
            }
        }

        // Sort by distance for more efficient searching (closer first)
        positions.sort(Comparator.comparingInt(pos -> pos.getManhattanDistance(center)));

        return positions;
    }

    /**
     * Normalize block type string to proper identifier format.
     */
    private static String normalizeBlockType(String input) {
        input = input.toLowerCase().trim();

        if (!input.contains(":")) {
            input = "minecraft:" + input;
        }

        return input;
    }

    /**
     * Get Block instance from identifier string.
     */
    private static Block getBlockFromIdentifier(String blockId) {
        try {
            Identifier id = Identifier.of(blockId);
            return Registries.BLOCK.get(id);
        } catch (Exception e) {
            LOGGER.error("Failed to parse block identifier: {}", blockId, e);
            return null;
        }
    }

    /**
     * Clear search cache for a specific bot.
     */
    public static void clearCache(UUID botId) {
        searchedPositions.remove(botId);
        LOGGER.debug("Cleared search cache for bot: {}", botId);
    }

    /**
     * Clear all search caches.
     */
    public static void clearAllCaches() {
        searchedPositions.clear();
        LOGGER.info("Cleared all search caches");
    }

    /**
     * Periodic cleanup of old caches to prevent memory leaks.
     */
    private static void cleanupOldCaches() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL) {
            // In a real scenario, we'd track last access time per bot
            // For now, just clear if caches get too large
            if (searchedPositions.size() > 10) {
                LOGGER.info("Cleaning up old search caches (total: {})", searchedPositions.size());
                searchedPositions.entrySet().removeIf(entry -> {
                    // Remove if cache is very large (bot searched a lot)
                    return entry.getValue().size() > 50000;
                });
            }
            lastCleanup = now;
        }
    }

    /**
     * Shutdown the search executor (call on mod unload).
     */
    public static void shutdown() {
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            searchExecutor.shutdownNow();
        }
        LOGGER.info("Search executor shut down");
    }
}

