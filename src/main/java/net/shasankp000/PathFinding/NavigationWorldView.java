package net.shasankp000.PathFinding;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/**
 * Minimal world surface consumed by the planner, allowing deterministic fixtures.
 */
public interface NavigationWorldView {
    boolean isLoaded(BlockPos pos);
    VoxelShape collisionShape(BlockPos pos);
    boolean isWater(BlockPos pos);
    boolean isHazard(BlockPos pos);
    boolean noBlockCollision(Box box);

    static NavigationWorldView live(ServerWorld level) {
        return new NavigationWorldView() {
            @Override public boolean isLoaded(BlockPos pos) {
                return level.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
            }
            @Override public VoxelShape collisionShape(BlockPos pos) {
                return level.getBlockState(pos).getCollisionShape(level, pos);
            }
            @Override public boolean isWater(BlockPos pos) {
                return level.getFluidState(pos).isIn(FluidTags.WATER);
            }
            @Override public boolean isHazard(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
                        || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.MAGMA_BLOCK)
                        || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.POWDER_SNOW)
                        || level.getFluidState(pos).isIn(FluidTags.LAVA);
            }
            @Override public boolean noBlockCollision(Box box) {
                return level.isSpaceEmpty(null, box, false);
            }
        };
    }
}
