package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathFinderFixtureTest {
    private static PathFinder.Search finish(InMemoryNavigationWorld world, Vec3 start, BlockPos goal, int horizon) {
        PathFinder.Search search = PathFinder.beginSearch(world, start, goal, horizon, Set.of());
        while (search.advance(10_000) == PathFinder.SearchStatus.SEARCHING) { }
        return search;
    }

    @Test void flatDiagonalPathUsesSafeDiagonalWalk() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-1, 5, 63, -1, 5);
        PathFinder.Search search = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(4, 64, 4), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, search.advance(1));
        assertEquals(GoalDisposition.EXACT, search.disposition());
        assertTrue(search.result().stream().skip(1).allMatch(node -> node.movement() == MovementType.WALK));
        assertTrue(search.result().size() <= 3, "the open floor should smooth to a direct diagonal");
    }

    @Test void diagonalCannotClipAClosedCorner() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-1, 3, 63, -1, 3)
                .solid(1, 64, 0).solid(0, 64, 1);
        PathFinder.Search search = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(1, 64, 1), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, search.advance(1));
        assertTrue(search.result().size() > 2, "the route must go around instead of clipping the corner");
    }

    @Test void cardinalOneBlockJumpIsPreserved() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-1, 0, 63, -1, 1)
                .solid(1, 64, 0).solid(2, 64, 0).solid(3, 64, 0);
        PathFinder.Search search = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(3, 65, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, search.advance(1));
        assertTrue(search.result().stream().anyMatch(node -> node.movement() == MovementType.JUMP_UP));
    }

    @Test void slabStepAndSafeDropRemainActionBoundaries() {
        InMemoryNavigationWorld slabWorld = new InMemoryNavigationWorld().floor(-1, 0, 63, -1, 1)
                .slab(1, 64, 0).slab(2, 64, 0);
        PathFinder.Search slab = finish(slabWorld, new Vec3(0.5, 64, 0.5), new BlockPos(2, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, slab.advance(1));
        assertTrue(slab.result().stream().anyMatch(node -> node.movement() == MovementType.STEP_UP));

        InMemoryNavigationWorld dropWorld = new InMemoryNavigationWorld().solid(0, 66, 0)
                .floor(1, 4, 63, -1, 1);
        PathFinder.Search drop = finish(dropWorld, new Vec3(0.5, 67, 0.5), new BlockPos(4, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, drop.advance(1));
        assertTrue(drop.result().stream().anyMatch(node -> node.movement() == MovementType.DROP));
    }

    @Test void blockedGoalNormalizesButDistantGoalIsFrontier() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-2, 80, 63, -2, 2)
                .solid(4, 64, 0);
        PathFinder.Search normalized = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(4, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, normalized.advance(1));
        assertEquals(GoalDisposition.NORMALIZED_FINAL, normalized.disposition());

        PathFinder.Search frontier = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(75, 64, 0), 16);
        assertEquals(PathFinder.SearchStatus.FOUND, frontier.advance(1));
        assertEquals(GoalDisposition.HORIZON_FRONTIER, frontier.disposition());
    }

    @Test void waterRouteUsesWaterEdges() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-1, 0, 63, -1, 1);
        for (int x = 1; x <= 4; x++) world.water(x, 64, 0);
        PathFinder.Search search = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(4, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, search.advance(1));
        assertTrue(search.result().stream().anyMatch(node -> node.movement().isWaterMovement()));
    }

    @Test void octileHeuristicAndEdgeCostsPreferDiagonalGroundTravel() {
        assertEquals(Math.sqrt(2), PathFinder.heuristic(Vec3.ZERO, new Vec3(1, 0, 1)), 1.0E-9);
        assertTrue(PathFinder.movementCost(MovementType.WALK) < PathFinder.movementCost(MovementType.JUMP_UP));
        assertTrue(PathFinder.movementCost(MovementType.JUMP_UP) < PathFinder.movementCost(MovementType.SWIM));
    }

    @Test void lazyPriorityQueueDiscardsOnlyStaleEntries() {
        assertTrue(PathFinder.isStaleQueueEntry(5.0, 4.0));
        assertTrue(PathFinder.isStaleQueueEntry(5.0, null));
        assertFalse(PathFinder.isStaleQueueEntry(4.0, 4.0));
    }

    @Test void controllerHandlesCornersJumpAndEmergencySurface() {
        NavigationController.Observation ground = new NavigationController.Observation(
                Vec3.ZERO, Vec3.ZERO, true, false, false, 300, 20);
        NavigationController.Command corner = NavigationController.decide(ground,
                new NavigationController.WaypointState(new Vec3(3, 0, 0), MovementType.WALK, true, true, true));
        assertFalse(corner.sprint());
        NavigationController.Command jump = NavigationController.decide(ground,
                new NavigationController.WaypointState(new Vec3(1, 1, 0), MovementType.JUMP_UP, true, true, true));
        assertEquals(NavigationController.JumpCommand.ONCE, jump.jump());
        NavigationController.Command surface = NavigationController.emergencySurface(ground);
        assertEquals(-90.0F, surface.pitch());
        assertEquals(NavigationController.JumpCommand.CONTINUOUS, surface.jump());
        assertFalse(surface.sprint());
    }

    @Test void overlappingSuspensionsAreIdempotent() {
        EnumSet<SuspensionReason> reasons = EnumSet.noneOf(SuspensionReason.class);
        assertTrue(NavigationService.updateSuspensions(reasons, SuspensionReason.EATING, true));
        assertFalse(NavigationService.updateSuspensions(reasons, SuspensionReason.EATING, true));
        assertTrue(NavigationService.updateSuspensions(reasons, SuspensionReason.COMBAT, true));
        NavigationService.updateSuspensions(reasons, SuspensionReason.EATING, false);
        assertEquals(Set.of(SuspensionReason.COMBAT), reasons);
        assertTrue(NavigationService.overrideAllows(SuspensionReason.COMBAT, SuspensionReason.THREAT));
        assertFalse(NavigationService.overrideAllows(SuspensionReason.COMBAT, SuspensionReason.EATING));
    }

    @Test void globalBudgetRespondsToServerLoad() {
        assertEquals(1200, NavigationService.planningBudgetForTickMillis(39.9));
        assertEquals(600, NavigationService.planningBudgetForTickMillis(40.0));
        assertEquals(600, NavigationService.planningBudgetForTickMillis(50.0));
        assertEquals(200, NavigationService.planningBudgetForTickMillis(50.1));
        int[] shares = NavigationService.allocatePlanningShares(1_200,
                List.of(1_000, 1_000, 1_000, 1_000, 1_000), 3);
        assertEquals(1_200, java.util.Arrays.stream(shares).sum());
        assertArrayEquals(new int[]{240, 240, 240, 240, 240}, shares);
        assertEquals(500, java.util.Arrays.stream(NavigationService.allocatePlanningShares(
                1_200, List.of(100, 100, 100, 100, 100), 1)).sum());
    }

    @Test void staleGenerationCannotAffectReplacementRoute() {
        assertTrue(NavigationService.generationMatches(42L, 42L));
        assertFalse(NavigationService.generationMatches(43L, 42L));
    }

    @Test void repeatedReplansTerminateUnlessDistanceImproves() {
        ReplanProgressTracker tracker = new ReplanProgressTracker(20.0);
        assertFalse(tracker.record(19.7, ReplanReason.WAYPOINT_INVALID));
        assertFalse(tracker.record(19.3, ReplanReason.WAYPOINT_INVALID));
        assertFalse(tracker.record(18.9, ReplanReason.WAYPOINT_INVALID));
        assertEquals(0, tracker.consecutiveWithoutProgress(), "cumulative one-block progress resets the bound");
        assertFalse(tracker.record(18.8, ReplanReason.ROUTE_DEVIATION));
        assertFalse(tracker.record(18.8, ReplanReason.ROUTE_DEVIATION));
        assertFalse(tracker.record(18.8, ReplanReason.ROUTE_DEVIATION));
        assertTrue(tracker.record(18.8, ReplanReason.ROUTE_DEVIATION));
        assertEquals(7, tracker.total());
        assertEquals(ReplanReason.ROUTE_DEVIATION, tracker.lastReason());
    }

    @Test void penaltiesExpireAtTheirServerTick() {
        LinkedHashMap<BlockPos, Long> penalties = new LinkedHashMap<>();
        penalties.put(BlockPos.ZERO, 200L);
        BlockPos later = new BlockPos(1, 1, 1);
        penalties.put(later, 201L);
        NavigationService.pruneExpiredPenalties(penalties, 200L);
        assertEquals(Set.of(later), penalties.keySet());
    }

    @Test void gapsAndDamagingDropsAreRejected() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().solid(0, 68, 0)
                .floor(1, 3, 63, 0, 0);
        PathFinder.Search search = finish(world, new Vec3(0.5, 69, 0.5), new BlockPos(3, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.NO_PATH, search.advance(1));
    }

    @Test void unloadedGoalProducesAFrontierInsteadOfTrustingUnknownCollision() {
        InMemoryNavigationWorld world = new InMemoryNavigationWorld().floor(-1, 20, 63, -1, 1)
                .loadedBetween(-2, 12, -2, 2);
        PathFinder.Search search = finish(world, new Vec3(0.5, 64, 0.5), new BlockPos(20, 64, 0), 64);
        assertEquals(PathFinder.SearchStatus.FOUND, search.advance(1));
        assertEquals(GoalDisposition.HORIZON_FRONTIER, search.disposition());
        assertTrue(search.effectiveGoal().x <= 12.5);
    }
}
