package net.shasankp000.Entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.PathFinding.PathTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static net.shasankp000.PathFinding.PathTracer.getBotMovementStatus;

public class AutoFaceEntity {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final double BOUNDING_BOX_SIZE = 5.0; // Detection range in blocks
    private static final int INTERVAL_SECONDS = 2; // Interval in seconds to check for nearby entities
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final ScheduledExecutorService executor2 = Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService executor3 = Executors.newSingleThreadExecutor();
    private static final double FOV_ANGLE = 60.0; // Field of view angle in degrees
    private static boolean botBusy;

    private static boolean isBotBusy() {

        return getBotMovementStatus();

    }

    public static void startAutoFace(ServerPlayerEntity bot) {

        MinecraftServer server = bot.getServer();

        executor2.scheduleAtFixedRate(() -> {

            botBusy =  isBotBusy(); // keeps checking for bot status.


        }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);


        executor.scheduleAtFixedRate(() -> {
                // Run detection and facing logic

            if (server!= null && server.isRunning()) {

                if(!botBusy) {

                    List<Entity> nearbyEntities = detectNearbyEntities(bot, BOUNDING_BOX_SIZE);
                    FaceClosestEntity.faceClosestEntity(bot, nearbyEntities);
                }

                else {

                    List<Entity> nearbyEntities = detectNearbyEntities(bot, BOUNDING_BOX_SIZE);
                    nearbyEntities.removeIf(entity -> !(entity instanceof HostileEntity));
                    List<Entity> entitiesInFront = filterEntitiesInFront(bot, nearbyEntities);
                    FaceClosestEntity.faceClosestEntity(bot, entitiesInFront);
                }

            }

            else if (server != null && !server.isRunning()) {

                stopAutoFace();

                System.out.println("Autoface stopped.");

                try {

                    ServerTickEvents.END_WORLD_TICK.register(world -> {
                        for (ServerPlayerEntity player : world.getPlayers()) {
                            if (player.getName().getString().equals(bot.getName().getString())) {
                                System.out.println("Found bot " + bot.getName().getString());
                                if (player.isDisconnected() || player.isDead()) {
                                    stopAutoFace();
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

    private static List<Entity> filterEntitiesInFront(ServerPlayerEntity bot, List<Entity> entities) {
        Vec3d botPosition = bot.getPos();
        Direction getDirection = bot.getHorizontalFacing();
        Vec3d botDirection = Vec3d.of(getDirection.getVector());

        return entities.stream()
                .filter(entity -> {
                    Vec3d entityPosition = entity.getPos().subtract(botPosition).normalize();
                    double angle = Math.toDegrees(Math.acos(botDirection.dotProduct(entityPosition)));
                    return angle < FOV_ANGLE / 2;
                })
                .collect(Collectors.toList());
    }

    public static void stopAutoFace() {
        if (executor != null && !executor.isShutdown()) {
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

    private static List<Entity> detectNearbyEntities(ServerPlayerEntity bot, double boundingBoxSize) {
        // Define a bounding box around the bot with the given size
        Box searchBox = bot.getBoundingBox().expand(boundingBoxSize, boundingBoxSize, boundingBoxSize);
        return bot.getWorld().getOtherEntities(bot, searchBox);
    }

}
