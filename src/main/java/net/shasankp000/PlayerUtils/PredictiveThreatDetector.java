package net.shasankp000.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Predictive threat detection - detects enemies BEFORE they shoot
 * Phase-based approach:
 * 1. Detect bow being drawn
 * 2. Calculate evasive direction
 * 3. Execute dodge when arrow fires
 */
public class PredictiveThreatDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger("predictive-threat");

    // Detection range for ranged threats
    private static final double THREAT_DETECTION_RANGE = 25.0;

    /**
     * Represents an entity that is preparing to shoot
     */
    public static class DrawingBowThreat {
        public final LivingEntity shooter;
        public final double distance;
        public final Vec3d shooterPos;
        public final Vec3d aimDirection;
        public final int drawTime; // How long bow has been drawn (in ticks)
        public final boolean fullyDrawn; // Is bow fully charged?

        public DrawingBowThreat(LivingEntity shooter, ServerPlayerEntity bot) {
            this.shooter = shooter;
            this.shooterPos = shooter.getPos();
            this.distance = Math.sqrt(shooter.squaredDistanceTo(bot));

            // Calculate aim direction based on shooter's look vector
            Vec3d lookVec = shooter.getRotationVector();
            this.aimDirection = lookVec.normalize();

            // Get item use time (how long bow has been drawn)
            this.drawTime = shooter.getItemUseTime();
            this.fullyDrawn = this.drawTime >= 20; // Bow is fully drawn at 20 ticks
        }

        /**
         * Check if shooter is aiming at the bot
         */
        public boolean isAimingAtBot(ServerPlayerEntity bot) {
            Vec3d botPos = bot.getPos();
            Vec3d directionToBot = botPos.subtract(shooterPos).normalize();

            // Calculate dot product to see if aim direction aligns with bot direction
            double dotProduct = aimDirection.dotProduct(directionToBot);

            // If dot product > 0.8, shooter is aiming pretty close to bot
            return dotProduct > 0.8;
        }

        /**
         * Calculate where the arrow will go (predicted trajectory)
         */
        public Vec3d predictArrowTrajectory() {
            // Arrow travels in the direction the shooter is looking
            // Speed depends on draw time (max speed at 20 ticks)
            double speed = Math.min(drawTime / 20.0, 1.0) * 3.0; // Max 3.0 blocks/tick
            return aimDirection.multiply(speed);
        }
    }

    /**
     * Detect all entities within range that are drawing bows
     */
    public static List<DrawingBowThreat> detectDrawingBows(ServerPlayerEntity bot) {
        ServerWorld world = (ServerWorld) bot.getWorld();
        Box searchBox = bot.getBoundingBox().expand(THREAT_DETECTION_RANGE);

        // Find all living entities within range
        List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
            LivingEntity.class,
            searchBox,
            entity -> {
                // Exclude the bot itself
                if (entity.getUuid().equals(bot.getUuid())) {
                    return false;
                }

                // Check if entity is using an item
                if (!entity.isUsingItem()) {
                    return false;
                }

                // Check if the item being used is a bow or crossbow
                ItemStack activeItem = entity.getActiveItem();
                return activeItem.getItem() instanceof RangedWeaponItem;
            }
        );

        // Convert to DrawingBowThreat objects
        List<DrawingBowThreat> threats = nearbyEntities.stream()
            .map(entity -> new DrawingBowThreat(entity, bot))
            .filter(threat -> threat.isAimingAtBot(bot)) // Only include threats aiming at bot
            .collect(Collectors.toList());

        // Don't log here - it's spammy (every 50ms). Phase 1 detection logs it instead.

        return threats;
    }

    /**
     * Get the most dangerous threat (closest and most charged)
     */
    public static DrawingBowThreat getMostDangerousThreat(List<DrawingBowThreat> threats) {
        return threats.stream()
            .max((t1, t2) -> {
                // Prioritize by draw time first, then by distance
                int drawCompare = Integer.compare(t1.drawTime, t2.drawTime);
                if (drawCompare != 0) {
                    return drawCompare;
                }
                // Closer is more dangerous
                return Double.compare(t2.distance, t1.distance);
            })
            .orElse(null);
    }

    /**
     * Calculate optimal evasive direction to dodge the predicted arrow
     * Returns a perpendicular direction to the arrow trajectory
     */
    public static Vec3d calculateEvasiveDirection(DrawingBowThreat threat, ServerPlayerEntity bot) {
        Vec3d arrowTrajectory = threat.predictArrowTrajectory();

        // Calculate perpendicular direction (90 degrees to trajectory)
        // For a vector (x, z), perpendicular is (-z, x) or (z, -x)
        Vec3d perpendicular1 = new Vec3d(-arrowTrajectory.z, 0, arrowTrajectory.x).normalize();
        Vec3d perpendicular2 = new Vec3d(arrowTrajectory.z, 0, -arrowTrajectory.x).normalize();

        // Choose the direction that moves bot away from shooter
        Vec3d botPos = bot.getPos();
        Vec3d shooterPos = threat.shooterPos;
        Vec3d awayFromShooter = botPos.subtract(shooterPos).normalize();

        // Pick whichever perpendicular direction is more aligned with moving away
        double dot1 = perpendicular1.dotProduct(awayFromShooter);
        double dot2 = perpendicular2.dotProduct(awayFromShooter);

        Vec3d evasiveDir = dot1 > dot2 ? perpendicular1 : perpendicular2;

        LOGGER.info("📐 Calculated evasive direction: {} (perpendicular to arrow)", evasiveDir);
        return evasiveDir;
    }

    /**
     * Detect if an arrow was just fired by checking if shooter stopped using the bow
     * This is MORE RELIABLE than scanning for arrow entities (arrows move too fast)
     *
     * The key insight: When shooter releases bow, isUsingItem() immediately becomes false
     * We detect this state change from "drawing" to "not drawing" = arrow fired!
     */
    public static boolean detectArrowRelease(ServerPlayerEntity bot, DrawingBowThreat threat) {
        // Check if shooter stopped using the item (released the bow)
        // Note: We check this BEFORE validating threat in AutoFaceEntity
        boolean wasDrawing = threat.shooter.isUsingItem();

        if (!wasDrawing) {
            // Shooter stopped drawing - arrow was either fired or cancelled
            // To distinguish: check if bow is still held in hand
            ItemStack mainHand = threat.shooter.getMainHandStack();
            ItemStack offHand = threat.shooter.getOffHandStack();

            boolean stillHoldingBow =
                (mainHand.getItem() instanceof RangedWeaponItem) ||
                (offHand.getItem() instanceof RangedWeaponItem);

            if (stillHoldingBow) {
                // Still holding bow but not drawing = arrow was fired!
                LOGGER.info("🏹 ARROW RELEASED by {} - DODGE NOW!", threat.shooter.getName().getString());
                return true;
            } else {
                // Switched away from bow = cancelled, not fired
                LOGGER.info("Threat cancelled - shooter switched weapons");
                return false;
            }
        }

        // Still drawing, no release yet
        return false;
    }

    /**
     * Check if the threat is no longer valid (stopped drawing, died, etc.)
     */
    public static boolean isThreatStillValid(DrawingBowThreat threat) {
        if (threat == null) {
            return false;
        }

        // Check if shooter is still alive
        if (!threat.shooter.isAlive() || threat.shooter.isRemoved()) {
            return false;
        }

        // Check if shooter is still using an item (drawing bow)
        if (!threat.shooter.isUsingItem()) {
            // Don't log here - this happens when arrow is fired (expected)
            return false;
        }

        return true;
    }
}

