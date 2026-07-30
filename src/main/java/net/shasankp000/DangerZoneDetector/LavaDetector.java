package net.shasankp000.DangerZoneDetector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class LavaDetector {

    /**
     * Detects the nearest lava block by casting rays in multiple directions.
     *
     * @param source The bot entity (or player).
     * @param reach  The maximum distance to check.
     * @return Distance to the nearest lava block, or Double.MAX_VALUE if none found.
     */
    public static double detectNearestLavaWithRaycast(ServerPlayer source, double reach) {
        double nearestDistance = Double.MAX_VALUE;

        // Cast rays in 6 cardinal directions (positive and negative X, Y, Z)
        Vec3[] directions = new Vec3[]{
                new Vec3(1, 0, 0),  // +X
                new Vec3(-1, 0, 0), // -X
                new Vec3(0, 1, 0),  // +Y
                new Vec3(0, -1, 0), // -Y
                new Vec3(0, 0, 1),  // +Z
                new Vec3(0, 0, -1)  // -Z
        };

        for (Vec3 direction : directions) {
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
    private static double rayTraceForLava(ServerPlayer source, Vec3 direction, double reach) {
        Vec3 start = source.getEyePosition(1.0F); // Starting point of the ray
        Vec3 end = start.add(direction.scale(reach)); // End point of the ray

        BlockHitResult blockHit = source.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY,
                source
        ));

        // Check if the block hit is lava
        if (blockHit != null && source.level().getBlockState(blockHit.getBlockPos()).is(Blocks.LAVA)) {
            return start.distanceTo(blockHit.getLocation());
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
    public static double detectNearestLavaWithBoundingBox(ServerPlayer source, int range) {
        Level world = source.level();

        // Define a bounding box around the bot
        AABB boundingBox = source.getBoundingBox().inflate(range, range, range);

        double nearestDistance = Double.MAX_VALUE;

        // Iterate through all block positions within the bounding box
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = (int) boundingBox.minX; x <= (int) boundingBox.maxX; x++) {
            for (int y = (int) boundingBox.minY; y <= (int) boundingBox.maxY; y++) {
                for (int z = (int) boundingBox.minZ; z <= (int) boundingBox.maxZ; z++) {
                    mutable.set(x, y, z);

                    // Check if the block is a lava source or flowing lava
                    if (world.getBlockState(mutable).is(Blocks.LAVA)) {
                        double distance = source.position().distanceTo(Vec3.atCenterOf(mutable));
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                        }
                    }
                }
            }
        }

        return nearestDistance;
    }

    public static double detectNearestLava(ServerPlayer source, double reach, int range) {
        // Step 1: Try raycasting for visible lava
        double nearestLavaFromRaycast = detectNearestLavaWithRaycast(source, reach);

        // Step 2: If no visible lava, fall back to bounding box
        if (nearestLavaFromRaycast == Double.MAX_VALUE) {
            return detectNearestLavaWithBoundingBox(source, range);
        }

        return nearestLavaFromRaycast;
    }


}
