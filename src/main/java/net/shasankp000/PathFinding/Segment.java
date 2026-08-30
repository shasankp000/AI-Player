package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A compatibility view of a movement edge. New code should use {@link PathFinder.PathNode}. */
public final class Segment {
    private final Vec3 start;
    private final Vec3 end;
    private final MovementType movement;
    private boolean sprint;

    public Segment(BlockPos start, BlockPos end, boolean jump, boolean sprint) {
        this(Vec3.atBottomCenterOf(start), Vec3.atBottomCenterOf(end),
                jump ? MovementType.JUMP_UP : MovementType.WALK, sprint);
    }

    public Segment(Vec3 start, Vec3 end, MovementType movement, boolean sprint) {
        this.start = start;
        this.end = end;
        this.movement = movement;
        this.sprint = sprint;
    }

    public BlockPos start() { return BlockPos.containing(start); }
    public BlockPos end() { return BlockPos.containing(end); }
    public Vec3 startTarget() { return start; }
    public Vec3 endTarget() { return end; }
    public MovementType movement() { return movement; }
    public boolean jump() { return movement == MovementType.JUMP_UP; }
    public boolean sprint() { return sprint; }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    @Override
    public String toString() {
        return "Segment[start=" + start + ", end=" + end + ", movement=" + movement + ", sprint=" + sprint + "]";
    }
}
