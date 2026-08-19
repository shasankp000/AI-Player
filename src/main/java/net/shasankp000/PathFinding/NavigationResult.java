package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;

/** Terminal result from a server-authoritative navigation session. */
public record NavigationResult(Status status, BlockPos finalPosition, String message) {
    public enum Status { REACHED, CANCELLED, NO_PATH, STUCK, INVALID_GOAL, PLAYER_UNAVAILABLE }

    public boolean reached() { return status == Status.REACHED; }
}
