package net.shasankp000.PathFinding;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shasankp000.Commands.modCommandRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PathTracer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final double WALKING_SPEED = 4.317; // blocks per second
    private static final double SPRINTING_SPEED = 5.612; // blocks per second
    private static Queue<Segment> segmentQueue = new LinkedList<>();
    private static boolean shouldSprint;
    private static final int MAX_RETRIES = 5; // Reduced from 10

    public static class BotSegmentManager {
        private static final Queue<Segment> jobQueue = new LinkedList<>();
        private final MinecraftServer server;
        private static ServerCommandSource botSource = null;
        private final String botName;
        private int retries = 0;
        private static boolean isMoving = false;
        private static Segment currentSegment = null; // Track current segment

        // ✅ Add completion tracking
        private static CompletableFuture<String> pathCompletionFuture = null;
        private static final AtomicReference<String> finalResult = new AtomicReference<>("");

        public static boolean getBotMovementStatus() {
            return isMoving;
        }

        public BotSegmentManager(MinecraftServer server, ServerCommandSource botSource, String botName) {
            this.server = server;
            BotSegmentManager.botSource = botSource;
            this.botName = botName;
        }

        public static void clearJobs() {
            jobQueue.clear();
            isMoving = false;
            currentSegment = null;

            // ✅ Reset completion tracking
            if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                pathCompletionFuture.complete("Path cleared");
            }
            pathCompletionFuture = null;
            finalResult.set("");

            LOGGER.info("Job queue flushed.");
        }

        // ✅ Add method to get completion future
        public static CompletableFuture<String> getPathCompletionFuture() {
            if (pathCompletionFuture == null) {
                pathCompletionFuture = new CompletableFuture<>();
            }
            return pathCompletionFuture;
        }

        public void addSegmentJob(Segment segment) {
            jobQueue.add(segment);
        }

        public void startProcessing() {
            // ✅ Initialize completion future if not already done
            if (pathCompletionFuture == null) {
                pathCompletionFuture = new CompletableFuture<>();
            }

            if (!jobQueue.isEmpty()) {
                currentSegment = jobQueue.poll();
                executeSegment(currentSegment);
            } else {
                isMoving = false;
                currentSegment = null;

                // if was sprinting previously, set to false.
                if (shouldSprint) {
                    shouldSprint = false; // reset the flag
                    server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " unsprint");
                }

                // ✅ Complete the path and set final result
                String result = tracePathOutput(botSource);
                finalResult.set(result);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(result);
                }

                LOGGER.info("No more segments to process. Final result: {}", result);
            }
        }

        public static Queue<Segment> getJobQueue() {
            return jobQueue;
        }

        private void executeSegment(Segment segment) {
            LOGGER.info("START segment: " + segment);
            updateFacing(segment);

            int distance = calculateAxisAlignedDistance(segment.start(), segment.end());

            if (distance == 0) {
                LOGGER.info("Skipping zero-length segment: {}", segment);
                waitForSegmentCompletion(segment); // instantly mark as complete
                return;
            }

            double speed = segment.sprint() ? SPRINTING_SPEED : WALKING_SPEED;
            double travelTime = roundTo2Decimals(distance / speed);
            long delayMillis = (long) (travelTime * 1000);

            System.out.println("Walking for " + travelTime + " seconds");

            modCommandRegistry.moveForward(server, botSource, botName);

            long jumpDelay = Math.max(100, delayMillis - Math.min(200, delayMillis / 2)); // Jump halfway or at least 100ms

            // Schedule jump if required, slightly before reaching the target to ensure proper timing
            if (segment.jump()) {
                scheduler.schedule(() -> {
                    server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " jump");
                    LOGGER.info(botName + " performed a jump!");
                }, jumpDelay, TimeUnit.MILLISECONDS); // Jump 200ms before reaching target
            }

            if (segment.sprint()) {
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " sprint");
            }
            else {
                // if was set to sprint before, stop sprinting anyways.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " unsprint");
            }

            scheduler.schedule(() -> {
                modCommandRegistry.stopMoving(server, botSource, botName);
                LOGGER.info(botName + " has stopped walking!");
            }, delayMillis, TimeUnit.MILLISECONDS);

            isMoving = true;

            // Increased delay to allow for movement settling
            scheduler.schedule(() -> waitForSegmentCompletion(segment), delayMillis + 100, TimeUnit.MILLISECONDS);
        }

        private void waitForSegmentCompletion(Segment completedSegment) {
            ServerPlayerEntity player = botSource.getPlayer();
            if (player == null) {
                LOGGER.error("Player is null, cannot continue pathfinding");
                // ✅ Complete with error
                String errorResult = "Player not found";
                finalResult.set(errorResult);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(errorResult);
                }
                return;
            }

            BlockPos currentPos = player.getBlockPos();

            // Get the final destination for distance checking
            BlockPos finalDestination = getFinalDestination();

            LOGGER.info("Bot at: {}, Target: {}, Final: {}", currentPos, completedSegment.end(), finalDestination);

            // Check if we've reached the segment target with improved tolerance
            if (hasReachedTarget(currentPos, completedSegment.end(), completedSegment)) {
                LOGGER.info("✅ Reached segment target: {}", completedSegment.end());
                retries = 0;
                isMoving = false;
                startProcessing(); // This will either start next segment or complete the path
                return;
            }

            // Check if we're close to the final destination and can stop
            if (isCloseToFinalDestination(currentPos, finalDestination)) {
                LOGGER.info("✅ Bot is close enough to final destination: {}", finalDestination);
                flushAllMovementTasks();
                isMoving = false;

                // ✅ Complete with success
                String result = tracePathOutput(botSource);
                finalResult.set(result);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(result);
                }
                return;
            }

            // Try to find if we're already at a future segment position
            if (tryAdvancedSegmentSkip(currentPos)) {
                return;
            }

            retries++;
            LOGGER.warn("Segment not reached. Retry {}/{}", retries, MAX_RETRIES);

            // If we haven't exceeded retries, try to re-path
            if (retries < MAX_RETRIES) {
                LOGGER.info("Attempting re-pathfinding from {} to {}", currentPos, finalDestination);

                ServerWorld world = botSource.getServer().getOverworld();
                List<PathFinder.PathNode> newPath = PathFinder.calculatePath(currentPos, finalDestination, world);

                if (newPath.isEmpty()) {
                    LOGGER.error("Re-pathfinding failed! Stopping bot.");
                    flushAllMovementTasks();
                    isMoving = false;

                    // ✅ Complete with failure
                    String failureResult = "Re-pathfinding failed";
                    finalResult.set(failureResult);
                    if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                        pathCompletionFuture.complete(failureResult);
                    }
                    return;
                }

                // Clear old segments and create new ones
                clearJobs();
                segmentQueue.clear();

                List<PathFinder.PathNode> simplified = PathFinder.simplifyPath(newPath, world);
                Queue<Segment> newSegments = PathFinder.convertPathToSegments(simplified, shouldSprint);

                LOGGER.info("New path generated with {} segments", newSegments.size());
                segmentQueue = new LinkedList<>(newSegments);
                newSegments.forEach(this::addSegmentJob);

                retries = 0; // Reset retries for new path
                startProcessing();
            } else {
                LOGGER.warn("Max retries exceeded. Stopping pathfinding.");
                flushAllMovementTasks();
                isMoving = false;

                // ✅ Complete with retry failure
                String retryFailureResult = "Max retries exceeded";
                finalResult.set(retryFailureResult);
                if (pathCompletionFuture != null && !pathCompletionFuture.isDone()) {
                    pathCompletionFuture.complete(retryFailureResult);
                }
            }
        }

        private BlockPos getFinalDestination() {
            if (segmentQueue.isEmpty()) {
                return currentSegment != null ? currentSegment.end() : null;
            }

            // Get the last segment's end position
            Segment lastSegment = ((LinkedList<Segment>) segmentQueue).peekLast();
            return lastSegment != null ? lastSegment.end() : (currentSegment != null ? currentSegment.end() : null);
        }

        private boolean isCloseToFinalDestination(BlockPos currentPos, BlockPos finalDestination) {
            if (finalDestination == null) return false;

            double distance = Math.sqrt(currentPos.getSquaredDistance(finalDestination));
            return distance <= 2.0; // Within 2 blocks is considered "close enough"
        }

        private boolean tryAdvancedSegmentSkip(BlockPos currentPos) {
            // Check if current position matches any upcoming segment start/end
            List<Segment> remainingSegments = new ArrayList<>(jobQueue);

            for (int i = 0; i < remainingSegments.size(); i++) {
                Segment segment = remainingSegments.get(i);

                // Check if we're at this segment's start or end
                if (isPositionMatch(currentPos, segment.start()) || isPositionMatch(currentPos, segment.end())) {
                    LOGGER.info("✅ Bot advanced to segment {}: {}", i, segment);

                    // Clear old segments up to this point
                    clearJobs();

                    // Add remaining segments starting from this one
                    for (int j = i; j < remainingSegments.size(); j++) {
                        addSegmentJob(remainingSegments.get(j));
                    }

                    retries = 0;
                    startProcessing();
                    return true;
                }
            }
            return false;
        }

        private boolean isPositionMatch(BlockPos pos1, BlockPos pos2) {
            return Math.abs(pos1.getX() - pos2.getX()) <= 1 &&
                    Math.abs(pos1.getY() - pos2.getY()) <= 1 &&
                    Math.abs(pos1.getZ() - pos2.getZ()) <= 1;
        }

        // ✅ Updated to return proper format for parsing
        public static String tracePathOutput(ServerCommandSource botSource) {
            if (botSource == null || botSource.getPlayer() == null) {
                return "Bot not found";
            }

            ServerPlayerEntity bot = botSource.getPlayer();
            BlockPos currentPos = bot.getBlockPos();

            // Return in the format expected by parseOutputValues
            return String.format("Bot moved to position - x: %d y: %d z: %d",
                    currentPos.getX(), currentPos.getY(), currentPos.getZ());
        }

        // Improved target reaching detection
        private boolean hasReachedTarget(BlockPos current, BlockPos target, Segment segment) {
            ServerPlayerEntity player = botSource.getPlayer();
            if (player == null) return false;

            // Use entity position for more accurate checking
            double playerX = player.getX();
            double playerY = player.getY();
            double playerZ = player.getZ();

            // Target center coordinates
            double targetX = target.getX() + 0.5;
            double targetY = target.getY();
            double targetZ = target.getZ() + 0.5;

            double dx = Math.abs(playerX - targetX);
            double dy = Math.abs(playerY - targetY);
            double dz = Math.abs(playerZ - targetZ);

            // Dynamic tolerance based on segment type
            double horizontalTolerance = segment.jump() ? 1.0 : 0.8;
            double verticalTolerance = segment.jump() ? 1.2 : 0.8;

            boolean reached = dx <= horizontalTolerance && dz <= horizontalTolerance && dy <= verticalTolerance;

            if (reached) {
                LOGGER.info("Target reached! dx={:.2f}, dy={:.2f}, dz={:.2f} (tolerance: h={}, v={})",
                        dx, dy, dz, horizontalTolerance, verticalTolerance);
            }

            return reached;
        }

        // Update calculateAxisAlignedDistance for precision
        private int calculateAxisAlignedDistance(BlockPos current, BlockPos target) {
            ServerPlayerEntity player = botSource.getPlayer();
            if (player != null) {
                double dx = Math.abs(player.getX() - (target.getX() + 0.5));
                double dy = Math.abs(player.getY() - target.getY());
                double dz = Math.abs(player.getZ() - (target.getZ() + 0.5));
                return (int) Math.max(1, Math.round(dx + dy + dz));
            }
            return Math.abs(current.getX() - target.getX()) + Math.abs(current.getY() - target.getY()) + Math.abs(current.getZ() - target.getZ());
        }

        private double roundTo2Decimals(double value) {
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.setScale(2, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }

        private String lastDirection = "north"; // initialize with something reasonable

        private void updateFacing(Segment segment) {
            BlockPos start = segment.start();
            BlockPos end = segment.end();

            int dx = end.getX() - start.getX();
            int dz = end.getZ() - start.getZ();
            int dy = end.getY() - start.getY();

            String direction = null;

            if (Math.abs(dx) > 0 && dz == 0) {
                direction = dx > 0 ? "east" : "west";
            } else if (Math.abs(dz) > 0 && dx == 0) {
                direction = dz > 0 ? "south" : "north";
            } else if (Math.abs(dy) > 0 && dx == 0 && dz == 0) {
                direction = dy > 0 ? "up" : "down";
            }

            if (direction == null) {
                direction = lastDirection;
            } else {
                lastDirection = direction;
            }

            server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look " + direction);
            LOGGER.info("{} is now facing {} (dx: {}, dy: {}, dz: {})", botName, direction, dx, dy, dz);
        }
    }

    // ✅ Updated to return CompletableFuture for proper async handling
    public static CompletableFuture<String> tracePath(MinecraftServer server, ServerCommandSource botSource, String botName, Queue<Segment> segments, boolean sprint) {
        shouldSprint = sprint;
        segmentQueue = new LinkedList<>(segments); // Create a copy

        // Clear any existing completion future
        BotSegmentManager.clearJobs();

        // Create the manager and initialize the completion future FIRST
        BotSegmentManager manager = new BotSegmentManager(server, botSource, botName);
        CompletableFuture<String> completionFuture = BotSegmentManager.getPathCompletionFuture();

        // Start the path execution in a separate thread
        new Thread(() -> {
            try {
                segments.forEach(manager::addSegmentJob);
                manager.startProcessing();
            } catch (Exception e) {
                LOGGER.error("Error starting path processing: ", e);
                if (!completionFuture.isDone()) {
                    completionFuture.complete("Path processing failed: " + e.getMessage());
                }
            }
        }).start();

        return completionFuture;
    }


    public static void flushAllMovementTasks() {
        segmentQueue.clear();
        BotSegmentManager.clearJobs();
        LOGGER.info("All movement tasks flushed");
    }
}