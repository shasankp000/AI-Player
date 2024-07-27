package net.shasankp000.PathFinding;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.shasankp000.Commands.spawnFakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.shasankp000.Commands.spawnFakePlayer.stopMoving;
import static net.shasankp000.PathFinding.PathFinder.identifyPrimaryAxis;
import static net.shasankp000.Commands.spawnFakePlayer.moveForward;


public class PathTracer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final double WALKING_SPEED = 4.317; // Blocks per second
    private static final double extraTime = 9.79; // Extra time due to gradual stopping. Need to do something about this.

    public static class MovementJob implements Comparable<MovementJob> {
        String axis;
        int priority;

        public MovementJob(String axis, int priority) {
            this.axis = axis;
            this.priority = priority;
        }

        @Override
        public int compareTo(MovementJob other) {
            return Integer.compare(this.priority, other.priority);
        }
    }

    public static Map<String, Integer> assignPriority(List<String> axisPriorityList) {
        Map<String, Integer> priorityMap = new HashMap<>();

        for (int i = 0; i < axisPriorityList.size(); i++) {
            // Assign priorities based on the order of axes in the list
            // The first axis in the list gets the highest priority (1)
            priorityMap.put(axisPriorityList.get(i), i + 1);
        }

        return priorityMap;
    }

    public static class BotMovementManager {
        private Queue<MovementJob> jobQueue = new LinkedList<>();
        private MinecraftServer server;
        private ServerCommandSource botSource;
        private String botName;
        private List<BlockPos> path;
        private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private boolean isMoving = false;

        public BotMovementManager(MinecraftServer server, ServerCommandSource botSource, String botName, List<BlockPos> path) {
            this.server = server;
            this.botSource = botSource;
            this.botName = botName;
            this.path = path;
        }

        public void addMovementJob(String axis, int priority) {
            jobQueue.add(new MovementJob(axis, priority));
        }

        public void processJobs() {
            if (!jobQueue.isEmpty()) {
                MovementJob job = jobQueue.poll();
                executeMovement(job);
            }
            else {
                LOGGER.info("No more jobs to process");
            }
        }

        private void executeMovement(MovementJob job) {

            System.out.println(botName + " is currently facing in " + Objects.requireNonNull(botSource.getPlayer()).getHorizontalFacing().getAxis().toString());

            updateFacing(server, botSource, botName, path, job.axis, Objects.requireNonNull(botSource.getPlayer()).getHorizontalFacing().getAxis().toString());

            BlockPos lastPos = path.get(path.size() - 1);
            BlockPos currentBotPos = botSource.getPlayer().getBlockPos();

            int distance = calculateDistance(job.axis, currentBotPos, lastPos);

            double travelTime = calculateTravelTime(distance);

            int roundedTime = (int) ((int) Math.round(travelTime));

            System.out.println(roundedTime);

            makeBotWalkForward(server, botSource, botName, travelTime);

            isMoving = true; // Mark as moving

            System.out.println(isMoving);
            System.out.println("Executing movement on axis: " + job.axis);
//
            // Schedule a task to check for movement completion
            scheduler.schedule(() -> waitForMovementCompletion(job), (roundedTime * 1000L), TimeUnit.MILLISECONDS);
        }

        private void waitForMovementCompletion(MovementJob job) {
          //  System.out.println("Waiting for movement job completion");
            BlockPos lastPos = path.get(path.size() - 1);
            BlockPos currentBotPos = Objects.requireNonNull(botSource.getPlayer()).getBlockPos();

            if (hasReachedTarget(job.axis, currentBotPos, lastPos)) {
                System.out.println("Movement job completed");
                isMoving = false; // Mark as not moving
                processJobs(); // Process the next job if available
            } else {
                // Schedule another check if not yet reached the target
                scheduler.schedule(() -> waitForMovementCompletion(job), 100, TimeUnit.MILLISECONDS);
            }
        }

        private boolean hasReachedTarget(String axis, BlockPos currentPos, BlockPos targetPos) {

           // System.out.println(axis);
            return switch (axis) {
                case "x" -> currentPos.getX() > targetPos.getX();
                case "y" -> currentPos.getY() == targetPos.getY();
                case "z" -> currentPos.getZ() > targetPos.getZ();
                default -> false;
            };
        }
    }

    public static void tracePath(MinecraftServer server, ServerCommandSource botSource, String botName, List<BlockPos> path) {
        new Thread(() -> {
            List<String> axisPriorityList = identifyPrimaryAxis(path);
            Map<String, Integer> priorityMap = assignPriority(axisPriorityList);

            BotMovementManager manager = new BotMovementManager(server, botSource, botName, path);

            for (Map.Entry<String, Integer> entry : priorityMap.entrySet()) {
                manager.addMovementJob(entry.getKey(), entry.getValue());
            }

            manager.processJobs();


        }).start();
    }


    private static int calculateDistance(String axis, BlockPos start, BlockPos end) {
        return switch (axis) {
            case "x" -> Math.abs(end.getX() - start.getX());
            case "z" -> Math.abs(end.getZ() - start.getZ());
            default -> 0;
        };
    }

    private static double calculateTravelTime(int distance) {
        return distance / WALKING_SPEED;
    }

    private static void makeBotWalkForward(MinecraftServer server ,ServerCommandSource botSource, String botName, double travelTime) {

        int roundedTravelTime = (int) Math.round(travelTime); // Round to the nearest whole number
        moveForward(server, botSource, botName);
        scheduler.schedule(new spawnFakePlayer.BotMovementTask(server, botSource, botName), roundedTravelTime, TimeUnit.SECONDS);


    }

    private static String getPosNeg(List<BlockPos> path, String primaryAxis) {

        String direction = "";
        BlockPos lastPos = path.get(path.size() - 1);

        if (primaryAxis.equals("x")) {

            if (lastPos.getX() > 0) {
                direction = "positive";
            }
            else {
                direction = "negative";
            }

        }
        else if (primaryAxis.equals("z")) {

            if (lastPos.getZ() > 0) {
                direction = "positive";
            }
            else {
                direction = "negative";
            }

        }

        return direction;
    }

    private static void updateFacing(MinecraftServer server ,ServerCommandSource botSource, String botName, List<BlockPos> path, String primaryAxis, String facingAxis) {
        String posNeg = "";

        // Determine direction and face accordingly.
        if (primaryAxis.equals(facingAxis)) {
            posNeg = getPosNeg(path, primaryAxis);

            if(facingAxis.equals("x") && posNeg.equals("positive")) {
                // turn east.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look east");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing east now");

            }
            else if(facingAxis.equals("x") && posNeg.equals("negative")) {
                // turn west.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look west");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing west now");
            }

            if(facingAxis.equals("z") && posNeg.equals("positive")) {
                // turn south.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look south");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing south now");

            }
            else if(facingAxis.equals("z") && posNeg.equals("negative")) {
                // turn north.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look north");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing north now");
            }

        }

        else {

            // In the case of execution of further jobs, when the bot is not facing in the primaryAxis, this part of the code is called.

            posNeg = getPosNeg(path, primaryAxis);

            if(primaryAxis.equals("x") && posNeg.equals("positive")) {
                // turn east.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look east");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing east now");

            }
            else if(primaryAxis.equals("x") && posNeg.equals("negative")) {
                // turn west.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look west");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing west now");
            }

            if(primaryAxis.equals("z") && posNeg.equals("positive")) {
                // turn south.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look south");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing south now");

            }
            else if(primaryAxis.equals("z") && posNeg.equals("negative")) {
                // turn north.
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " look north");
                server.getCommandManager().executeWithPrefix(botSource, "/say I am facing north now");
            }
        }


    }

    private static void moveBotInAxis(ServerCommandSource botSource, String botName, int delta, char axis) {
        // Implement movement logic here
        // For example, you could use the Carpet mod's move command:
        String direction;
        if (delta > 0) {
            direction = (axis == 'x' ? "right" : axis == 'y' ? "up" : "forward");
            System.out.println(direction);
        } else {
            direction = (axis == 'x' ? "left" : axis == 'y' ? "down" : "back");
            System.out.println(direction);
        }

//        botSource.getServer().getCommandManager().executeWithPrefix(botSource.withLevel(2),
//                String.format("/player %s move %s", botName, direction));
    }
}
