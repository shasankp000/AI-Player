package net.shasankp000.DangerZoneDetector;

import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class LavaDetector {

    /**
     * Detects the nearest lava block by casting rays in multiple directions.
     *
     * @param source The bot entity (or player).
     * @param reach  The maximum distance to check.
     * @return Distance to the nearest lava block, or Double.MAX_VALUE if none found.
     */
    public static double detectNearestLavaWithRaycast(ServerPlayerEntity source, double reach) {
        double nearestDistance = Double.MAX_VALUE;

        // Cast rays in 6 cardinal directions (positive and negative X, Y, Z)
        Vec3d[] directions = new Vec3d[]{
                new Vec3d(1, 0, 0),  // +X
                new Vec3d(-1, 0, 0), // -X
                new Vec3d(0, 1, 0),  // +Y
                new Vec3d(0, -1, 0), // -Y
                new Vec3d(0, 0, 1),  // +Z
                new Vec3d(0, 0, -1)  // -Z
        };

        for (Vec3d direction : directions) {
            double distance = rayTraceForLava(source, direction, reach);
            if (distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        return nearestDistance;
    }

    /**
     * Casts a single ray in a given direction to detect lava blocks.
     *
     * @param source    The bot entity (or player).
     * @param direction The direction to cast the ray.
     * @param reach     The maximum distance to cast.
     * @return Distance to the nearest lava block, or Double.MAX_VALUE if none found.
     */
    private static double rayTraceForLava(ServerPlayerEntity source, Vec3d direction, double reach) {
        Vec3d start = source.getCameraPosVec(1.0F); // Starting point of the ray
        Vec3d end = start.add(direction.multiply(reach)); // End point of the ray

        BlockHitResult blockHit = source.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                source
        ));

        // Check if the block hit is lava
        if (blockHit != null && source.getWorld().getBlockState(blockHit.getBlockPos()).isOf(Blocks.LAVA)) {
            return start.distanceTo(blockHit.getPos());
        }

        return Double.MAX_VALUE; // No lava found in this direction
    }

    /**
     * Detects the nearest lava block within a bounding box around the bot.
     *
     * @param source The bot entity.
     * @param range  The search range (distance in blocks from the bot).
     * @return Distance to the nearest lava block, or Double.MAX_VALUE if none found.
     */
    public static double detectNearestLavaWithBoundingBox(ServerPlayerEntity source, int range) {
        World world = source.getWorld();

        // Define a bounding box around the bot
        Box boundingBox = source.getBoundingBox().expand(range, range, range);

        double nearestDistance = Double.MAX_VALUE;

        // Iterate through all block positions within the bounding box
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = (int) boundingBox.minX; x <= (int) boundingBox.maxX; x++) {
            for (int y = (int) boundingBox.minY; y <= (int) boundingBox.maxY; y++) {
                for (int z = (int) boundingBox.minZ; z <= (int) boundingBox.maxZ; z++) {
                    mutable.set(x, y, z);

                    // Check if the block is a lava source or flowing lava
                    if (world.getBlockState(mutable).isOf(Blocks.LAVA)) {
                        double distance = source.getPos().distanceTo(Vec3d.ofCenter(mutable));
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                        }
                    }
                }
            }
        }

        return nearestDistance;
    }

    public static double detectNearestLava(ServerPlayerEntity source, double reach, int range) {
        // Step 1: Try raycasting for visible lava
        double nearestLavaFromRaycast = detectNearestLavaWithRaycast(source, reach);

        // Step 2: If no visible lava, fall back to bounding box
        if (nearestLavaFromRaycast == Double.MAX_VALUE) {
            return detectNearestLavaWithBoundingBox(source, range);
        }

        return nearestLavaFromRaycast;
    }


}
