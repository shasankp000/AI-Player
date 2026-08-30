package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Minimal world surface consumed by the planner, allowing deterministic fixtures. */
public interface NavigationWorldView {
    boolean isLoaded(BlockPos pos);
    VoxelShape collisionShape(BlockPos pos);
    boolean isWater(BlockPos pos);
    boolean isHazard(BlockPos pos);
    boolean noBlockCollision(AABB box);

    static NavigationWorldView live(ServerLevel level) {
        return new NavigationWorldView() {
            @Override public boolean isLoaded(BlockPos pos) { return level.isLoaded(pos); }
            @Override public VoxelShape collisionShape(BlockPos pos) {
                return level.getBlockState(pos).getCollisionShape(level, pos);
            }
            @Override public boolean isWater(BlockPos pos) {
                return level.getFluidState(pos).is(FluidTags.WATER);
            }
            @Override public boolean isHazard(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                        || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                        || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                        || level.getFluidState(pos).is(FluidTags.LAVA);
            }
            @Override public boolean noBlockCollision(AABB box) {
                return level.noBlockCollision(null, box, false);
            }
        };
    }
}
