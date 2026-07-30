package net.shasankp000.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Utility class for detecting incoming projectiles and calculating dodge/block strategies
 */
public class ProjectileDefenseUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("projectile-defense");

    // Detection range for incoming projectiles (increased for earlier detection)
    private static final double DETECTION_RANGE = 20.0; // Increased from 15.0

    // Threat thresholds (distance in blocks) - react earlier!
    private static final double IMMEDIATE_THREAT_DISTANCE = 8.0; // Increased from 5.0
    private static final double MEDIUM_THREAT_DISTANCE = 15.0;   // Increased from 10.0

    /**
     * Detected projectile with threat information
     */
    public static class IncomingProjectile {
        public final ProjectileEntity projectile;
        public final double distance;
        public final Vec3d velocity;
        public final boolean isHeadedTowardsBot;
        public final double threatLevel; // 0.0 to 100.0
        public final String projectileType;
        public final Entity owner; // Who shot this projectile

        public IncomingProjectile(ProjectileEntity projectile, ServerPlayerEntity bot) {
            this.projectile = projectile;
            this.distance = Math.sqrt(projectile.squaredDistanceTo(bot));
            this.velocity = projectile.getVelocity();
            this.projectileType = getProjectileType(projectile);
            this.owner = projectile.getOwner(); // Track who shot it

            // Calculate if projectile is headed towards the bot
            Vec3d projectilePos = projectile.getPos();
            Vec3d botPos = bot.getPos();
            Vec3d directionToBot = botPos.subtract(projectilePos).normalize();
            Vec3d projectileDirection = velocity.normalize();

            // Dot product to check if projectile is moving towards bot
            double dotProduct = projectileDirection.dotProduct(directionToBot);

            // Lower threshold to 0.3 to catch more projectiles (was 0.7)
            this.isHeadedTowardsBot = dotProduct > 0.3; // Threshold for "headed towards"

            // Calculate threat level
            this.threatLevel = calculateThreatLevel();
        }

        private String getProjectileType(ProjectileEntity projectile) {
            String name = projectile.getType().getName().getString();
            if (projectile instanceof ArrowEntity) {
                return "arrow";
            }
            return name.toLowerCase();
        }

        private double calculateThreatLevel() {
            if (!isHeadedTowardsBot) {
                return 0.0; // No threat if not headed towards bot
            }

            // CRITICAL SAFETY CHECK: Projectile stuck in bot's body
            // Very close + very slow = stuck arrow, not a threat
            double speed = velocity.length();
            if (distance < 1.0 && speed < 0.5) {
                LOGGER.debug("⚠ Ignoring projectile stuck in/near bot (dist: {}, speed: {})",
                    String.format("%.2f", distance), String.format("%.3f", speed));
                return 0.0; // No threat - it's already stuck
            }

            double threat = 0.0;

            // Distance-based threat (heavily weighted for close projectiles)
            if (distance <= IMMEDIATE_THREAT_DISTANCE) {
                threat += 80.0; // Very high threat for close projectiles
            } else if (distance <= MEDIUM_THREAT_DISTANCE) {
                threat += 50.0; // Moderate threat
            } else {
                threat += 25.0; // Still significant for far projectiles
            }

            // Velocity-based threat (faster = more dangerous)
            threat += Math.min(speed * 8.0, 40.0); // Increased multiplier for fast projectiles

            return Math.min(threat, 100.0);
        }
    }

    /**
     * Detect all projectiles near the bot within detection range
     */
    public static List<IncomingProjectile> detectIncomingProjectiles(ServerPlayerEntity bot) {
        ServerWorld world = (ServerWorld) bot.getWorld();
        Box searchBox = bot.getBoundingBox().expand(DETECTION_RANGE);

        // Find all projectile entities
        List<ProjectileEntity> projectiles = world.getEntitiesByClass(
            ProjectileEntity.class,
            searchBox,
            projectile -> {
                // Exclude projectiles fired by the bot itself
                Entity owner = projectile.getOwner();
                if (owner != null && owner.getUuid().equals(bot.getUuid())) {
                    return false;
                }

                // CRITICAL FIX: Exclude arrows stuck in ground/blocks
                Vec3d velocity = projectile.getVelocity();
                double speed = velocity.length();
                int age = projectile.age;

                // Debug: Log arrow properties
                if (projectile instanceof ArrowEntity) {
                    LOGGER.debug("Arrow detected - Speed: {}, Age: {}, OnGround: {}, AttachedTo: {}",
                        String.format("%.3f", speed), age, projectile.isOnGround(),
                        projectile.getVehicle() != null ? projectile.getVehicle().getName().getString() : "none");
                }

                // Filter 1: CRITICAL - Exclude arrows stuck ON THE BOT (attached to bot's body)
                // Check if projectile is riding/attached to any entity (especially the bot)
                if (projectile.getVehicle() != null) {
                    Entity vehicle = projectile.getVehicle();
                    // If arrow is attached to the bot itself, ignore it
                    if (vehicle.getUuid().equals(bot.getUuid())) {
                        return false; // Arrow stuck on bot's body
                    }
                }

                // Filter 1b: Check if arrow is stuck IN the bot's body
                // Arrows stuck in entities are within bounding box with near-zero velocity
                if (projectile instanceof net.minecraft.entity.projectile.PersistentProjectileEntity persistentProjectile) {
                    double distanceToBot = Math.sqrt(projectile.squaredDistanceTo(bot));

                    // If arrow is VERY close to bot (within 0.8 blocks) with low velocity, it's stuck in bot
                    if (distanceToBot < 0.8 && speed < 0.5) {
                        // Additionally check if it's within bot's bounding box
                        if (bot.getBoundingBox().expand(0.5).contains(projectile.getPos())) {
                            LOGGER.debug("Ignoring arrow stuck in bot's body (distance: {}, speed: {})",
                                String.format("%.2f", distanceToBot), String.format("%.3f", speed));
                            return false;
                        }
                    }
                }

                // Filter 2: If projectile is on ground (touching a block), it's stuck
                if (projectile.isOnGround()) {
                    return false;
                }

                // Filter 3: Zero velocity = stuck/stationary
                if (speed < 0.01) { // Much stricter threshold
                    return false;
                }

                // Filter 4: Old projectiles with low speed are definitely stuck
                // Fresh arrows (age < 10) can be legitimately slow if shot from close range
                if (age > 10 && speed < 1.0) {
                    return false;
                }

                return true;
            }
        );

        // Convert to IncomingProjectile with threat analysis (no verbose logging to maximize speed)
        List<IncomingProjectile> incomingProjectiles = projectiles.stream()
            .map(p -> new IncomingProjectile(p, bot))
            .filter(ip -> ip.isHeadedTowardsBot)
            .collect(Collectors.toList());


        return incomingProjectiles;
    }

    /**
     * Get the most threatening projectile
     */
    public static IncomingProjectile getMostThreateningProjectile(List<IncomingProjectile> projectiles) {
        return projectiles.stream()
            .max((p1, p2) -> Double.compare(p1.threatLevel, p2.threatLevel))
            .orElse(null);
    }

    /**
     * Calculate the best dodge direction to avoid a projectile
     * Returns a direction vector (normalized) or null if no dodge needed
     */
    public static Vec3d calculateDodgeDirection(ServerPlayerEntity bot, IncomingProjectile projectile) {
        if (projectile.distance > IMMEDIATE_THREAT_DISTANCE) {
            return null; // No immediate dodge needed
        }

        Vec3d botPos = bot.getPos();
        Vec3d projectilePos = projectile.projectile.getPos();
        Vec3d projectileVelocity = projectile.velocity;

        // Calculate perpendicular direction to projectile velocity
        // This gives us the best direction to sidestep
        Vec3d perpendicularDir = new Vec3d(
            -projectileVelocity.z, // Swap and negate for perpendicular
            0, // Don't dodge vertically
            projectileVelocity.x
        ).normalize();

        // Check which side has more clearance
        Vec3d leftDodge = perpendicularDir;
        Vec3d rightDodge = perpendicularDir.multiply(-1);

        // Simple clearance check - prefer the direction away from projectile source
        Vec3d directionFromProjectile = botPos.subtract(projectilePos).normalize();

        // Choose the dodge direction that's more aligned with moving away from projectile
        double leftAlignment = leftDodge.dotProduct(directionFromProjectile);
        double rightAlignment = rightDodge.dotProduct(directionFromProjectile);

        Vec3d dodgeDirection = leftAlignment > rightAlignment ? leftDodge : rightDodge;

        LOGGER.info("Calculated dodge direction: {} for {} at distance {}",
            dodgeDirection, projectile.projectileType, projectile.distance);

        return dodgeDirection;
    }

    // Cache shield status to prevent excessive checks
    private static final Map<UUID, ShieldCache> shieldCache = new ConcurrentHashMap<>();

    private static class ShieldCache {
        boolean hasShield;
        long lastChecked;
        static final long CACHE_DURATION = 500; // 500ms cache

        ShieldCache(boolean hasShield) {
            this.hasShield = hasShield;
            this.lastChecked = System.currentTimeMillis();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - lastChecked) < CACHE_DURATION;
        }
    }

    /**
     * Check if bot has a shield in inventory (with caching to prevent lag)
     */
    public static boolean hasShield(ServerPlayerEntity bot) {
        UUID botId = bot.getUuid();

        // Check cache first
        ShieldCache cached = shieldCache.get(botId);
        if (cached != null && cached.isValid()) {
            return cached.hasShield;
        }

        // Check if already equipped in offhand (fast path)
        if (hasShieldEquipped(bot)) {
            shieldCache.put(botId, new ShieldCache(true));
            return true;
        }

        // Check ALL inventory slots (0-40 covers everything)
        int inventorySize = bot.getInventory().size();

        for (int i = 0; i < inventorySize; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && isShield(stack)) {
                shieldCache.put(botId, new ShieldCache(true));
                return true;
            }
        }

        shieldCache.put(botId, new ShieldCache(false));
        return false;
    }

    /**
     * Check if bot has a shield equipped (offhand only)
     */
    public static boolean hasShieldEquipped(ServerPlayerEntity bot) {
        ItemStack offHand = bot.getOffHandStack();
        return isShield(offHand);
    }

    /**
     * Check if an item stack is a shield
     */
    private static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Check by item ID (most reliable)
        String itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
        if (itemId.contains("shield")) {
            return true;
        }

        // Fallback: Check by translation key
        String translationKey = stack.getItem().getTranslationKey();
        if (translationKey != null && translationKey.toLowerCase().contains("shield")) {
            return true;
        }

        // Fallback: Check by display name
        String displayName = stack.getItem().getName().getString().toLowerCase();
        if (displayName.contains("shield")) {
            return true;
        }

        return false;
    }

    /**
     * Find shield slot in inventory (-1 if not found)
     * Checks entire inventory including hotbar, main inventory, and armor slots
     */
    public static int findShieldInInventory(ServerPlayerEntity bot) {
        LOGGER.debug("🔍 Searching for shield in all inventory slots...");

        int inventorySize = bot.getInventory().size();

        // Check ALL slots
        for (int i = 0; i < inventorySize; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (isShield(stack)) {
                String slotType = i < 9 ? "HOTBAR" : (i < 36 ? "MAIN INVENTORY" : "ARMOR/OFFHAND");
                LOGGER.debug("✅ Found shield at slot {} ({})", i, slotType);
                return i;
            }
        }

        LOGGER.debug("❌ No shield found in any slot!");
        return -1;
    }

    /**
     * Equip shield from inventory to offhand
     * Strategy: Always move to hotbar first, then swap to offhand
     * Returns true if successful, false otherwise
     */
    public static boolean equipShieldToOffhand(ServerPlayerEntity bot) {
        LOGGER.info("🛡 Starting shield equip process...");

        // Check if shield is already equipped
        if (hasShieldEquipped(bot)) {
            LOGGER.info("✓ Shield already equipped in offhand");
            return true;
        }

        int shieldSlot = findShieldInInventory(bot);
        if (shieldSlot == -1) {
            LOGGER.warn("❌ No shield found in inventory - cannot equip");
            return false;
        }

        try {
            int targetHotbarSlot = -1;

            // If shield is already in hotbar (slots 0-8), use that slot directly
            if (shieldSlot < 9) {
                LOGGER.info("Shield is already in hotbar at slot {}", shieldSlot);
                targetHotbarSlot = shieldSlot;
            } else {
                // Shield is NOT in hotbar - need to move it there first
                LOGGER.info("Shield is in slot {} - moving to hotbar first...", shieldSlot);

                // Find an empty hotbar slot, or use slot 8 as fallback
                targetHotbarSlot = 8;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = bot.getInventory().getStack(i);
                    if (stack.isEmpty()) {
                        targetHotbarSlot = i;
                        LOGGER.info("Found empty hotbar slot: {}", i);
                        break;
                    }
                }

                LOGGER.info("Moving shield from slot {} to hotbar slot {}", shieldSlot, targetHotbarSlot);

                // Swap shield to hotbar
                ItemStack shieldStack = bot.getInventory().getStack(shieldSlot);
                ItemStack hotbarStack = bot.getInventory().getStack(targetHotbarSlot);
                bot.getInventory().setStack(shieldSlot, hotbarStack);
                bot.getInventory().setStack(targetHotbarSlot, shieldStack);

                LOGGER.info("✓ Shield moved to hotbar slot {}", targetHotbarSlot);
            }

            // Now equip from hotbar to main hand
            LOGGER.info("Selecting hotbar slot {} (shield)", targetHotbarSlot);
            bot.getInventory().selectedSlot = targetHotbarSlot;

            // Verify shield is in main hand
            ItemStack mainHand = bot.getMainHandStack();
            if (!isShield(mainHand)) {
                LOGGER.error("❌ Shield not in main hand after selection! Got: {}",
                    net.minecraft.registry.Registries.ITEM.getId(mainHand.getItem()));
                return false;
            }

            LOGGER.info("✓ Shield is now in main hand");

            // Swap main hand (shield) to offhand
            LOGGER.info("Swapping shield from main hand to offhand...");
            ItemStack offHand = bot.getOffHandStack();
            bot.getInventory().offHand.set(0, mainHand); // Shield goes to offhand
            bot.getInventory().setStack(targetHotbarSlot, offHand); // Previous offhand item goes to hotbar


            // Verify shield is now in offhand
            if (hasShieldEquipped(bot)) {
                LOGGER.info("✅ SUCCESS! Shield equipped to offhand");
                return true;
            } else {
                LOGGER.error("❌ Shield swap failed - not in offhand after swap!");
                return false;
            }

        } catch (Exception e) {
            LOGGER.error("❌ Failed to equip shield: {}", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Determine if bot should block or dodge based on shield availability and threat level
     */
    public static String determineDefenseStrategy(ServerPlayerEntity bot, IncomingProjectile projectile) {
        LOGGER.info("🛡 Determining defense strategy - Distance: {}, Threat: {}",
            String.format("%.1f", projectile.distance), String.format("%.1f", projectile.threatLevel));

        boolean hasShield = hasShield(bot);
        boolean isImmediate = projectile.distance <= IMMEDIATE_THREAT_DISTANCE;

        LOGGER.info("  - Has shield: {}", hasShield);
        LOGGER.info("  - Is immediate threat (≤{}m): {}", IMMEDIATE_THREAT_DISTANCE, isImmediate);

        // If shield is available and threat is immediate, block
        if (hasShield && isImmediate) {
            LOGGER.info("  ⚡ Strategy: BLOCK (shield available + close range)");
            return "block";
        }

        // If no shield or medium distance, try to dodge
        if (projectile.distance <= MEDIUM_THREAT_DISTANCE) {
            LOGGER.info("  ⚡ Strategy: DODGE (no shield or medium range)");
            return "dodge";
        }

        // Far away, just track
        LOGGER.info("  ⚡ Strategy: TRACK (far away)");
        return "track";
    }

    /**
     * Calculate the total projectile threat risk for risk assessment
     */
    public static double calculateProjectileThreatRisk(List<IncomingProjectile> projectiles) {
        if (projectiles.isEmpty()) {
            return 0.0;
        }

        // Sum up all threat levels
        double totalThreat = projectiles.stream()
            .mapToDouble(p -> p.threatLevel)
            .sum();

        // Cap at 100.0
        return Math.min(totalThreat, 100.0);
    }

    /**
     * Check obstacle clearance in front of bot for pathfinding decision
     * Returns distance in blocks until first obstacle (0-30 blocks)
     * Used to decide: clear path = direct sprint, obstacles = use pathfinder
     */
    public static double checkObstacleClearance(ServerPlayerEntity bot, Vec3d direction, double maxDistance) {
        ServerWorld world = (ServerWorld) bot.getWorld();
        Vec3d startPos = bot.getPos().add(0, bot.getStandingEyeHeight(), 0); // From bot's eyes

        // Raytrace in the given direction
        for (double dist = 1.0; dist <= maxDistance; dist += 0.5) {
            Vec3d checkPos = startPos.add(direction.multiply(dist));
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos(
                (int) Math.floor(checkPos.x),
                (int) Math.floor(checkPos.y),
                (int) Math.floor(checkPos.z)
            );

            // Check if block is solid (obstacle)
            net.minecraft.block.BlockState state = world.getBlockState(blockPos);
            if (!state.isAir() && state.isSolidBlock(world, blockPos)) {
                LOGGER.debug("Obstacle detected at {} blocks: {}", dist, state.getBlock().getName().getString());
                return dist; // Return distance to first obstacle
            }

            // Also check head clearance (one block up)
            net.minecraft.util.math.BlockPos headPos = blockPos.up();
            net.minecraft.block.BlockState headState = world.getBlockState(headPos);
            if (!headState.isAir() && headState.isSolidBlock(world, headPos)) {
                LOGGER.debug("Head clearance obstacle at {} blocks: {}", dist, headState.getBlock().getName().getString());
                return dist;
            }
        }

        // No obstacles found within range
        return maxDistance;
    }

    /**
     * Detect incoming projectiles with world/server safety checks
     * Prevents detection when world is unloading or server is stopping
     */
    public static List<IncomingProjectile> detectIncomingProjectilesSafe(ServerPlayerEntity bot) {
        // Safety checks before detection
        if (bot == null || !bot.isAlive()) {
            return List.of(); // Bot is dead
        }

        ServerWorld world = (ServerWorld) bot.getWorld();
        if (world == null) {
            return List.of(); // World is null
        }

        net.minecraft.server.MinecraftServer server = world.getServer();
        if (server == null || !server.isRunning()) {
            return List.of(); // Server stopped or stopping
        }

        // Safe to detect projectiles
        return detectIncomingProjectiles(bot);
    }
}

