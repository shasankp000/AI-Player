package net.shasankp000.Entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.Database.QTable;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.Commands.modCommandRegistry;
import net.shasankp000.Database.QTableStorage;
import net.shasankp000.GameAI.proximity.ProximityTracker;
import net.shasankp000.PlayerUtils.BlockDistanceLimitedSearch;
import net.shasankp000.PlayerUtils.blockDetectionUnit;
import net.shasankp000.PlayerUtils.ProjectileDefenseUtils;
import net.shasankp000.PlayerUtils.PredictiveThreatDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.PathFinding.NavigationService;
import net.shasankp000.PathFinding.SuspensionReason;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoFaceEntity {

    public static final Logger LOGGER = LoggerFactory.getLogger("AutoFaceEntity");
    private static final double BOUNDING_BOX_SIZE = 32.0; // Detection range in blocks (increased for better combat awareness)
    private static final int INTERVAL_SECONDS = 1; // Interval in seconds to check for nearby entities (for entity detection)
    private static final int PROJECTILE_CHECK_INTERVAL_MS = 50; // Check projectiles every 50ms (1 game tick) - fast enough for arrows
    private static final ExecutorService executor3 = Executors.newSingleThreadExecutor();
    public static boolean botBusy;
    private static boolean botExecutingTask;
    public static boolean hostileEntityInFront;
    public static boolean isHandlerTriggered;
    private static boolean isWorldTickListenerActive = true; // Flag to control execution
    private static QTable qTable;
    public static RLAgent rlAgent;
    public static List<Entity> hostileEntities;

    private static final Map<ServerPlayer, ScheduledExecutorService> botExecutors = new HashMap<>();
    private static ServerPlayer Bot = null;
    public static boolean isBotMoving = false;
    public static boolean isShooting = false; // Flag to pause autoface during shooting
    private static Entity shootingTarget = null; // The entity being shot at (priority target)
    private static long shootingStartTime = 0; // When shooting started

    // Projectile defense tracking
    public static boolean isDefendingFromProjectile = false; // Flag when bot is actively defending
    public static ProjectileDefenseUtils.IncomingProjectile currentThreat = null; // Current projectile being defended against
    public static String defenseMode = "none"; // "block", "dodge", or "none"
    private static boolean dodgeExecuted = false; // Flag to prevent multiple dodge commands for same threat

    // Predictive threat tracking (PHASE-BASED DETECTION)
    public static PredictiveThreatDetector.DrawingBowThreat predictedThreat = null; // Entity drawing bow
    public static Vec3 plannedEvasiveDirection = null; // Pre-calculated dodge direction
    public static boolean isWaitingForRelease = false; // Waiting for arrow to be fired
    public static long threatDetectionTime = 0; // When we first detected the threat

    // Persistent shield blocking state
    public static boolean isActivelyBlocking = false; // Is bot currently blocking with shield
    public static net.minecraft.world.entity.LivingEntity blockingAgainst = null; // Entity we're blocking against
    public static long blockingStartTime = 0; // When blocking started

    // Flag to prevent chat spam for "terminating all tasks" message
    private static boolean threatMessageSent = false; // Set to true when message sent, reset when out of danger

    public static void setBotExecutingTask(boolean value) {
        botExecutingTask = value;
    }

    public static boolean isBotExecutingTask() {
        return botExecutingTask;
    }

    public static void startAutoFace(ServerPlayer bot) {
        // Stop any existing executor for this bot
        LOGGER.info("========== STARTING AUTOFACE FOR BOT: {} ==========", bot.getName().getString());

        MinecraftServer server = bot.createCommandSourceStack().getServer();
        BotEventHandler.setActiveBot(server, bot);
        Bot = bot;

        stopAutoFace(bot);

        ScheduledExecutorService botExecutor = Executors.newSingleThreadScheduledExecutor();

        botExecutors.put(bot, botExecutor);

        // Load Q-table from storage
        try {
            qTable = QTableStorage.loadQTable();
            System.out.println("Loaded Q-table from storage.");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.err.println("No existing Q-table found. Starting fresh.");
            qTable = new QTable();
        }


        // RL agent hook
        rlAgent = null;

        if (modCommandRegistry.isTrainingMode) {

            rlAgent = new RLAgent(); // Initialize RL agent (use singleton or DI for reusability)

        }
        else {

            try {

                double epsilon = QTableStorage.loadEpsilon(BotEventHandler.qTableDir + "/epsilon.bin");

                rlAgent = new RLAgent(epsilon, qTable);

            }

            catch (Exception e) {

                System.err.println("No existing epsilon found. Starting fresh.");
                rlAgent = new RLAgent(1.0, qTable);

            }

        }


        RLAgent finalRlAgent = rlAgent;
        LOGGER.info("Scheduling AutoFace task to run every 33ms (30 FPS) for bot: {}", bot.getName().getString());
        botExecutor.scheduleAtFixedRate(() -> {
            // Run detection and facing logic

            if (server != null && server.isRunning() && bot.isAlive()) {

                // ===== PRIORITY: UPDATE EVASION STATUS =====
                // Check if bot should continue evading or stop (distance-based)
                updateEvasionStatus(bot, server);

                // If actively evading, skip most other logic
                if (isEvading) {
                    return; // Evasion takes priority
                }

                // ===== PRIORITY: UPDATE PERSISTENT SHIELD BLOCKING =====
                // This runs first to maintain shield block and face attacker if blocking is active
                updatePersistentBlocking(bot, server);

                // If actively blocking, skip all other logic - just maintain the block
                if (isActivelyBlocking) {
                    return; // Shield blocking takes absolute priority
                }

                // Detect all entities within the bounding box
                List<Entity> nearbyEntities = detectNearbyEntities(bot, BOUNDING_BOX_SIZE);

                // Filter hostile entities (mobs AND hostile players)
                // CRITICAL: Exclude ALL projectile entities to prevent bot from facing arrows/stuck projectiles!
                 hostileEntities = nearbyEntities.stream()
                        .filter(entity -> {
                            // Include hostile mobs (including SlimeEntity which is also a HostileEntity subclass)
                            if (entity instanceof Monster || entity instanceof Slime) {
                                return true;
                            }
                            // Include hostile players (tracked by retaliation system)
                            if (entity instanceof Player player &&
                                !player.getUUID().equals(bot.getUUID())) { // Don't target self
                                boolean isHostile = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
                                if (isHostile) {
                                    double distance = Math.sqrt(player.distanceToSqr(bot));
                                    LOGGER.info("⚔ Hostile player detected: {} at {}m",
                                        player.getName().getString(), String.format("%.1f", distance));
                                }
                                return isHostile;
                            }
                            return false;
                        })
                        .filter(entity -> !(entity instanceof net.minecraft.world.entity.projectile.Projectile))
                        .filter(entity -> !(entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow))
                        .filter(entity -> !(entity instanceof net.minecraft.world.entity.projectile.arrow.Arrow))
                        // Also exclude very close entities that might be stuck projectiles
                        .filter(entity -> {
                            double distanceToBot = Math.sqrt(entity.distanceToSqr(bot));
                            Vec3 entityVel = entity.getDeltaMovement();
                            double speed = entityVel.length();
                            // If entity is within 0.5 blocks AND has zero velocity, likely stuck projectile
                            return !(distanceToBot < 0.5 && speed < 0.01);
                        })
                        .toList();

                // ===== PREDICTIVE THREAT DETECTION REMOVED =====
                // All combat decisions now handled by RL agent in BotEventHandler
                // This prevents conflicts and lets the agent learn optimal strategies

                boolean hasSculkNearby = false;

                BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

                List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

                hasSculkNearby = nearbyBlocks.stream()
                        .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));


                // ===== PRIORITY: PROJECTILE DEFENSE =====
                // If defending from projectile, face it for blocking (highest priority)
                if (isDefendingFromProjectile && currentThreat != null) {
                    if ("block".equals(defenseMode)) {
                        // Face the projectile to block it with shield
                        FaceClosestEntity.faceProjectile(bot, currentThreat.projectile);
                        return; // Skip all other facing logic
                    } else if ("dodge".equals(defenseMode) && !dodgeExecuted) {
                        // Execute dodge ONCE per threat - immediately!
                        dodgeExecuted = true; // Mark as executed

                        // Calculate and face dodge direction
                        Vec3 dodgeDir = ProjectileDefenseUtils.calculateDodgeDirection(bot, currentThreat);
                        if (dodgeDir != null) {
                            // Face dodge direction IMMEDIATELY
                            double yaw = Math.toDegrees(Math.atan2(dodgeDir.z, dodgeDir.x)) - 90;
                            bot.setYRot((float) yaw);

                            LOGGER.info("⚡ DODGE! {} at {:.1f}m - Moving perpendicular!",
                                currentThreat.projectileType, currentThreat.distance);

                            // SPRINT for faster dodge (2x speed)
                            server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
                                "/player " + bot.getName().getString() + " sprint");

                            // Schedule stop after 400ms (sprint moves ~3 blocks in this time)
                            executor3.submit(() -> {
                                try {
                                    Thread.sleep(400); // 400ms sprint = ~3 block dodge
                                    server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
                                        "/player " + bot.getName().getString() + " stop");
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                        }
                    }
                }
                // ===== END PRIORITY: PROJECTILE DEFENSE =====

                if (!hostileEntities.isEmpty()) {
                    NavigationService.suspend(bot.getUUID(), SuspensionReason.THREAT);
                    botBusy = true;

                    // Report all hostile entities to debug manager
                    if (net.shasankp000.Overlay.ThreatDebugManager.isDebugEnabled()) {
                        net.shasankp000.Overlay.ThreatDebugManager.setBotPlayer(bot);
                        for (Entity hostileEntity : hostileEntities) {
                            double dist = Math.sqrt(hostileEntity.distanceToSqr(bot.position()));
                            double threat;

                            // Use player-specific threat calculation for hostile players
                            if (hostileEntity instanceof Player player) {
                                threat = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.getPlayerThreatLevel(bot, player);
                            } else {
                                // Calculate basic threat for mobs (simplified version)
                                threat = 25.0 / Math.max(dist, 1.0);
                            }

                            net.shasankp000.Overlay.ThreatDebugManager.updateThreat(
                                hostileEntity.getUUID(),
                                hostileEntity.getName().getString(),
                                threat,
                                dist,
                                hostileEntity instanceof Player ? "Hostile Player" : "Detected"
                            );
                        }
                    }

                    // If bot is shooting at a specific target, lock onto that target only
                    if (isShooting && isShootingTargetValid()) {
                        System.out.println("Bot is shooting at priority target: " + shootingTarget.getName().getString());
                        // Face only the shooting target, ignore other entities
                        FaceClosestEntity.faceClosestEntity(bot, List.of(shootingTarget));
                        return;
                    }

                    // If we were shooting but target is no longer valid, clear it
                    if (isShooting && !isShootingTargetValid()) {
                        System.out.println("Shooting target no longer valid, resuming normal autoface");
                        clearShootingTarget();
                    }

                    // Find the closest hostile entity
                    Entity closestHostile = hostileEntities.stream()
                            .min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.position())))
                            .orElseThrow(); // Use orElseThrow since empty case is already handled

                    double distanceToHostileEntity = Math.sqrt(closestHostile.distanceToSqr(bot.position()));

                    // Mark closest as current target in debug manager
                    if (net.shasankp000.Overlay.ThreatDebugManager.isDebugEnabled()) {
                        net.shasankp000.Overlay.ThreatDebugManager.setCurrentTarget(closestHostile.getUUID());
                        // Update status to "Targeting"
                        double threat = 25.0 / Math.max(distanceToHostileEntity, 1.0);
                        net.shasankp000.Overlay.ThreatDebugManager.updateThreat(
                            closestHostile.getUUID(),
                            closestHostile.getName().getString(),
                            threat,
                            distanceToHostileEntity,
                            "Targeting"
                        );
                    }

                    if ((NavigationService.isNavigating(bot.getUUID()) || isBotMoving) || blockDetectionUnit.getBlockDetectionStatus() || isBotExecutingTask()) {

                        System.out.println("Hostile mobs detected while bot is executing jobs!");


                        if (distanceToHostileEntity <= 32.0) {
                            isBotMoving = false;
                            setBotExecutingTask(false);

                            FaceClosestEntity.faceClosestEntity(bot, AutoFaceEntity.hostileEntities);



                            // Log details of the detected hostile entity
                            System.out.println("Closest hostile entity: " + closestHostile.getName().getString()
                                    + " at distance: " + distanceToHostileEntity);

                            botBusy = true; // Set the bot as busy if hostile entities are in range
                            hostileEntityInFront = true;

                            // Trigger the handler
                            if (isHandlerTriggered) {
                                System.out.println("isHandlerTriggered: " + isHandlerTriggered);
                                System.out.println("Handler already triggered. Skipping.");
                            } else {
                                System.out.println("Triggering handler for hostile entity.");
                                isHandlerTriggered = true;

                                BotEventHandler eventHandler = new BotEventHandler(server, bot);

                                if (modCommandRegistry.isTrainingMode) {

                                    try {
                                        eventHandler.detectAndReact(finalRlAgent, distanceToHostileEntity, qTable);
                                        isHandlerTriggered = false; // Reset after handling
                                    } catch (IOException e) {
                                        System.out.println("Exception occurred in startAutoFace: " + e.getMessage());
                                        isHandlerTriggered = false; // Reset even on exception
                                        throw new RuntimeException(e);

                                    }
                                }
                                else {

                                    eventHandler.detectAndReactPlayMode(finalRlAgent, qTable);
                                    isHandlerTriggered = false; // Reset after handling

                                }

                            }
                        }

                    }
                    else {
                        // this block is crucial, since the training would never trigger previously if the bot was not doing any task!

                        if (distanceToHostileEntity <= 32.0) {
                            isBotMoving = false;
                            setBotExecutingTask(false);

                            // Send message only once per threat encounter
                            if (!threatMessageSent) {
                                System.out.println("Hostile mobs detected while bot is idle!");
                                ChatUtils.sendChatMessages(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "Terminating all current tasks due to threat detections");
                                threatMessageSent = true;
                            }


                            FaceClosestEntity.faceClosestEntity(bot, AutoFaceEntity.hostileEntities);



                            // Log details of the detected hostile entity (only once)
                            if (!isHandlerTriggered) {
                                System.out.println("Closest hostile entity: " + closestHostile.getName().getString()
                                        + " at distance: " + distanceToHostileEntity);
                            }

                            botBusy = true; // Set the bot as busy if hostile entities are in range
                            hostileEntityInFront = true;

                            // Trigger the handler ONLY ONCE
                            if (isHandlerTriggered) {
                                // Handler already running for this threat, skip to avoid infinite loop
                                return;
                            } else {
                                System.out.println("Triggering handler for hostile entity.");
                                isHandlerTriggered = true;

                                BotEventHandler eventHandler = new BotEventHandler(server, bot);

                                if (modCommandRegistry.isTrainingMode) {

                                    try {
                                        eventHandler.detectAndReact(finalRlAgent, distanceToHostileEntity, qTable);
                                        isHandlerTriggered = false; // Reset after handling
                                    } catch (IOException e) {
                                        System.out.println("Exception occurred in startAutoFace: " + e.getMessage());
                                        isHandlerTriggered = false; // Reset even on exception
                                        throw new RuntimeException(e);

                                    }
                                }
                                else {

                                    eventHandler.detectAndReactPlayMode(finalRlAgent, qTable);
                                    isHandlerTriggered = false; // Reset after handling

                                }

                            }
                        }

                    }

                }
                else if ((DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10) <= 5 && DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10)!= 0) || hasSculkNearby)  {
                    NavigationService.suspend(bot.getUUID(), SuspensionReason.THREAT);

                    System.out.println("Triggering handler for danger zone case");
                    isBotMoving = false;
                    setBotExecutingTask(false);



                    botBusy = true;

                    BotEventHandler eventHandler = new BotEventHandler(server, bot);

                    double distanceToHostileEntity = 0.0;

                    if (hostileEntities != null && !hostileEntities.isEmpty()) {
                        Entity closestHostile = hostileEntities.stream()
                                .min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.position())))
                                .orElse(null);

                        if (closestHostile != null) {
                            distanceToHostileEntity = Math.sqrt(closestHostile.distanceToSqr(bot.position()));
                            System.out.println("Closest hostile entity: " + closestHostile.getName().getString()
                                    + " at distance: " + distanceToHostileEntity);
                        }
                    }

                    // first check if bot is moving, and if so, then stop moving.
                    // the hope is that the bot will stop moving ahead of time since the danger zone detector has a wide range.

                    if (NavigationService.isNavigating(bot.getUUID()) || isBotMoving || isBotExecutingTask()) {

                        System.out.println("Stopping movement since danger zone is detected.");

                        // Send message only once per threat encounter
                        if (!threatMessageSent) {
                            ChatUtils.sendChatMessages(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "Terminating all current tasks due to threat detections");
                            threatMessageSent = true;
                        }
                        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "/player " + bot.getName().getString() + " stop");
                    }
                    else {
                        // Stop movement regardless of whether the bot is executing a goal or not.

                        System.out.println("Stopping movement since danger zone is detected.");

                        // Send message only once per threat encounter
                        if (!threatMessageSent) {
                            ChatUtils.sendChatMessages(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "Terminating all current tasks due to threat detections");
                            threatMessageSent = true;
                        }
                        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "/player " + bot.getName().getString() + " stop");
                    }


                    if (modCommandRegistry.isTrainingMode) {

                        try {
                            eventHandler.detectAndReact(finalRlAgent, distanceToHostileEntity ,qTable);
                        } catch (IOException e) {
                            System.out.println("Exception occurred in startAutoFace: " + e.getMessage());
                            throw new RuntimeException(e);

                        }
                    }
                    else {

                        eventHandler.detectAndReactPlayMode(finalRlAgent, qTable);

                    }

                }

                else {
                    // No hostile entities detected - bot is safe
                    NavigationService.resume(bot.getUUID(), SuspensionReason.THREAT);

                    // Reset threat message flag when out of danger
                    threatMessageSent = false;

                    // If bot is still locked on shooting target, check if it's still valid
                    if (isShooting) {
                        if (isShootingTargetValid()) {
                            System.out.println("No hostile entities detected, but shooting target still valid");
                            // Continue facing the shooting target even if no hostiles nearby
                            FaceClosestEntity.faceClosestEntity(bot, List.of(shootingTarget));
                            return;
                        } else {
                            System.out.println("Shooting target no longer valid, clearing lock");
                            clearShootingTarget();
                        }
                    }

                    // Clear hostile entity flags
                    botBusy = false;
                    hostileEntityInFront = false;

                    // Low hunger is evaluated by the RL policy. USE_ITEM is not
                    // forced: its observed hunger gain is learned as a Q transition.
                    BotEventHandler.considerLowHunger(finalRlAgent, qTable, bot);

                    // Safe nighttime has no combat trigger, so explicitly let the
                    // learned policy evaluate its SLEEP action while the bot is idle.
                    if (!((NavigationService.isNavigating(bot.getUUID()) || isBotMoving)
                            || blockDetectionUnit.getBlockDetectionStatus() || isBotExecutingTask())) {
                        BotEventHandler.considerNightSleep(finalRlAgent, qTable, bot);
                    }

                    // Face nearby entities (players, passive mobs, etc.) - but only if bot is NOT busy with tasks
                    if (!((NavigationService.isNavigating(bot.getUUID()) || isBotMoving) || blockDetectionUnit.getBlockDetectionStatus() || isBotExecutingTask())) {
                        FaceClosestEntity.faceClosestEntity(bot, nearbyEntities);

                        // Feature 5 — Player Proximity Awareness.
                        // Runs in the same guard as faceClosestEntity: bot is idle, safe, not moving.
                        // nearbyEntities already computed above — zero extra detection cost.
                        ProximityTracker.tick(bot, nearbyEntities);
                    }
                }


            }

            else if (server != null && !server.isRunning() || bot.hasDisconnected()) {

                stopAutoFace(bot);

                try {

                    ServerTickEvents.END_LEVEL_TICK.register(world -> {

                        if (!isWorldTickListenerActive) {
                            return; // Skip execution if listener is deactivated
                        }

                    });
                } catch (Exception e) {

                    System.out.println(e.getMessage());
                }


            }


        }, 0, 33, TimeUnit.MILLISECONDS); // Run every 33ms (30 FPS) for ultra-fast projectile detection

    }

    public static void onServerStopped(MinecraftServer minecraftServer) {

        executor3.submit(() -> {
            try {
                stopAutoFace(Bot);
                // Feature 5 — clear all proximity state on server stop
                ProximityTracker.clear();
            } catch (Exception e) {
                LOGGER.error("Failed to stop AutoFace cleanly", e);
            }
        });
    }


    /**
     * ADAPTIVE PANIC EVASION - Universal evasion method for all distances
     *
     * Calculation Logic:
     * 1. Average player can accurately hit moving target at ~15 blocks max
     * 2. Arrow travel time: ~1.5 seconds for 15 blocks
     * 3. Player reaction/tracking time: ~0.5-1.0 seconds
     * 4. Bot needs to keep moving for: arrow travel time + tracking difficulty time
     * 5. Closer shooter = less arrow travel time, but bot needs LONGER evasion (harder to dodge)
     * 6. Formula: evasionTime = baseTime + (distanceCompensation * distance)
     *
     * Distance-based timing:
     * - 5m:  Move for 2000ms (close range, need maximum evasion)
     * - 10m: Move for 1500ms (medium range)
     * - 15m: Move for 1200ms (far range, arrow drops more)
     * - 20m: Move for 1000ms (very far, arrow very inaccurate)
     */
    // Evasion state tracking
    private static volatile boolean isEvading = false;
    private static volatile long evasionStartTime = 0;
    private static volatile net.minecraft.world.entity.LivingEntity evasionThreatSource = null;
    private static volatile int evasionTickCounter = 0;
    private static final double SAFE_ESCAPE_DISTANCE = 25.0; // Consider safe when 25+ blocks away

    public static void executeAdaptivePanicEvasion(ServerPlayer bot, PredictiveThreatDetector.DrawingBowThreat threat, MinecraftServer server) {
        Vec3 botPos = bot.position();
        Vec3 shooterPos = threat.shooterPos;
        double distance = threat.distance;

        // Mark that we're evading
        isEvading = true;
        evasionStartTime = System.currentTimeMillis();
        evasionThreatSource = threat.shooter;
        evasionTickCounter = 0;

        LOGGER.info("⚡ ADAPTIVE EVASION: Starting from distance {}m (will stop at {}m or threat eliminated)",
            String.format("%.1f", distance), SAFE_ESCAPE_DISTANCE);

        // Calculate direction AWAY from shooter with randomness
        Vec3 awayFromShooter = botPos.subtract(shooterPos).normalize();

        // Add large random angle (±45 degrees) for unpredictability
        double randomAngle = (Math.random() - 0.5) * Math.PI / 2.0; // ±90 degrees
        double cos = Math.cos(randomAngle);
        double sin = Math.sin(randomAngle);
        Vec3 scrambledDir = new Vec3(
            awayFromShooter.x * cos - awayFromShooter.z * sin,
            0,
            awayFromShooter.x * sin + awayFromShooter.z * cos
        ).normalize();

        // Face scrambled direction
        double yaw = Math.toDegrees(Math.atan2(scrambledDir.z, scrambledDir.x)) - 90;
        bot.setYRot((float) yaw);

        LOGGER.info("💨 PANIC! Scrambling away at random angle from shooter!");

        // Start sprinting AND moving forward immediately
        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
            "/player " + bot.getName().getString() + " sprint");
        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
            "/player " + bot.getName().getString() + " move forward");
    }

    /**
     * Called every autoface tick to check if evasion should continue or stop
     * This replaces the fixed-duration timer approach
     */
    private static void updateEvasionStatus(ServerPlayer bot, MinecraftServer server) {
        if (!isEvading || evasionThreatSource == null) {
            return; // Not evading
        }

        evasionTickCounter++;

        // Check if threat is eliminated
        if (!evasionThreatSource.isAlive() || evasionThreatSource.isRemoved()) {
            LOGGER.info("✓ Evasion complete - threat eliminated");
            stopEvasion(bot, server);
            return;
        }

        // Check if bot has reached safe distance
        double currentDistance = Math.sqrt(bot.distanceToSqr(evasionThreatSource.position()));
        if (currentDistance >= SAFE_ESCAPE_DISTANCE) {
            LOGGER.info("✓ Evasion complete - reached safe distance ({}m)", String.format("%.1f", currentDistance));
            stopEvasion(bot, server);
            return;
        }

        // Timeout after 10 seconds (safety fallback) - 300 ticks at 30fps
        if (evasionTickCounter > 300) {
            LOGGER.warn("⚠ Evasion timeout (10s) - stopping");
            stopEvasion(bot, server);
            return;
        }

        // Still evading - occasionally jump and adjust direction
        if (evasionTickCounter % 9 == 0) { // Jump roughly every 300ms (9 ticks at 33ms intervals)
            server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
                "/player " + bot.getName().getString() + " jump");
        }

        // Adjust direction every ~600ms for unpredictability (18 ticks)
        if (evasionTickCounter % 18 == 0 && evasionTickCounter > 18) {
            Vec3 botPos = bot.position();
            Vec3 threatPos = evasionThreatSource.position();
            Vec3 awayFromThreat = botPos.subtract(threatPos).normalize();

            double newRandomAngle = (Math.random() - 0.5) * Math.PI / 6.0; // ±30 degrees
            double newCos = Math.cos(newRandomAngle);
            double newSin = Math.sin(newRandomAngle);
            Vec3 newScrambledDir = new Vec3(
                awayFromThreat.x * newCos - awayFromThreat.z * newSin,
                0,
                awayFromThreat.x * newSin + awayFromThreat.z * newCos
            ).normalize();

            double newYaw = Math.toDegrees(Math.atan2(newScrambledDir.z, newScrambledDir.x)) - 90;
            bot.setYRot((float) newYaw);
        }
    }

    /**
     * Stop evasion maneuvers
     */
    private static void stopEvasion(ServerPlayer bot, MinecraftServer server) {
        if (!isEvading) {
            return;
        }

        isEvading = false;
        evasionThreatSource = null;
        evasionTickCounter = 0;

        // Stop all movement
        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
            "/player " + bot.getName().getString() + " stop");

        // ✅ Signal action completion
        net.shasankp000.GameAI.BotEventHandler.completeAction(bot.getName().getString());
    }

    /**
     * Set the target that the bot is currently shooting at
     * This will make autoface lock onto this target with priority
     */
    public static void setShootingTarget(Entity target) {
        shootingTarget = target;
        shootingStartTime = System.currentTimeMillis();
        isShooting = true;
        LOGGER.info("Shooting target set to: {}", target != null ? target.getName().getString() : "null");
    }

    /**
     * Clear the shooting target and resume normal autoface
     */
    public static void clearShootingTarget() {
        if (shootingTarget != null) {
            LOGGER.info("Clearing shooting target: {}", shootingTarget.getName().getString());
        }
        shootingTarget = null;
        shootingStartTime = 0;
        isShooting = false;
    }

    /**
     * Check if the shooting target is still valid
     * Returns false if target is dead, removed, or timeout exceeded
     */
    private static boolean isShootingTargetValid() {
        if (shootingTarget == null) {
            return false;
        }

        // Check if target is dead or removed
        if (!shootingTarget.isAlive() || shootingTarget.isRemoved()) {
            LOGGER.info("Shooting target {} is no longer valid (dead/removed)", shootingTarget.getName().getString());
            clearShootingTarget();
            return false;
        }

        // Check for timeout (10 seconds max lock)
        long elapsed = System.currentTimeMillis() - shootingStartTime;
        if (elapsed > 10000) {
            LOGGER.warn("Shooting target lock timeout after {} ms", elapsed);
            clearShootingTarget();
            return false;
        }

        return true;
    }

    /**
     * Set projectile defense mode - called when defending from projectiles
     */
    public static void setProjectileDefenseMode(ProjectileDefenseUtils.IncomingProjectile threat, String mode) {
        currentThreat = threat;
        defenseMode = mode;
        isDefendingFromProjectile = true;
        LOGGER.info("Projectile defense mode set to: {} for {}", mode, threat.projectileType);
    }

    /**
     * Clear projectile defense mode and resume normal operations
     */
    public static void clearProjectileDefenseMode() {
        if (currentThreat != null) {
            LOGGER.info("✓ Dodged {} - Threat cleared", currentThreat.projectileType);
        }
        currentThreat = null;
        defenseMode = "none";
        isDefendingFromProjectile = false;
        dodgeExecuted = false; // Reset for next threat
    }

    /**
     * Start persistent shield blocking against an entity
     * Shield will remain raised until entity stops using ranged weapons
     */
    public static void startPersistentBlocking(ServerPlayer bot, net.minecraft.world.entity.LivingEntity attacker, MinecraftServer server) {
        if (isActivelyBlocking && blockingAgainst != null && blockingAgainst.equals(attacker)) {
            // Already blocking against this entity
            return;
        }

        isActivelyBlocking = true;
        blockingAgainst = attacker;
        blockingStartTime = System.currentTimeMillis();

        String botName = bot.getName().getString();
        net.minecraft.commands.CommandSourceStack botSource =
            bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);

        LOGGER.info("🛡 STARTING PERSISTENT SHIELD BLOCK against {}", attacker.getName().getString());

        // Activate continuous shield blocking
        server.execute(() -> {
            server.getCommands().performPrefixedCommand(botSource,
                "/player " + botName + " use continuous");
            LOGGER.info("🛡 Shield block activated - will maintain until threat ends");
        });
    }

    /**
     * Stop persistent shield blocking
     */
    public static void stopPersistentBlocking(ServerPlayer bot, MinecraftServer server) {
        if (!isActivelyBlocking) {
            return; // Not currently blocking
        }

        String botName = bot.getName().getString();
        net.minecraft.commands.CommandSourceStack botSource =
            bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);

        long blockDuration = System.currentTimeMillis() - blockingStartTime;
        LOGGER.info("🛡 STOPPING PERSISTENT SHIELD BLOCK (blocked for {}ms)", blockDuration);

        // Release shield
        server.execute(() -> {
            server.getCommands().performPrefixedCommand(botSource,
                "/player " + botName + " use");
            LOGGER.info("✓ Shield block released");
        });

        isActivelyBlocking = false;
        blockingAgainst = null;
        blockingStartTime = 0;
    }

    /**
     * Check if the entity we're blocking against is still a ranged threat
     * Returns false if entity stopped using bow/crossbow, is dead, or too far away
     */
    public static boolean isBlockingThreatStillValid(ServerPlayer bot) {
        if (!isActivelyBlocking || blockingAgainst == null) {
            return false;
        }

        // Check if attacker is still alive
        if (!blockingAgainst.isAlive() || blockingAgainst.isRemoved()) {
            LOGGER.info("🛡 Blocking threat no longer valid - entity dead/removed");
            return false;
        }

        // Check distance (stop blocking if too far)
        double distance = Math.sqrt(blockingAgainst.distanceToSqr(bot));
        if (distance > 30.0) { // 30 block cutoff
            LOGGER.info("🛡 Blocking threat too far away ({}m) - releasing block", String.format("%.1f", distance));
            return false;
        }

        // Check if entity is still using a ranged weapon
        net.minecraft.world.item.ItemStack activeItem = blockingAgainst.getUseItem();
        net.minecraft.world.item.ItemStack mainHand = blockingAgainst.getMainHandItem();

        boolean hasRangedWeapon = isRangedWeapon(activeItem) || isRangedWeapon(mainHand);

        if (!hasRangedWeapon) {
            LOGGER.info("🛡 Attacker {} switched away from ranged weapon - releasing block",
                blockingAgainst.getName().getString());
            return false;
        }

        // Timeout after 30 seconds of continuous blocking
        long blockDuration = System.currentTimeMillis() - blockingStartTime;
        if (blockDuration > 30000) {
            LOGGER.warn("🛡 Shield block timeout after 30s - releasing");
            return false;
        }

        return true;
    }

    /**
     * Check if an item stack is a ranged weapon (bow or crossbow)
     */
    private static boolean isRangedWeapon(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.contains("bow") || itemId.contains("crossbow");
    }

    /**
     * Update persistent shield blocking state - call this in the main loop
     * Manages shield blocking lifecycle and faces the attacker
     */
    public static void updatePersistentBlocking(ServerPlayer bot, MinecraftServer server) {
        if (!isActivelyBlocking) {
            return; // Not blocking
        }

        // Check if threat is still valid
        if (!isBlockingThreatStillValid(bot)) {
            stopPersistentBlocking(bot, server);
            return;
        }

        // Keep facing the attacker while blocking
        Vec3 botPos = bot.position();
        Vec3 attackerPos = blockingAgainst.position();
        Vec3 toAttacker = attackerPos.subtract(botPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(toAttacker.z, toAttacker.x)) - 90;
        double horizontalDistance = Math.sqrt(toAttacker.x * toAttacker.x + toAttacker.z * toAttacker.z);
        double pitch = -Math.toDegrees(Math.atan2(toAttacker.y, horizontalDistance));

        bot.setYRot((float) yaw);
        bot.setXRot((float) pitch);

        // Log every few seconds to avoid spam
        long elapsed = System.currentTimeMillis() - blockingStartTime;
        if (elapsed % 3000 < 50) { // Log roughly every 3 seconds
            double distance = Math.sqrt(blockingAgainst.distanceToSqr(bot));
            LOGGER.info("🛡 Actively blocking against {} at {}m",
                blockingAgainst.getName().getString(), String.format("%.1f", distance));
        }
    }

    /**
     * Check if currently defending from a projectile
     */
    public static boolean isDefendingFromProjectile() {
        return isDefendingFromProjectile && currentThreat != null;
    }

    public static void stopAutoFace(ServerPlayer bot) {
        NavigationService.resume(bot.getUUID(), SuspensionReason.THREAT);
        ScheduledExecutorService executor = botExecutors.remove(bot);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);

                System.out.println("Autoface stopped.");

            } catch (InterruptedException e) {
                System.out.println("Error shutting down executor for bot: {" + bot.getName().getString() + "}" + " " + e);
                Thread.currentThread().interrupt();
            }
        }
    }


    public static void handleBotRespawn(ServerPlayer bot) {
        // Ensure complete cleanup before restart
        stopAutoFace(bot);
        isWorldTickListenerActive = true;

        // Wait briefly to ensure cleanup is complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        startAutoFace(bot);
        LOGGER.info("Bot {} respawned and initialized.", bot.getName().getString());
    }


    public static List<Entity> detectNearbyEntities(ServerPlayer bot, double boundingBoxSize) {
        // Define a bounding box around the bot with the given size
        AABB searchBox = bot.getBoundingBox().inflate(boundingBoxSize, boundingBoxSize, boundingBoxSize);
        return bot.level().getEntities(bot, searchBox);
    }

    public static String determineDirectionToBot(ServerPlayer bot, Entity target) {
        double relativeAngle = getRelativeAngle(bot, target);

        // Determine the direction based on relative angle
        if (relativeAngle <= 45 || relativeAngle > 315) {
            return "front"; // Entity is in front of the bot
        } else if (relativeAngle > 45 && relativeAngle <= 135) {
            return "right"; // Entity is to the right
        } else if (relativeAngle > 135 && relativeAngle <= 225) {
            return "behind"; // Entity is behind the bot
        } else {
            return "left"; // Entity is to the left
        }
    }

    private static double getRelativeAngle(Entity bot, Entity target) {
        double botX = bot.getX();
        double botZ = bot.getZ();
        double targetX = target.getX();
        double targetZ = target.getZ();

        // Get bot's facing direction
        float botYaw = bot.getYRot(); // Horizontal rotation (0 = south, 90 = west, etc.)

        // Calculate relative angle to the entity
        double deltaX = targetX - botX;
        double deltaZ = targetZ - botZ;
        double angleToEntity = Math.toDegrees(Math.atan2(deltaZ, deltaX)); // Angle from bot to entity

        // Normalize angles between 0 and 360
        double botFacing = (botYaw + 360) % 360;
        double relativeAngle = (angleToEntity - botFacing + 360) % 360;
        return relativeAngle;
    }


}
