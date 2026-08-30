package net.shasankp000.PathFinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class NavigationMathTest {
    @Test
    void optionsClampUnsafeBudgets() {
        NavigationOptions options = new NavigationOptions(true, 1, 1);
        assertEquals(8, options.planningHorizon());
        assertEquals(32, options.expansionsPerTick());
        assertTrue(options.sprint());
    }

    @Test
    void segmentDistanceUsesTheClosestPoint() {
        assertEquals(2.0, NavigationService.distanceFromSegment(
                new Vec3(5, 70, 2), new Vec3(0, 64, 0), new Vec3(10, 64, 0)), 1.0E-9);
        assertEquals(1.0, NavigationService.distanceFromSegment(
                new Vec3(11, 64, 0), new Vec3(0, 64, 0), new Vec3(10, 64, 0)), 1.0E-9);
    }

    @Test
    void segmentPreservesHalfBlockTargetsAndMovementType() {
        Segment segment = new Segment(new Vec3(0.5, 64.0, 0.5), new Vec3(1.5, 64.5, 0.5),
                MovementType.STEP_UP, false);
        assertEquals(64.5, segment.endTarget().y, 1.0E-9);
        assertEquals(MovementType.STEP_UP, segment.movement());
        assertFalse(segment.jump());
    }

    @Test
    void waterTargetsExcludeExitLandings() {
        assertTrue(MovementType.ENTER_WATER.targetIsWater());
        assertTrue(MovementType.SWIM.targetIsWater());
        assertFalse(MovementType.EXIT_WATER.targetIsWater());
        assertTrue(MovementType.EXIT_WATER.isActionBoundary());
    }

    @Test
    void resultReportsTerminalMeaning() {
        NavigationResult reached = new NavigationResult(NavigationResult.Status.REACHED,
                new BlockPos(1, 2, 3), "done");
        assertTrue(reached.reached());
        assertFalse(new NavigationResult(NavigationResult.Status.STUCK,
                BlockPos.ZERO, "stuck").reached());
    }

    @Test
    void fullBlockSurfaceSupportsExactIntegerFeetHeight() {
        assertTrue(PathFinder.collisionBoxesSupport(
                List.of(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1)),
                0.5, 0.5, 1.0));
    }

    @Test
    void slabSurfaceSupportsHalfBlockFeetHeight() {
        assertTrue(PathFinder.collisionBoxesSupport(
                List.of(new net.minecraft.world.phys.AABB(0, 0, 0, 1, 0.5, 1)),
                0.5, 0.5, 0.5));
    }

    @Test
    void collisionSurfaceDoesNotSupportOutsideItsFootprint() {
        assertFalse(PathFinder.collisionBoxesSupport(
                List.of(new net.minecraft.world.phys.AABB(0, 0, 0, 0.25, 1, 0.25)),
                0.5, 0.5, 1.0));
    }

    @Test
    void oneBlockJumpClearsTheBlockFaceEarly() {
        double takeoffY = 100.0;
        double landingY = 101.0;
        assertTrue(PathFinder.jumpArcY(takeoffY, landingY, 1.0 / 3.0) > 101.0);
        assertEquals(101.0, PathFinder.jumpArcY(takeoffY, landingY, 1.0), 1.0E-9);
    }

    @Test
    void jumpApexStaysWithinVanillaOneBlockJumpHeight() {
        assertEquals(101.25, PathFinder.jumpArcY(100.0, 101.0, 0.5), 1.0E-9);
    }
}
