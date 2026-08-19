package net.shasankp000.PathFinding;

/** Immutable controls for a navigation request. */
public record NavigationOptions(boolean sprint, int planningHorizon, int expansionsPerTick) {
    public static final int DEFAULT_HORIZON = 64;
    public static final int DEFAULT_EXPANSIONS_PER_TICK = 400;

    public NavigationOptions {
        planningHorizon = Math.max(8, planningHorizon);
        expansionsPerTick = Math.max(32, expansionsPerTick);
    }

    public static NavigationOptions of(boolean sprint) {
        return new NavigationOptions(sprint, DEFAULT_HORIZON, DEFAULT_EXPANSIONS_PER_TICK);
    }
}
