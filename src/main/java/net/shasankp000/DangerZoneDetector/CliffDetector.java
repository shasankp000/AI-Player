package net.shasankp000.DangerZoneDetector;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public class CliffDetector {

    /**
     * Detects cliffs using a bounding box.
     *
     * @param source     The bot entity.
     * @param range      The forward range to scan for cliffs.
     * @param depth      The downward range to check for solid blocks.
     * @return Distance to the cliff if detected, or Double.MAX_VALUE if no cliff is found.
     */
    public static double detectCliffWithBoundingBox(ServerPlayerEntity source, int range, int depth) {
        Vec3d botPos = source.getPos();
        World world = source.getWorld();

        // Get the direction the bot is facing
        Vec3d facingDirection = source.getRotationVec(1.0F).normalize();

        // Iterate through positions in the facing direction
        for (int i = 1; i <= range; i++) {
            // Calculate the current position in the facing direction
            Vec3d checkPos = botPos.add(facingDirection.multiply(i));
            BlockPos blockPos = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);

            // Create a bounding box that stretches downward
            Box detectionBox = new Box(blockPos).stretch(0, -depth, 0);

            boolean hasSolidBlock = false;

            // Iterate through all voxel shapes within the bounding box
            for (VoxelShape shape : world.getBlockCollisions(source, detectionBox)) {
                // Get the bounding box of the current voxel shape
                Box voxelBox = shape.getBoundingBox();
                BlockPos voxelPos = new BlockPos((int) voxelBox.minX, (int) voxelBox.minY, (int) voxelBox.minZ);

                // Check if the block is solid
                BlockState state = world.getBlockState(voxelPos);
                if (state.isSolidBlock(world, voxelPos)) {
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

