package net.shasankp000.PlayerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

public class BlockDistanceLimitedSearch {

    private final ServerPlayer player;
    private final int maxDepth;       // Vertical search range (both up and down)
    private final int searchRadius;   // Horizontal search radius (x and z)
    private final List<String> reachableBlockNames = new ArrayList<>();

    /**
     * @param player       The player whose surroundings will be scanned.
     * @param maxDepth     The maximum vertical offset (both up and down) to search.
     * @param searchRadius The horizontal radius (x and z) to search.
     */
    public BlockDistanceLimitedSearch(ServerPlayer player, int maxDepth, int searchRadius) {
        this.player = player;
        this.maxDepth = maxDepth;
        this.searchRadius = searchRadius;
    }

    /**
     * Pre-compute reachable blocks from the player's position within the given range.
     * We scan within a horizontal circle (of radius searchRadius) and a vertical range of ±maxDepth.
     */
    public void preCompute() {
        // Clear previous results if any.
        reachableBlockNames.clear();

        BlockPos botPos = player.blockPosition();
        // Iterate horizontally over a circle.
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                // Only consider positions within a circle.
                if (dx * dx + dz * dz > searchRadius * searchRadius) {
                    continue;
                }
                // Iterate vertically from -maxDepth to maxDepth.
                for (int dy = -maxDepth; dy <= maxDepth; dy++) {
                    BlockPos pos = botPos.offset(dx, dy, dz);
                    if (canReachBlock(pos)) {
                        // Get the block's name and add it to the list.
                        Block block = player.level().getBlockState(pos).getBlock();
                        String blockName = block.getName().getString();
                        reachableBlockNames.add(blockName);
                    }
                }
            }
        }
    }

    /**
     * Determines if the block at the given position is considered "reachable."
     * For this example, we assume a block is reachable if it is not air.
     *
     * @param pos The block position to check.
     * @return true if the block is not air, false otherwise.
     */
    private boolean canReachBlock(BlockPos pos) {
        Block block = player.level().getBlockState(pos).getBlock();
        // Consider the block reachable if it's not air.
        return !block.defaultBlockState().isAir();
    }

    /**
     * Returns the list of reachable block names.
     * Make sure to call preCompute() (or one of the detect methods) first.
     *
     * @return List of block names representing reachable blocks.
     */
    public List<String> getReachableBlockNames() {
        return reachableBlockNames;
    }

    /**
     * Asynchronously compute and return the list of reachable block names.
     *
     * @return A CompletableFuture that will complete with the list of reachable block names.
     */
    public CompletableFuture<List<String>> detectNearbyBlocksAsync() {
        return CompletableFuture.supplyAsync(() -> {
            preCompute();
            return getReachableBlockNames();
        });
    }

    /**
     * Synchronously compute and return the list of reachable block names.
     *
     * @return List of reachable block names.
     */
    public List<String> detectNearbyBlocks() {
        preCompute();
        return getReachableBlockNames();
    }
}
