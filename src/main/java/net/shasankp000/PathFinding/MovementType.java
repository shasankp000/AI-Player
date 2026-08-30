package net.shasankp000.PathFinding;

/** A server-physics transition used to arrive at a navigation waypoint. */
public enum MovementType {
    START, WALK, STEP_UP, JUMP_UP, DROP, ENTER_WATER, SWIM, EXIT_WATER;

    public boolean isWaterMovement() {
        return this == ENTER_WATER || this == SWIM || this == EXIT_WATER;
    }

    public boolean targetIsWater() {
        return this == ENTER_WATER || this == SWIM;
    }

    public boolean isActionBoundary() {
        return this != WALK && this != STEP_UP;
    }
}
