package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shasankp000.PathFinding.PathFinder.PathNode;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.shasankp000.PathFinding.PathFinder.*;

public class GoTo {

    public static String goTo(CommandSourceStack botSource, int x, int y, int z, boolean sprint) {
        MinecraftServer server = botSource.getServer();
        ServerPlayer bot = botSource.getPlayer();
        ServerLevel world = server.overworld();
        String botName = botSource.getTextName();

        if (bot == null) {
            System.out.println("Bot not found!");
            return "Bot not found!";
        }

        System.out.println("Found bot: " + botSource.getTextName());

        try {
            // Calculate the path
            List<PathNode> rawPath = calculatePath(bot.blockPosition(), new BlockPos(x, y, z), world);

            // Simplify + filter
            List<PathNode> finalPath = simplifyPath(rawPath, world);
            LOGGER.info("Path output: {}", finalPath);

            Queue<Segment> segments = convertPathToSegments(finalPath, sprint);
            LOGGER.info("Generated segments: {}", segments);

            // ✅ Trace the path and wait for completion
            CompletableFuture<String> pathFuture = PathTracer.tracePath(server, botSource, botName, segments, sprint);


            // Wait for path completion with timeout
            String result = pathFuture.get(60, TimeUnit.SECONDS);

            String finalOutput = "";

            if (result.equals("Path cleared")) {
                finalOutput = String.format("Bot moved to position - x: %d y: %d z: %d",
                        (int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
            }
            else if (result.equals("Player not found")){
                finalOutput = "Error. Player not found";
            }
            else if (result.equals("Max retries exceeded")) {
                finalOutput = String.format("Bot moved to position - x: %d y: %d z: %d",
                        (int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
            }
            else if (result.equals("Re-pathing failed")) {
                finalOutput = String.format("Bot moved to position - x: %d y: %d z: %d",
                        (int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
            }
            else if (result.contains("Path processing failed: ")) {
                finalOutput = "Error. Path tracer failed to process the pathfinder's data";
            }
            else {
                finalOutput = PathTracer.BotSegmentManager.tracePathOutput(botSource);
            }

            System.out.println("Path tracer output: " + result);
            System.out.println("Final path output: " + finalOutput);

            return finalOutput; // Already in proper format from PathTracer

        } catch (Exception e) {
            LOGGER.error("Error executing goTo: ", e);
            return "Failed to execute goTo: " + e.getMessage();
        }
    }
}