package net.shasankp000.PathFinding;

/** Cause recorded whenever an active session requests a replacement plan. */
public enum ReplanReason {
    INITIAL,
    HORIZON_ADVANCE,
    WAYPOINT_INVALID,
    ROUTE_DEVIATION,
    UNEXPECTED_FALL,
    STALL_RECOVERY,
    RESUMED,
    OVERRIDE_COMPLETED,
    SURFACED
}
