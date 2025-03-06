package net.shasankp000.PlayerUtils;

import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Represents an internal 3D map of the blocks surrounding the bot.
 * The map is centered on the bot's current position.
 * The map array is indexed as:
 *   map[dy + verticalRange][dx + horizontalRange][dz + horizontalRange]
 * where:
 *   - dx and dz are the horizontal offsets (east-west and north-south, respectively)
 *   - dy is the vertical offset (relative to the bot's Y coordinate)
 */
public class InternalMap {

    private final ServerPlayerEntity player;
    private final int verticalRange;    // How many blocks above and below the bot to scan
    private final int horizontalRange;  // How many blocks outward (in x and z) from the bot to scan
    private final Block[][][] map;      // 3D array holding scanned block data

    /**
     * Constructs a new InternalMap for the given player.
     *
     * @param player           The player (bot) whose surroundings will be mapped.
     * @param verticalRange    The vertical range: scan from -verticalRange to +verticalRange.
     * @param horizontalRange  The horizontal range: scan from -horizontalRange to +horizontalRange in x and z.
     */
    public InternalMap(ServerPlayerEntity player, int verticalRange, int horizontalRange) {
        this.player = player;
        this.verticalRange = verticalRange;
        this.horizontalRange = horizontalRange;
        // Create the map with dimensions: height = (verticalRange*2 + 1), width & depth = (horizontalRange*2 + 1)
        map = new Block[verticalRange * 2 + 1][horizontalRange * 2 + 1][horizontalRange * 2 + 1];
    }

    /**
     * Scans the blocks surrounding the bot and updates the internal map.
     * The bot's current position is treated as the center (offset 0,0,0) of the map.
     */
    public void updateMap() {
        BlockPos botPos = player.getBlockPos();
        // Loop through all relative offsets within the defined ranges.
        for (int dy = -verticalRange; dy <= verticalRange; dy++) {
            for (int dx = -horizontalRange; dx <= horizontalRange; dx++) {
                for (int dz = -horizontalRange; dz <= horizontalRange; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    Block block = player.getEntityWorld().getBlockState(pos).getBlock();
                    // Store the block in the map array at the corresponding shifted indices.
                    map[dy + verticalRange][dx + horizontalRange][dz + horizontalRange] = block;
                }
            }
        }
    }

    /**
     * Returns the block at the given relative coordinates (dx, dy, dz) from the bot's current position.
     *
     * @param dx Relative x offset (east-west)
     * @param dy Relative y offset (vertical)
     * @param dz Relative z offset (north-south)
     * @return The Block at that location, or null if the coordinates are out of bounds.
     */
    public Block getBlockAt(int dx, int dy, int dz) {
        if (dx < -horizontalRange || dx > horizontalRange ||
                dy < -verticalRange || dy > verticalRange ||
                dz < -horizontalRange || dz > horizontalRange) {
            return null;  // Out of the defined mapping range.
        }
        return map[dy + verticalRange][dx + horizontalRange][dz + horizontalRange];
    }

    /**
     * For debugging: prints a simple representation of the internal map.
     * This example prints each vertical layer (relative dy) as a grid of block initials.
     */
    public void printMap() {
        for (int dy = -verticalRange; dy <= verticalRange; dy++) {
            System.out.println("Layer at relative Y = " + dy + ":");
            for (int dz = -horizontalRange; dz <= horizontalRange; dz++) {
                for (int dx = -horizontalRange; dx <= horizontalRange; dx++) {
                    Block block = getBlockAt(dx, dy, dz);
                    if (block != null) {
                        // Use the block's translation key (e.g., "block.minecraft.stone")
                        String key = block.getName().getString();
                        // Extract the short name (after the last '.')
                        String shortName = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                        // Print the first character of the short name as a simple identifier.
                        System.out.print(shortName + " ");
                    } else {
                        System.out.print("? ");
                    }
                }
                System.out.println();
            }
            System.out.println();
        }
    }

}
