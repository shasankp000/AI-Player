package net.shasankp000.PathFinding;

import net.minecraft.util.math.Vec3d;

/** Pure observation-to-command policy; action-pack I/O remains in {@link NavigationService}. */
public final class NavigationController {
    private NavigationController() {}

    public enum JumpCommand {
        NONE, ONCE, CONTINUOUS
    }

    public record Observation(Vec3d position, Vec3d velocity, boolean grounded, boolean inWater,
                              boolean horizontalCollision, int air, int hunger) {}

    public record WaypointState(Vec3d target, MovementType movement, boolean sprintRequested,
                                boolean nearTransition, boolean jumpReady) {}

    public record Command(float yaw, float pitch, float forward, float strafe,
                          boolean sprint, JumpCommand jump) {}

    public static Command decide(Observation observation, WaypointState waypoint) {
        double dx = waypoint.target.x - observation.position.x;
        double dz = waypoint.target.z - observation.position.z;
        double horizontal = Math.max(0.01, Math.hypot(dx, dz));
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = waypoint.movement.isWaterMovement()
                ? clamp((float) -Math.toDegrees(Math.atan2(
                        waypoint.target.y - observation.position.y, horizontal)), -35.0F, 35.0F)
                : 0.0F;
        boolean sprint = waypoint.sprintRequested && !waypoint.nearTransition
                && horizontal > 1.5 && !observation.inWater && observation.hunger > 6;
        JumpCommand jump = JumpCommand.NONE;
        if (waypoint.movement == MovementType.JUMP_UP && observation.grounded
                && horizontal <= 1.15 && waypoint.jumpReady) jump = JumpCommand.ONCE;
        else if (waypoint.movement == MovementType.SWIM
                && waypoint.target.y > observation.position.y + 0.2) jump = JumpCommand.CONTINUOUS;
        return new Command(yaw, pitch, 1.0F, 0.0F, sprint, jump);
    }

    public static Command emergencySurface(Observation observation) {
        return new Command(0.0F, -90.0F, 0.25F, 0.0F, false, JumpCommand.CONTINUOUS);
    }

    static boolean hasMeaningfulProgress(double startRemaining, double endRemaining) {
        return startRemaining - endRemaining >= 0.05;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
