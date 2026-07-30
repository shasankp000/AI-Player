package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;

public class Segment {
    private final BlockPos start;
    private final BlockPos end;
    private final boolean jump;
    private boolean sprint; // <--- mutable!

    public Segment(BlockPos start, BlockPos end, boolean jump, boolean sprint) {
        this.start = start;
        this.end = end;
        this.jump = jump;
        this.sprint = sprint;
    }

    public BlockPos start() { return start; }
    public BlockPos end() { return end; }
    public boolean jump() { return jump; }
    public boolean sprint() { return sprint; }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    @Override
    public String toString() {
        return "Segment[start=" + start + ", end=" + end + ", jump=" + jump + ", sprint=" + sprint + "]";
    }
}

