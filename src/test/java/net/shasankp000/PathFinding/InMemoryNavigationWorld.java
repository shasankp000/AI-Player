package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class InMemoryNavigationWorld implements NavigationWorldView {
    private final Map<BlockPos, VoxelShape> collisions = new HashMap<>();
    private final Set<BlockPos> water = new HashSet<>();
    private final Set<BlockPos> hazards = new HashSet<>();
    private int minLoadedX = -256, maxLoadedX = 256, minLoadedZ = -256, maxLoadedZ = 256;

    InMemoryNavigationWorld floor(int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) solid(x, y, z);
        return this;
    }

    InMemoryNavigationWorld solid(int x, int y, int z) {
        collisions.put(new BlockPos(x, y, z), Shapes.block());
        return this;
    }

    InMemoryNavigationWorld slab(int x, int y, int z) {
        collisions.put(new BlockPos(x, y, z), Shapes.box(0, 0, 0, 1, 0.5, 1));
        return this;
    }

    InMemoryNavigationWorld water(int x, int y, int z) {
        water.add(new BlockPos(x, y, z));
        return this;
    }

    InMemoryNavigationWorld hazard(int x, int y, int z) {
        hazards.add(new BlockPos(x, y, z));
        return this;
    }

    InMemoryNavigationWorld loadedBetween(int minX, int maxX, int minZ, int maxZ) {
        minLoadedX = minX; maxLoadedX = maxX; minLoadedZ = minZ; maxLoadedZ = maxZ;
        return this;
    }

    @Override public boolean isLoaded(BlockPos pos) {
        return pos.getX() >= minLoadedX && pos.getX() <= maxLoadedX
                && pos.getZ() >= minLoadedZ && pos.getZ() <= maxLoadedZ;
    }

    @Override public VoxelShape collisionShape(BlockPos pos) {
        return collisions.getOrDefault(pos, Shapes.empty());
    }

    @Override public boolean isWater(BlockPos pos) { return water.contains(pos); }
    @Override public boolean isHazard(BlockPos pos) { return hazards.contains(pos); }

    @Override public boolean noBlockCollision(AABB box) {
        for (Map.Entry<BlockPos, VoxelShape> entry : collisions.entrySet()) {
            BlockPos pos = entry.getKey();
            for (AABB local : entry.getValue().toAabbs()) {
                if (box.intersects(local.move(pos.getX(), pos.getY(), pos.getZ()))) return false;
            }
        }
        return true;
    }
}
