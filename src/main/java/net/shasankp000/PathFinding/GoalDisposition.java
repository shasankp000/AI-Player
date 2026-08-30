package net.shasankp000.PathFinding;

/** Describes why a planner selected its effective destination. */
public enum GoalDisposition {
    EXACT,
    NORMALIZED_FINAL,
    HORIZON_FRONTIER
}
