package net.shasankp000.DangerZoneDetector;

import net.minecraft.server.network.ServerPlayerEntity;

public class DangerZoneDetector {

    private static final int BOUNDING_BOX_SIZE = 10; // Detection range in blocks

    /**
     * Detects nearby danger zones (lava or cliffs) and calculates the effective danger distance.
     *
     * @param source       The bot entity.
     * @param lavaRange    The range to check for lava blocks.
     * @param cliffRange   The forward range to check for cliffs.
     * @param cliffDepth   The downward range to check for solid blocks (cliff depth).
     * @return The effective danger distance (distance from lava + distance from cliff).
     */
    public static double detectDangerZone(ServerPlayerEntity source, int lavaRange, int cliffRange, int cliffDepth) {
        // Detect nearby lava blocks
        double lavaDistance = LavaDetector.detectNearestLava(source, lavaRange, BOUNDING_BOX_SIZE);
        if (lavaDistance == Double.MAX_VALUE) {
            lavaDistance = 0; // Default to 0 if no lava is nearby
        }

        // Detect nearby cliffs
        double cliffDistance = CliffDetector.detectCliffWithBoundingBox(source, cliffRange, cliffDepth);
        if (cliffDistance == Double.MAX_VALUE) {
            cliffDistance = 0; // Default to 0 if no cliffs are nearby
        }

        // Calculate the effective danger distance
        double effectiveDangerDistance = lavaDistance + cliffDistance;

        // Debug output (optional, for testing purposes)
        System.out.println("Lava Distance: " + lavaDistance);
        System.out.println("Cliff Distance: " + cliffDistance);
        System.out.println("Effective Danger Distance: " + effectiveDangerDistance);

        return effectiveDangerDistance;
    }
}

