package net.shasankp000.DangerZoneDetector;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CliffDetector {

    /**
     * Detects cliffs using a bounding box.
     *
     * @param source     The bot entity.
     * @param range      The forward range to scan for cliffs.
     * @param depth      The downward range to check for solid blocks.
     * @return Distance to the cliff if detected, or Double.MAX_VALUE if no cliff is found.
     */
    public static double detectCliffWithBoundingBox(ServerPlayer source, int range, int depth) {
        Vec3 botPos = source.position();
        Level world = source.level();

        // Get the direction the bot is facing
        Vec3 facingDirection = source.getViewVector(1.0F).normalize();

        // Iterate through positions in the facing direction
        for (int i = 1; i <= range; i++) {
            // Calculate the current position in the facing direction
            Vec3 checkPos = botPos.add(facingDirection.scale(i));
            BlockPos blockPos = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);

            // Create a bounding box that stretches downward
            AABB detectionBox = new AABB(blockPos).expandTowards(0, -depth, 0);

            boolean hasSolidBlock = false;

            // Iterate through all voxel shapes within the bounding box
            for (VoxelShape shape : world.getBlockCollisions(source, detectionBox)) {
                // Get the bounding box of the current voxel shape
                AABB voxelBox = shape.bounds();
                BlockPos voxelPos = new BlockPos((int) voxelBox.minX, (int) voxelBox.minY, (int) voxelBox.minZ);

                // Check if the block is solid
                BlockState state = world.getBlockState(voxelPos);
                if (state.isRedstoneConductor(world, voxelPos)) {
                    hasSolidBlock = true;
                    break; // Stop checking if a solid block is found
                }
            }

            // If no solid blocks are found, this is a cliff
            if (!hasSolidBlock) {
                return botPos.distanceTo(checkPos);
            }
        }

        // No cliff detected within the specified range
        return Double.MAX_VALUE;
    }
}

