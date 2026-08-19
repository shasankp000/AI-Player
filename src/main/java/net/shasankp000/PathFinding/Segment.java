package net.shasankp000.PathFinding;

import net.minecraft.util.math.Vec3d;

/** Waypoint-to-waypoint movement instruction emitted by the planner. */
public record Segment(Vec3d start, Vec3d end, MovementType movement, boolean sprint) {
    public boolean targetIsWater() { return movement.targetIsWater(); }
    public boolean isActionBoundary() { return movement.isActionBoundary(); }
}
