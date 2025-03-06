// There is a small bug in this code when the bot is killed off, and then it's respawned.
// Can't identify where the problem stems from, yet.

package net.shasankp000.Entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.shasankp000.Database.QTable;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.Commands.modCommandRegistry;
import net.shasankp000.Database.QTableStorage;
import net.shasankp000.PlayerUtils.BlockDistanceLimitedSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;

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

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final double BOUNDING_BOX_SIZE = 10.0; // Detection range in blocks
    private static final int INTERVAL_SECONDS = 1; // Interval in seconds to check for nearby entities
    private static final ExecutorService executor3 = Executors.newSingleThreadExecutor();
    public static boolean botBusy;
    public static boolean hostileEntityInFront;
    public static boolean isHandlerTriggered;
    private static boolean isWorldTickListenerActive = true; // Flag to control execution
    private static QTable qTable;
    public static RLAgent rlAgent;
    public static List<Entity> hostileEntities;

    private static final Map<ServerPlayerEntity, ScheduledExecutorService> botExecutors = new HashMap<>();
    private static ServerPlayerEntity Bot = null;
    public static boolean isBotMoving = false;

    public static void startAutoFace(ServerPlayerEntity bot) {
        // Stop any existing executor for this bot

        Bot = bot;

        stopAutoFace(bot);

        ScheduledExecutorService botExecutor = Executors.newSingleThreadScheduledExecutor();

        botExecutors.put(bot, botExecutor);

        MinecraftServer server = bot.getServer();

        // Load Q-table from storage
        try {
            qTable = QTableStorage.load(BotEventHandler.qTableDir + "/qtable.bin");
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

            }

        }


        RLAgent finalRlAgent = rlAgent;
        botExecutor.scheduleAtFixedRate(() -> {
            // Run detection and facing logic

            if (server != null && server.isRunning() && bot.isAlive()) {

                // Detect all entities within the bounding box
                List<Entity> nearbyEntities = detectNearbyEntities(bot, BOUNDING_BOX_SIZE);

                // Filter only hostile entities
                 hostileEntities = nearbyEntities.stream()
                        .filter(entity -> entity instanceof HostileEntity)
                        .toList();

                boolean hasSculkNearby = false;

                BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

                List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

                hasSculkNearby = nearbyBlocks.stream()
                        .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));


                if (!hostileEntities.isEmpty()) {
                    botBusy = true;

                    System.out.println("Hostile entity detected!");

                    if (isBotMoving) {
                        System.out.println("Bot is moving, skipping facing the closest entity");
                    }
                    else {
                        FaceClosestEntity.faceClosestEntity(bot, AutoFaceEntity.hostileEntities);
                    }


                    // Find the closest hostile entity
                    Entity closestHostile = hostileEntities.stream()
                            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(bot.getPos())))
                            .orElseThrow(); // Use orElseThrow since empty case is already handled

                    double distanceToHostileEntity = Math.sqrt(closestHostile.squaredDistanceTo(bot.getPos()));

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
                            } catch (IOException e) {
                                System.out.println("Exception occurred in startAutoFace: " + e.getMessage());
                                throw new RuntimeException(e);

                            }
                        }
                        else {

                            eventHandler.detectAndReactPlayMode(finalRlAgent, qTable);

                        }

                    }
                }
                else if ((DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10) <= 5 && DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10)!= 0) || hasSculkNearby)  {

                    System.out.println("Triggering handler for danger zone case");

                    botBusy = true;

                    BotEventHandler eventHandler = new BotEventHandler(server, bot);

                    double distanceToHostileEntity = 0.0;

                    try {

                        // Find the closest hostile entity
                        Entity closestHostile = hostileEntities.stream()
                                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(bot.getPos())))
                                .orElseThrow(); // Use orElseThrow since empty case is already handled

                        distanceToHostileEntity = Math.sqrt(closestHostile.squaredDistanceTo(bot.getPos()));

                        // Log details of the detected hostile entity
                        System.out.println("Closest hostile entity: " + closestHostile.getName().getString()
                                + " at distance: " + distanceToHostileEntity);

                    } catch (Exception e) {
                        System.out.println("An exception occurred while calculating detecting hostile entities nearby" + e.getMessage());
                        System.out.println(e.getStackTrace());
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
                    botBusy = false; // Clear the flag if no hostile entities are in front

                    hostileEntityInFront = false;

                    FaceClosestEntity.faceClosestEntity(bot, nearbyEntities);

                }


            }

            else if (server != null && !server.isRunning() || bot.isDisconnected()) {

                stopAutoFace(bot);

                try {

                    ServerTickEvents.END_WORLD_TICK.register(world -> {

                        if (!isWorldTickListenerActive) {
                            return; // Skip execution if listener is deactivated
                        }

                    });
                } catch (Exception e) {

                    System.out.println(e.getMessage());
                }


            }


        }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);

    }

    public static void onServerStopped(MinecraftServer minecraftServer) {

        executor3.submit(() -> {
            try {
                stopAutoFace(Bot);
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Ollama client", e);
            }
        });
    }


    public static void stopAutoFace(ServerPlayerEntity bot) {
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


    public static void handleBotRespawn(ServerPlayerEntity bot) {
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


    public static List<Entity> detectNearbyEntities(ServerPlayerEntity bot, double boundingBoxSize) {
        // Define a bounding box around the bot with the given size
        Box searchBox = bot.getBoundingBox().expand(boundingBoxSize, boundingBoxSize, boundingBoxSize);
        return bot.getWorld().getOtherEntities(bot, searchBox);
    }

    public static String determineDirectionToBot(ServerPlayerEntity bot, Entity target) {
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
        float botYaw = bot.getYaw(); // Horizontal rotation (0 = south, 90 = west, etc.)

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
