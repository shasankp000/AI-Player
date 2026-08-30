package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;

/** Terminal result of a server-authoritative mining session. */
public record MiningResult(Status status, BlockPos target, String message) {

    public enum Status {
        SUCCESS,
        CANCELLED,
        TIMED_OUT,
        INVALID_TARGET,
        OUT_OF_RANGE,
        UNBREAKABLE,
        PLAYER_UNAVAILABLE,
        TARGET_CHANGED,
        FAILED
    }

    public static MiningResult success(BlockPos target) {
        return new MiningResult(Status.SUCCESS, target, "Mining complete!");
    }

    public static MiningResult failure(Status status, BlockPos target, String message) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("Failure cannot use SUCCESS status");
        return new MiningResult(status, target, message);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }

    public String functionMessage() {
        return succeeded() ? message : "⚠️ Failed to mine block: " + message;
    }
}
