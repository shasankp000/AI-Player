package net.shasankp000.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RangedWeaponUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("ranged-weapon-utils");

    /**
     * Check if the bot has a bow or crossbow equipped (mainhand or offhand only)
     */
    public static boolean hasBowOrCrossbowEquipped(ServerPlayerEntity bot) {
        ItemStack mainHand = bot.getMainHandStack();
        ItemStack offHand = bot.getOffHandStack();

        String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
        String offHandName = offHand.getItem().getName().getString().toLowerCase();

        return mainHandName.contains("bow") || mainHandName.contains("crossbow") ||
               offHandName.contains("bow") || offHandName.contains("crossbow");
    }

    /**
     * Check if the bot has a bow or crossbow anywhere in inventory (equipped or not)
     */
    public static boolean hasBowOrCrossbow(ServerPlayerEntity bot) {
        // First check if equipped
        if (hasBowOrCrossbowEquipped(bot)) {
            return true;
        }

        // Check entire inventory using our find methods
        int crossbowSlot = findCrossbowInInventory(bot);
        if (crossbowSlot != -1) {
            return true;
        }

        int bowSlot = findBowInInventory(bot);
        return bowSlot != -1;
    }

    /**
     * Check if the bot has arrows in inventory
     */
    public static boolean hasArrows(ServerPlayerEntity bot) {
        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("arrow")) {
                return true;
            }
        }

        // Check rest of inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("arrow")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the bot has firework rockets in inventory (for crossbows)
     */
    public static boolean hasFireworkRockets(ServerPlayerEntity bot) {
        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
                return true;
            }
        }

        // Check rest of inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if firework rocket is in offhand (required for crossbow)
     */
    public static boolean isFireworkInOffhand(ServerPlayerEntity bot) {
        ItemStack offHand = bot.getOffHandStack();
        return offHand.getItem().getName().getString().toLowerCase().contains("firework rocket");
    }

    /**
     * Get the hotbar slot containing firework rockets, or -1 if not found
     */
    public static int getFireworkSlot(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if the bot has a crossbow equipped (mainhand or offhand only)
     */
    public static boolean hasCrossbowEquipped(ServerPlayerEntity bot) {
        ItemStack mainHand = bot.getMainHandStack();
        ItemStack offHand = bot.getOffHandStack();

        String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
        String offHandName = offHand.getItem().getName().getString().toLowerCase();

        return mainHandName.contains("crossbow") || offHandName.contains("crossbow");
    }

    /**
     * Check if the bot has a crossbow anywhere in inventory (equipped or not)
     */
    public static boolean hasCrossbow(ServerPlayerEntity bot) {
        // First check if equipped
        if (hasCrossbowEquipped(bot)) {
            return true;
        }

        // Check inventory using our find method
        int crossbowSlot = findCrossbowInInventory(bot);
        return crossbowSlot != -1;
    }

    /**
     * Check if the bot has a bow equipped (not crossbow) - mainhand or offhand only
     */
    public static boolean hasBowEquipped(ServerPlayerEntity bot) {
        ItemStack mainHand = bot.getMainHandStack();
        ItemStack offHand = bot.getOffHandStack();

        String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
        String offHandName = offHand.getItem().getName().getString().toLowerCase();

        return (mainHandName.contains("bow") && !mainHandName.contains("crossbow")) ||
               (offHandName.contains("bow") && !offHandName.contains("crossbow"));
    }

    /**
     * Check if the bot has a bow anywhere in inventory (equipped or not)
     */
    public static boolean hasBow(ServerPlayerEntity bot) {
        // First check if equipped
        if (hasBowEquipped(bot)) {
            return true;
        }

        // Check inventory using our find method
        int bowSlot = findBowInInventory(bot);
        return bowSlot != -1;
    }

    /**
     * Get the appropriate ammo type for the currently equipped ranged weapon
     * Returns: "arrow", "firework", or null if no valid ammo
     * Note: Firework rockets must be in offhand to work with crossbows
     */
    public static String getAmmoType(ServerPlayerEntity bot) {
        if (hasCrossbow(bot)) {
            // Crossbows can use firework rockets (preferred for explosive damage) or arrows
            // BUT firework rockets MUST be in offhand to work!
            if (isFireworkInOffhand(bot)) {
                return "firework";
            } else if (hasArrows(bot)) {
                return "arrow";
            }
        } else if (hasBow(bot)) {
            // Bows only use arrows
            if (hasArrows(bot)) {
                return "arrow";
            }
        }
        return null;
    }

    /**
     * Check if the bot can shoot (has ranged weapon and appropriate ammo)
     */
    public static boolean canShoot(ServerPlayerEntity bot) {
        boolean hasWeapon = hasBowOrCrossbow(bot);
        String ammoType = getAmmoType(bot);
        boolean hasAmmo = ammoType != null;

        LOGGER.debug("Can shoot check - Has weapon: {}, Ammo type: {}", hasWeapon, ammoType);

        return hasWeapon && hasAmmo;
    }

    /**
     * Calculate the optimal target entity for shooting based on distance and threat level
     * Now supports BOTH hostile mobs and hostile players
     */
    public static Entity findBestShootingTarget(ServerPlayerEntity bot, List<Entity> hostileEntities) {
        if (hostileEntities.isEmpty()) {
            return null;
        }

        Entity bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        Vec3d botPos = bot.getPos();

        for (Entity entity : hostileEntities) {
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) {
                continue;
            }

            double distance = botPos.distanceTo(entity.getPos());

            // Skip targets that are too close (melee range) or too far
            if (distance < 3.0 || distance > 32.0) {
                continue;
            }

            double threatScore;

            // Use player-specific threat calculation for hostile players
            if (entity instanceof PlayerEntity player) {
                // Get player threat from retaliation system
                double playerThreat = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.getPlayerThreatLevel(bot, player);

                // Apply distance weighting (same as for mobs)
                double distanceMultiplier;
                if (distance < 5.0) {
                    distanceMultiplier = 2.0;
                } else if (distance < 10.0) {
                    distanceMultiplier = 1.5;
                } else if (distance < 20.0) {
                    distanceMultiplier = 1.0;
                } else if (distance < 30.0) {
                    distanceMultiplier = 1.0 - ((distance - 20.0) / 20.0) * 0.5;
                } else {
                    distanceMultiplier = 0.3 * Math.exp(-(distance - 30.0) / 15.0);
                }

                threatScore = playerThreat * distanceMultiplier;

                LOGGER.debug("Player threat: {} at {}m - Base: {}, Mult: {}, Final: {}",
                    player.getName().getString(),
                    String.format("%.1f", distance),
                    String.format("%.1f", playerThreat),
                    String.format("%.2f", distanceMultiplier),
                    String.format("%.1f", threatScore));
            } else {
                // Use mob threat calculation
                threatScore = calculateThreatScore(entity.getName().getString(), distance);
            }

            // Prioritize targets that are in line of sight
            if (hasLineOfSight(bot, entity)) {
                threatScore *= 1.5;
            }

            if (threatScore > bestScore) {
                bestScore = threatScore;
                bestTarget = entity;
            }
        }

        return bestTarget;
    }

    /**
     * Calculate threat score for prioritizing shooting targets
     * Now includes DISTANCE WEIGHTING - closer threats get higher priority
     *
     * Formula: finalThreat = baseThreat * distanceMultiplier
     * - Very close (< 5m): 2.0x multiplier (URGENT)
     * - Close (5-10m): 1.5x multiplier (HIGH PRIORITY)
     * - Medium (10-20m): 1.0x multiplier (NORMAL)
     * - Far (20-30m): 0.5x multiplier (LOW PRIORITY)
     * - Very far (> 30m): 0.3x multiplier (IGNORE unless high base threat)
     */
    private static double calculateThreatScore(String entityName, double distance) {
        double baseThreat;

        // Comprehensive threat scores for all hostile mobs
        switch (entityName) {
            // CRITICAL PRIORITY - Explosive/Dangerous at range
            case "Creeper":
                baseThreat = 100.0; // Highest priority - explosive, must keep at range
                break;

            // VERY HIGH PRIORITY - Ranged attackers (counter with ranged)
            case "Skeleton", "Stray", "Bogged":
                baseThreat = 50.0; // Ranged enemies with arrows
                break;
            case "Witch":
                baseThreat = 48.0; // Potion thrower, very dangerous
                break;
            case "Pillager":
                baseThreat = 45.0; // Crossbow user
                break;
            case "Blaze":
                baseThreat = 43.0; // Fireball shooter
                break;
            case "Ghast":
                baseThreat = 40.0; // Long-range fireball attacker
                break;

            // HIGH PRIORITY - Fast/Agile or special mechanics
            case "Breeze":
                baseThreat = 38.0; // Wind charge attacker from 1.21
                break;
            case "Phantom":
                baseThreat = 35.0; // Flying, fast-moving
                break;
            case "Warden":
                baseThreat = 95.0; // Extremely dangerous, but avoid if possible
                break;
            case "Evoker":
                baseThreat = 42.0; // Summons vexes and fangs
                break;
            case "Vex":
                baseThreat = 32.0; // Flying, phases through blocks
                break;

            // MEDIUM-HIGH PRIORITY - Dangerous melee or mechanics
            case "Ravager":
                baseThreat = 36.0; // Heavy damage, but slow
                break;
            case "Piglin Brute":
                baseThreat = 34.0; // High damage melee
                break;
            case "Vindicator":
                baseThreat = 33.0; // Fast melee attacker
                break;
            case "Hoglin":
                baseThreat = 30.0; // Charges and deals knockback
                break;
            case "Zoglin":
                baseThreat = 30.0; // Hostile version of Hoglin
                break;

            // MEDIUM PRIORITY - Mobile or group threats
            case "Spider":
            case "Cave Spider":
                baseThreat = 28.0; // Fast-moving, can climb
                break;
            case "Slime", "Magma Cube":
                baseThreat = 26.0; // Bouncy, medium threat
                break;
            case "Piglin":
                baseThreat = 24.0; // Can use ranged or melee
                break;
            case "Zombified Piglin":
                baseThreat = 22.0; // Aggressive when provoked
                break;

            // MEDIUM-LOW PRIORITY - Standard melee
            case "Zombie", "Husk":
                baseThreat = 18.0; // Basic melee, slow
                break;
            case "Drowned":
                baseThreat = 20.0; // Can use tridents at range
                break;
            case "Zombie Villager":
                baseThreat = 17.0; // Similar to zombie
                break;

            // LOW PRIORITY - Weak or situational
            case "Enderman":
                baseThreat = 25.0; // Dangerous if provoked, teleports
                break;
            case "Silverfish":
                baseThreat = 12.0; // Low health, small
                break;
            case "Endermite":
                baseThreat = 10.0; // Very weak
                break;
            case "Shulker":
                baseThreat = 29.0; // Levitation bullets, hides in shell
                break;

            // UNDERWATER THREATS
            case "Guardian":
                baseThreat = 31.0; // Laser beam attack
                break;
            case "Elder Guardian":
                baseThreat = 37.0; // Mining fatigue + laser
                break;

            // DEFAULT - Unknown or neutral mobs that became hostile
            default:
                baseThreat = 15.0; // Moderate default threat
                LOGGER.debug("Unknown entity type for threat scoring: {}", entityName);
                break;
        }

        // DISTANCE WEIGHTING - Prioritize closer threats (CRITICAL CHANGE)
        double distanceMultiplier;

        if (distance < 5.0) {
            // VERY CLOSE - Immediate danger, double the threat
            distanceMultiplier = 2.0;
        } else if (distance < 10.0) {
            // CLOSE - High priority
            distanceMultiplier = 1.5;
        } else if (distance < 20.0) {
            // MEDIUM - Normal priority
            distanceMultiplier = 1.0;
        } else if (distance < 30.0) {
            // FAR - Lower priority, but still valid target
            // Linear decay from 1.0 to 0.5
            distanceMultiplier = 1.0 - ((distance - 20.0) / 20.0) * 0.5;
        } else {
            // VERY FAR - Only shoot if base threat is very high (creepers, ranged attackers)
            // Exponential decay beyond 30 blocks
            distanceMultiplier = 0.3 * Math.exp(-(distance - 30.0) / 15.0);
        }

        // Special case: Creepers are ALWAYS priority if they're moving closer
        if (entityName.equals("Creeper") && distance < 10.0) {
            distanceMultiplier *= 1.5; // Extra boost for close creepers
        }

        double finalThreat = baseThreat * distanceMultiplier;

        LOGGER.debug("Threat calculation: {} at {}m - Base: {}, Distance mult: {}, Final: {}",
            entityName,
            String.format("%.1f", distance),
            String.format("%.1f", baseThreat),
            String.format("%.2f", distanceMultiplier),
            String.format("%.1f", finalThreat));

        return finalThreat;
    }

    /**
     * Simple line of sight check (can be improved with raycasting)
     */
    private static boolean hasLineOfSight(ServerPlayerEntity bot, Entity target) {
        // Basic check: target is roughly at the same Y level or visible
        double yDiff = Math.abs(bot.getY() - target.getY());
        return yDiff < 5.0; // Simplified check
    }

    /**
     * Calculate lead compensation for moving targets
     */
    public static Vec3d calculateLeadPosition(Entity target, double projectileSpeed) {
        if (!(target instanceof LivingEntity)) {
            return target.getPos();
        }

        Vec3d targetPos = target.getPos();
        Vec3d targetVelocity = target.getVelocity();

        // If target is not moving much, no need to lead
        if (targetVelocity.length() < 0.1) {
            return targetPos;
        }

        // Calculate time for projectile to reach target (simplified)
        // Actual calculation would need to solve quadratic equation
        double distance = targetPos.distanceTo(targetPos);
        double timeToImpact = distance / projectileSpeed;

        // Lead the target based on its velocity
        Vec3d leadOffset = targetVelocity.multiply(timeToImpact * 0.8); // 0.8 factor for tuning

        return targetPos.add(leadOffset);
    }

    /**
     * Calculate the optimal pitch and yaw to aim at a target with GRAVITY COMPENSATION
     * Uses ballistic trajectory calculation to account for arrow drop over distance
     *
     * Physics:
     * - Arrow velocity: 3.0 blocks/tick (fully charged bow)
     * - Gravity: 0.05 blocks/tick² (Minecraft gravity for arrows)
     * - Drag: 0.01 (slight slowdown per tick)
     *
     * Formula for projectile motion with drag:
     * Pitch angle = atan((v² ± sqrt(v⁴ - g(gx² + 2yv²))) / (gx))
     * Where: v = velocity, g = gravity, x = horizontal distance, y = vertical distance
     */
    public static float[] calculateAimAngles(ServerPlayerEntity bot, Vec3d targetPos) {
        Vec3d botPos = bot.getEyePos();
        Vec3d toTarget = targetPos.subtract(botPos);

        // Calculate yaw (horizontal angle) - same as before
        float yaw = (float) Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0f;

        // Calculate horizontal distance and vertical offset
        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double verticalOffset = toTarget.y; // Positive if target is above, negative if below

        // Minecraft arrow physics constants (tuned for accuracy)
        // Arrow initial velocity when fully charged bow is released
        double arrowVelocity = 3.0; // blocks/tick (fully charged bow = 60 blocks/sec)
        // Actual Minecraft gravity for arrows (from EntityArrow.java)
        double gravity = 0.05; // blocks/tick² (standard projectile gravity)
        // Air drag coefficient for arrows
        double drag = 0.01; // velocity multiplier per tick (0.99 drag factor)

        // Calculate pitch with gravity compensation using ballistic trajectory
        float pitch = calculateBallisticPitch(horizontalDistance, verticalOffset, arrowVelocity, gravity, drag);

        LOGGER.debug("Ballistic aim: distance={}m, height={}m, pitch={}°",
            String.format("%.1f", horizontalDistance),
            String.format("%.1f", verticalOffset),
            String.format("%.1f", pitch));

        return new float[]{yaw, pitch};
    }

    /**
     * Calculate the pitch angle needed to hit a target using ballistic trajectory
     *
     * This solves the projectile motion equation:
     * y = x*tan(θ) - (g*x²)/(2*v²*cos²(θ))
     *
     * Simplified to quadratic form and solved for the optimal launch angle
     */
    private static float calculateBallisticPitch(double horizontalDistance, double verticalOffset,
                                                  double velocity, double gravity, double drag) {
        // For very close targets (< 3 blocks), use simple direct aim
        if (horizontalDistance < 3.0) {
            return (float) -Math.toDegrees(Math.atan2(verticalOffset, horizontalDistance));
        }

        // Calculate flight time estimate to account for drag
        // Use iterative approximation: t ≈ distance / average_velocity
        double flightTime = horizontalDistance / (velocity * 0.95); // Assume 5% drag loss on average

        // Calculate drag-adjusted effective velocity
        // For longer distances, velocity degrades more due to air resistance
        double dragFactor = Math.exp(-drag * flightTime); // Exponential decay model
        double effectiveVelocity = velocity * dragFactor;

        double v2 = effectiveVelocity * effectiveVelocity;
        double v4 = v2 * v2;

        // Add extra compensation for long distances (20+ blocks)
        // Minecraft arrows drop more than physics would predict at long range
        double extraGravityCompensation = 1.0;
        if (horizontalDistance > 20.0) {
            // Add 5% extra compensation per 10 blocks beyond 20
            extraGravityCompensation = 1.0 + ((horizontalDistance - 20.0) / 10.0) * 0.05;
        }
        double adjustedGravity = gravity * extraGravityCompensation;

        // Discriminant of the quadratic equation
        // discriminant = v⁴ - g(g*x² + 2*y*v²)
        double discriminant = v4 - adjustedGravity * (adjustedGravity * horizontalDistance * horizontalDistance + 2 * verticalOffset * v2);

        // If discriminant is negative, target is out of range - aim at maximum angle
        if (discriminant < 0) {
            LOGGER.warn("Target out of range at {}m - using max trajectory angle",
                String.format("%.1f", horizontalDistance));
            // Use 45° for maximum range (slightly higher for long shots)
            return horizontalDistance > 30 ? 42.0f : 40.0f;
        }

        // Two solutions exist (high arc and low arc) - we prefer the LOW arc for faster travel time
        // θ = atan((v² - sqrt(discriminant)) / (g*x))  <- LOW trajectory (faster)
        // θ = atan((v² + sqrt(discriminant)) / (g*x))  <- HIGH trajectory (slower)

        double sqrtDiscriminant = Math.sqrt(discriminant);

        // Low trajectory angle (preferred for combat - faster arrow travel)
        double angleLow = Math.atan((v2 - sqrtDiscriminant) / (adjustedGravity * horizontalDistance));

        // High trajectory angle (backup if low angle is impossible)
        double angleHigh = Math.atan((v2 + sqrtDiscriminant) / (adjustedGravity * horizontalDistance));

        // Choose low angle if reasonable (between -15° and 55°), otherwise use high angle
        double chosenAngle = angleLow;
        if (angleLow < Math.toRadians(-15) || angleLow > Math.toRadians(55)) {
            chosenAngle = angleHigh;
            LOGGER.debug("Using high trajectory arc (low arc out of bounds)");
        }

        // For long-range shots (>25 blocks), add a small empirical correction
        // Based on observed Minecraft arrow physics behavior
        if (horizontalDistance > 25.0) {
            // Add 1-2 degrees for every 10 blocks beyond 25
            double longRangeCorrection = ((horizontalDistance - 25.0) / 10.0) * Math.toRadians(1.5);
            chosenAngle += longRangeCorrection;
            LOGGER.debug("Applied long-range correction: +{}°", String.format("%.1f", Math.toDegrees(longRangeCorrection)));
        }

        // Convert to degrees (negative because Minecraft pitch is inverted)
        float pitchDegrees = (float) -Math.toDegrees(chosenAngle);

        // Clamp pitch to reasonable range (-90° to 90°)
        pitchDegrees = Math.max(-89.9f, Math.min(89.9f, pitchDegrees));

        LOGGER.debug("Final pitch: {}° (distance: {}m, drag factor: {}, gravity comp: {})",
            String.format("%.2f", pitchDegrees),
            String.format("%.1f", horizontalDistance),
            String.format("%.3f", dragFactor),
            String.format("%.2f", extraGravityCompensation));

        return pitchDegrees;
    }

    /**
     * Check if the target is moving fast (requires leading the shot)
     */
    public static boolean isTargetMovingFast(Entity target) {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        Vec3d velocity = target.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        // Consider targets moving faster than 0.2 blocks/tick as fast-moving
        return speed > 0.2;
    }

    /**
     * Get the hotbar slot with a bow or crossbow
     */
    public static int getBowOrCrossbowSlot(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("bow") || itemName.contains("crossbow")) {
                return i + 1; // Hotbar slots are 1-indexed in commands
            }
        }
        return -1;
    }

    /**
     * Find firework rocket in entire inventory (hotbar + main inventory + hands)
     * Returns slot index (0-35) or -1 if not found, -100 if in mainhand, -101 if in offhand
     */
    private static int findFireworkInInventory(ServerPlayerEntity bot) {
        // Check mainhand first (currently equipped)
        ItemStack mainHand = bot.getMainHandStack();
        if (mainHand.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
            return -100; // Special code for mainhand
        }

        // Check offhand
        ItemStack offHand = bot.getOffHandStack();
        if (offHand.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
            return -101; // Special code for offhand
        }

        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
                return i;
            }
        }

        // Check main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem().getName().getString().toLowerCase().contains("firework rocket")) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Find crossbow in entire inventory (hotbar + main inventory + hands)
     * Returns slot index (0-35) or -1 if not found, -100 if in mainhand, -101 if in offhand
     */
    private static int findCrossbowInInventory(ServerPlayerEntity bot) {
        // Check mainhand first (currently equipped)
        ItemStack mainHand = bot.getMainHandStack();
        if (mainHand.getItem().getName().getString().toLowerCase().contains("crossbow")) {
            return -100; // Special code for mainhand
        }

        // Check offhand
        ItemStack offHand = bot.getOffHandStack();
        if (offHand.getItem().getName().getString().toLowerCase().contains("crossbow")) {
            return -101; // Special code for offhand
        }

        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("crossbow")) {
                return i;
            }
        }

        // Check main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("crossbow")) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Find an empty hotbar slot, or -1 if hotbar is full
     */
    private static int findEmptyHotbarSlot(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Prepare the bot for shooting with a crossbow
     * Moves firework rocket to offhand and crossbow to mainhand
     * Returns the ammo type that will be used: "firework", "arrow", or null
     */
    public static String prepareCrossbowAmmo(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server, net.minecraft.server.command.ServerCommandSource botSource) {
        String botName = bot.getName().getString();

        // Check if setup is already complete
        if (isFireworkInOffhand(bot) && hasCrossbow(bot)) {
            LOGGER.info("Crossbow and firework already equipped - ready to fire");
            return "firework";
        }

        // Step 1: Find firework rocket in inventory
        int fireworkSlot = findFireworkInInventory(bot);

        if (fireworkSlot != -1) {
            LOGGER.info("Found firework rocket at slot {}", fireworkSlot);

            // Handle firework already in offhand
            if (fireworkSlot == -101) {
                LOGGER.info("Firework already in offhand, skipping to crossbow setup");
                // Skip to crossbow setup
            }
            // Handle firework in mainhand
            else if (fireworkSlot == -100) {
                LOGGER.info("Firework in mainhand, swapping to offhand");
                // Just swap to offhand (this will swap whatever is in offhand to mainhand)
                server.getCommandManager().executeWithPrefix(botSource,
                    "/player " + botName + " swapHands");

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while swapping firework to offhand");
                }

                // After swap, check if crossbow is now in mainhand (it could have been in offhand)
                ItemStack mainHand = bot.getMainHandStack();
                if (mainHand.getItem().getName().getString().toLowerCase().contains("crossbow")) {
                    LOGGER.info("Crossbow was in offhand, now in mainhand after swap - setup complete!");
                    return "firework";
                }
            }
            // Handle firework in inventory/hotbar
            else {
                // Step 2: If firework is in main inventory (not hotbar), move it to hotbar
                if (fireworkSlot >= 9) {
                    int emptyHotbarSlot = findEmptyHotbarSlot(bot);
                    if (emptyHotbarSlot == -1) {
                        LOGGER.warn("Hotbar is full, using slot 8 for firework");
                        emptyHotbarSlot = 8;
                    }

                    LOGGER.info("Moving firework from inventory slot {} to hotbar slot {}", fireworkSlot, emptyHotbarSlot);

                    server.getCommandManager().executeWithPrefix(botSource,
                        "/item replace entity " + botName + " hotbar." + emptyHotbarSlot + " from entity " + botName + " inventory." + (fireworkSlot - 9));

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted while moving firework to hotbar");
                    }

                    fireworkSlot = emptyHotbarSlot; // Update to new hotbar location
                }

                // Step 3: Equip firework rocket in mainhand
                LOGGER.info("Equipping firework rocket from hotbar slot {}", fireworkSlot);
                server.getCommandManager().executeWithPrefix(botSource,
                    "/player " + botName + " hotbar " + (fireworkSlot + 1));

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while equipping firework");
                }

                // Step 4: Swap firework to offhand
                LOGGER.info("Swapping firework to offhand");
                server.getCommandManager().executeWithPrefix(botSource,
                    "/player " + botName + " swapHands");

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while swapping firework to offhand");
                }
            }

            // Step 5: Find and equip crossbow
            int crossbowSlot = findCrossbowInInventory(bot);
            if (crossbowSlot == -1) {
                LOGGER.error("Crossbow not found in inventory!");
                return null;
            }

            LOGGER.info("Found crossbow at slot {}", crossbowSlot);

            // Handle crossbow already in mainhand
            if (crossbowSlot == -100) {
                LOGGER.info("Crossbow already in mainhand");
                // Already equipped, we're done!
            }
            // Handle crossbow in offhand
            else if (crossbowSlot == -101) {
                LOGGER.info("Crossbow in offhand, swapping to mainhand");
                server.getCommandManager().executeWithPrefix(botSource,
                    "/player " + botName + " swapHands");

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while swapping crossbow to mainhand");
                }
            }
            // Handle crossbow in inventory/hotbar
            else {
                // Step 6: If crossbow is in main inventory, move it to hotbar
                if (crossbowSlot >= 9) {
                    int emptyHotbarSlot = findEmptyHotbarSlot(bot);
                    if (emptyHotbarSlot == -1) {
                        LOGGER.warn("Hotbar is full, using slot 7 for crossbow");
                        emptyHotbarSlot = 7;
                    }

                    LOGGER.info("Moving crossbow from inventory slot {} to hotbar slot {}", crossbowSlot, emptyHotbarSlot);

                    server.getCommandManager().executeWithPrefix(botSource,
                        "/item replace entity " + botName + " hotbar." + emptyHotbarSlot + " from entity " + botName + " inventory." + (crossbowSlot - 9));

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted while moving crossbow to hotbar");
                    }

                    crossbowSlot = emptyHotbarSlot;
                }

                // Step 7: Equip crossbow in mainhand
                LOGGER.info("Equipping crossbow from hotbar slot {}", crossbowSlot);
                server.getCommandManager().executeWithPrefix(botSource,
                    "/player " + botName + " hotbar " + (crossbowSlot + 1));

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while equipping crossbow");
                }
            }

            LOGGER.info("Setup complete: Crossbow in mainhand, firework in offhand");
            return "firework";
        }

        // No fireworks available, check for arrows
        if (hasArrows(bot)) {
            LOGGER.info("No firework rockets available, using arrows with crossbow");

            // Just make sure crossbow is equipped
            int crossbowSlot = findCrossbowInInventory(bot);
            if (crossbowSlot != -1) {
                if (crossbowSlot == -100) {
                    LOGGER.info("Crossbow already in mainhand, ready to fire arrows");
                    // Already equipped
                } else if (crossbowSlot == -101) {
                    LOGGER.info("Crossbow in offhand, swapping to mainhand");
                    server.getCommandManager().executeWithPrefix(botSource,
                        "/player " + botName + " swapHands");

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted");
                    }
                } else if (crossbowSlot >= 9) {
                    // Move to hotbar first
                    int emptyHotbarSlot = findEmptyHotbarSlot(bot);
                    if (emptyHotbarSlot == -1) emptyHotbarSlot = 7;

                    server.getCommandManager().executeWithPrefix(botSource,
                        "/item replace entity " + botName + " hotbar." + emptyHotbarSlot + " from entity " + botName + " inventory." + (crossbowSlot - 9));

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted");
                    }

                    crossbowSlot = emptyHotbarSlot;

                    server.getCommandManager().executeWithPrefix(botSource,
                        "/player " + botName + " hotbar " + (crossbowSlot + 1));

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted");
                    }
                } else {
                    // In hotbar, just equip
                    server.getCommandManager().executeWithPrefix(botSource,
                        "/player " + botName + " hotbar " + (crossbowSlot + 1));

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        LOGGER.warn("Interrupted");
                    }
                }
            }

            return "arrow";
        }

        LOGGER.warn("No ammo available for crossbow!");
        return null;
    }

    /**
     * Find bow in entire inventory (hotbar + main inventory + hands)
     * Returns slot index (0-35) or -1 if not found, -100 if in mainhand, -101 if in offhand
     */
    private static int findBowInInventory(ServerPlayerEntity bot) {
        // Check mainhand first (currently equipped)
        ItemStack mainHand = bot.getMainHandStack();
        String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
        if (mainHandName.contains("bow") && !mainHandName.contains("crossbow")) {
            return -100; // Special code for mainhand
        }

        // Check offhand
        ItemStack offHand = bot.getOffHandStack();
        String offHandName = offHand.getItem().getName().getString().toLowerCase();
        if (offHandName.contains("bow") && !offHandName.contains("crossbow")) {
            return -101; // Special code for offhand
        }

        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("bow") && !itemName.contains("crossbow")) {
                return i;
            }
        }

        // Check main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("bow") && !itemName.contains("crossbow")) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Prepare the bot for shooting with a bow
     * Ensures bow is in mainhand and arrows are available
     * Returns the ammo type that will be used: "arrow" or null
     */
    public static String prepareBowAmmo(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server, net.minecraft.server.command.ServerCommandSource botSource) {
        String botName = bot.getName().getString();

        // Check if we have arrows
        if (!hasArrows(bot)) {
            LOGGER.warn("No arrows available for bow!");
            return null;
        }

        // Check if bow is already in mainhand
        if (hasBow(bot)) {
            ItemStack mainHand = bot.getMainHandStack();
            String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
            if (mainHandName.contains("bow") && !mainHandName.contains("crossbow")) {
                LOGGER.info("Bow already equipped in mainhand - ready to fire");
                return "arrow";
            }
        }

        // Find bow in inventory
        int bowSlot = findBowInInventory(bot);
        if (bowSlot == -1) {
            LOGGER.error("Bow not found in inventory!");
            return null;
        }

        LOGGER.info("Found bow at slot {}", bowSlot);

        // Handle bow already in mainhand
        if (bowSlot == -100) {
            LOGGER.info("Bow already in mainhand");
            return "arrow";
        }
        // Handle bow in offhand
        else if (bowSlot == -101) {
            LOGGER.info("Bow in offhand, swapping to mainhand");
            server.getCommandManager().executeWithPrefix(botSource,
                "/player " + botName + " swapHands");

            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while swapping bow to mainhand");
            }
        }
        // Handle bow in inventory/hotbar
        else {
            // If bow is in main inventory, move it to hotbar
            if (bowSlot >= 9) {
                int emptyHotbarSlot = findEmptyHotbarSlot(bot);
                if (emptyHotbarSlot == -1) {
                    LOGGER.warn("Hotbar is full, using slot 7 for bow");
                    emptyHotbarSlot = 7;
                }

                LOGGER.info("Moving bow from inventory slot {} to hotbar slot {}", bowSlot, emptyHotbarSlot);

                server.getCommandManager().executeWithPrefix(botSource,
                    "/item replace entity " + botName + " hotbar." + emptyHotbarSlot + " from entity " + botName + " inventory." + (bowSlot - 9));

                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while moving bow to hotbar");
                }

                bowSlot = emptyHotbarSlot;
            }

            // Equip bow in mainhand
            LOGGER.info("Equipping bow from hotbar slot {}", bowSlot);
            server.getCommandManager().executeWithPrefix(botSource,
                "/player " + botName + " hotbar " + (bowSlot + 1));

            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while equipping bow");
            }
        }

        LOGGER.info("Setup complete: Bow in mainhand, arrows available");
        return "arrow";
    }

    /**
     * Equip bow or crossbow in main hand directly (without carpet commands)
     */
    public static void equipBowOrCrossbowInMainHand(ServerPlayerEntity bot) {
        // Check if already equipped
        ItemStack mainHand = bot.getMainHandStack();
        String mainHandName = mainHand.getItem().getName().getString().toLowerCase();
        if (mainHandName.contains("bow") || mainHandName.contains("crossbow")) {
            LOGGER.info("Bow/Crossbow already equipped in mainhand");
            return;
        }

        // Find weapon in hotbar
        int weaponSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            String itemName = stack.getItem().getName().getString().toLowerCase();
            if (itemName.contains("bow") || itemName.contains("crossbow")) {
                weaponSlot = i;
                break;
            }
        }

        if (weaponSlot == -1) {
            // Check rest of inventory and move to hotbar
            for (int i = 9; i < 36; i++) {
                ItemStack stack = bot.getInventory().getStack(i);
                String itemName = stack.getItem().getName().getString().toLowerCase();
                if (itemName.contains("bow") || itemName.contains("crossbow")) {
                    // Find empty hotbar slot
                    int emptySlot = -1;
                    for (int h = 0; h < 9; h++) {
                        if (bot.getInventory().getStack(h).isEmpty()) {
                            emptySlot = h;
                            break;
                        }
                    }

                    if (emptySlot != -1) {
                        // Swap from inventory to hotbar
                        ItemStack weapon = stack.copy();
                        bot.getInventory().setStack(emptySlot, weapon);
                        bot.getInventory().setStack(i, ItemStack.EMPTY);
                        weaponSlot = emptySlot;
                        LOGGER.info("Moved weapon from inventory slot {} to hotbar slot {}", i, emptySlot);
                        break;
                    }
                }
            }
        }

        if (weaponSlot != -1) {
            // Select the hotbar slot
            bot.getInventory().selectedSlot = weaponSlot;
            LOGGER.info("Equipped weapon in mainhand from hotbar slot {}", weaponSlot);
        } else {
            LOGGER.warn("No bow or crossbow found in inventory!");
        }
    }
}

