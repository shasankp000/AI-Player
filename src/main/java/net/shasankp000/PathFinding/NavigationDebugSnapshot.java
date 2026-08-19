package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/** Immutable server-thread snapshot used by the in-game navigation debugger. */
public record NavigationDebugSnapshot(
        boolean navigating,
        String phase,
        BlockPos requestedGoal,
        Vec3d effectiveGoal,
        GoalDisposition disposition,
        int waypointIndex,
        int waypointCount,
        Set<SuspensionReason> suspensions,
        int air,
        int recoveries,
        int penalties,
        int totalReplans,
        ReplanReason lastReplanReason,
        int searchOpen,
        int searchClosed,
        int searchExpansions,
        int globalPlanningBudget
) {}
