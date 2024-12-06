// There is a small bug in this code when the bot is killed off and then it's respawned.
// Can't identify where the problem stems from, yet.

package net.shasankp000.Entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.RLAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoFaceEntity {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final double BOUNDING_BOX_SIZE = 10.0; // Detection range in blocks
    private static final int INTERVAL_SECONDS = 2; // Interval in seconds to check for nearby entities
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService executor3 = Executors.newSingleThreadExecutor();
    public static boolean botBusy;
    public static boolean hostileEntityInFront;
    public static boolean isHandlerTriggered;
    private static boolean isWorldTickListenerActive = true; // Flag to control execution


    public static void startAutoFace(ServerPlayerEntity bot) {

        MinecraftServer server = bot.getServer();

        // RL agent hook
        RLAgent rlAgent = new RLAgent(); // Initialize RL agent (use singleton or DI for reusability)


        executor.scheduleAtFixedRate(() -> {
                // Run detection and facing logic

            if (server != null && server.isRunning() && bot.isAlive()) {
                // Detect all entities within the bounding box
                List<Entity> nearbyEntities = detectNearbyEntities(bot, BOUNDING_BOX_SIZE);

                // Filter only hostile entities
                List<Entity> hostileEntities = nearbyEntities.stream()
                        .filter(entity -> entity instanceof HostileEntity)
                        .toList();

                if (!hostileEntities.isEmpty()) {
                    System.out.println("Hostile entity detected!");

                    FaceClosestEntity.faceClosestEntity(bot, hostileEntities);

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
                        System.out.println("Handler already triggered. Skipping.");
                    } else {
                        System.out.println("Triggering handler for hostile entity.");
                        isHandlerTriggered = true;

                        BotEventHandler eventHandler = new BotEventHandler(server, bot);
                        eventHandler.detectAndReact(rlAgent, distanceToHostileEntity);
                    }
                } else {
                    botBusy = false; // Clear the flag if no hostile entities are in front

                    hostileEntityInFront = false;

                    FaceClosestEntity.faceClosestEntity(bot, nearbyEntities);

                }


            }

            else if (server != null && !server.isRunning() || !bot.isAlive()) {

                stopAutoFace();

                System.out.println("Autoface stopped.");

                try {

                    ServerTickEvents.END_WORLD_TICK.register(world -> {

                        if (!isWorldTickListenerActive) {
                            return; // Skip execution if listener is deactivated
                        }


                        for (ServerPlayerEntity player : world.getPlayers()) {
                            if (player.getName().getString().equals(bot.getName().getString())) {
                                System.out.println("Found bot " + bot.getName().getString());
                                if (player.isDisconnected() || player.isDead()) {
                                    stopAutoFace();
                                    isWorldTickListenerActive = false; // Deactivate listener
                                    return; // Exit the loop
                                }
                            }
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
                stopAutoFace();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Ollama client", e);
            }
        });
    }


    public static void stopAutoFace() {
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    public static void handleBotRespawn(ServerPlayerEntity bot) {
        // Stop any ongoing processes tied to the old bot session
        stopAutoFace();
        isWorldTickListenerActive = true; // Reset tick listener control flag

        // Initialize bot-specific handlers
        startAutoFace(bot); // Reinitialize AutoFace
        System.out.println("Bot " + bot.getName().getString() + " respawned and initialized.");
    }


    public static List<Entity> detectNearbyEntities(ServerPlayerEntity bot, double boundingBoxSize) {
        // Define a bounding box around the bot with the given size
        Box searchBox = bot.getBoundingBox().expand(boundingBoxSize, boundingBoxSize, boundingBoxSize);
        return bot.getWorld().getOtherEntities(bot, searchBox);
    }

}
