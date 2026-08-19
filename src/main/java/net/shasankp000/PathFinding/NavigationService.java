package net.shasankp000.PathFinding;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.shasankp000.PlayerUtils.FoodConsumptionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owns all server-authoritative, per-bot navigation sessions. */
public final class NavigationService {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final Map<UUID, NavigationSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int STALL_WINDOW_TICKS = 20;
    private static final double MIN_WINDOW_PROGRESS = 0.05;
    private static final int MAX_RECOVERIES = 3;
    private static final int MAX_PENALTIES = 32;
    private static final long PENALTY_TTL_TICKS = 200L;
    private static final int SURFACE_AIR_THRESHOLD = 120;
    private static final int CRITICAL_AIR_THRESHOLD = 20;
    private static final int MAX_SURFACE_STALL_TICKS = 40;
    private static final long PLANNING_TIME_BUDGET_NANOS = 2_000_000L;
    private static final Map<UUID, EnumSet<SuspensionReason>> SUSPENSIONS = new HashMap<>();
    private static final Set<UUID> PARTICLE_DEBUG = ConcurrentHashMap.newKeySet();
    private static int planningCursor;
    private static int lastGlobalPlanningBudget;

    private NavigationService() {}

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ServerTickEvents.END_SERVER_TICK.register(NavigationService::tick);
        }
    }

    public static CompletableFuture<NavigationResult> navigate(ServerPlayerEntity player, BlockPos goal,
                                                               NavigationOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(options, "options");
        MinecraftServer server = player.getWorld().getServer();
        CompletableFuture<NavigationResult> future = new CompletableFuture<>();
        Runnable begin = () -> beginOnServer(player, goal, options, future);
        if (server == null) {
            future.complete(new NavigationResult(NavigationResult.Status.PLAYER_UNAVAILABLE,
                    player.getBlockPos(), "Server unavailable"));
        } else if (server.isOnThread()) {
            begin.run();
        } else {
            server.execute(begin);
        }
        return future;
    }

    /** Temporarily navigates around an obstacle, then replans toward the original destination. */
    public static CompletableFuture<NavigationResult> navigateOverride(ServerPlayerEntity player, BlockPos goal,
                                                                       NavigationOptions options,
                                                                       SuspensionReason owner) {
        CompletableFuture<NavigationResult> future = new CompletableFuture<>();
        MinecraftServer server = player.getWorld().getServer();
        if (server == null) {
            future.complete(new NavigationResult(NavigationResult.Status.PLAYER_UNAVAILABLE,
                    player.getBlockPos(), "Server unavailable"));
            return future;
        }
        Runnable begin = () -> {
            NavigationSession session = SESSIONS.get(player.getUuid());
            if (session == null) {
                beginOnServer(player, goal, options, future);
                NavigationSession standalone = SESSIONS.get(player.getUuid());
                if (standalone != null) {
                    standalone.standaloneSuspensionOwner = owner;
                    if (standalone.search == null) startPlanning(standalone, player, ReplanReason.INITIAL);
                }
                return;
            }
            if (session.override != null) session.override.future.complete(new NavigationResult(
                    NavigationResult.Status.CANCELLED, player.getBlockPos(), "Replaced by a newer override"));
            session.override = new NavigationOverride(goal, options, owner, future);
            if (player instanceof ServerPlayerInterface playerInterface)
                stopNavigationInputs(playerInterface.getActionPack());
            startPlanning(session, player, ReplanReason.INITIAL);
        };
        if (server.isOnThread()) begin.run(); else server.execute(begin);
        return future;
    }

    public static boolean isNavigating(UUID botId) {
        return SESSIONS.containsKey(botId);
    }

    public static boolean isAnyNavigating() {
        return !SESSIONS.isEmpty();
    }

    public static void suspend(UUID botId, SuspensionReason reason) {
        runForBot(botId, () -> {
            EnumSet<SuspensionReason> reasons = SUSPENSIONS.computeIfAbsent(botId,
                    ignored -> EnumSet.noneOf(SuspensionReason.class));
            if (!updateSuspensions(reasons, reason, true)) return;
            NavigationSession session = SESSIONS.get(botId);
            ServerPlayerEntity player = session == null ? resolvePlayer(botId) : session.server.getPlayerManager().getPlayer(botId);
            if (player instanceof ServerPlayerInterface playerInterface)
                stopNavigationInputs(playerInterface.getActionPack());
            if (session != null) session.resetProgressWindow(player == null ? Vec3d.ZERO : player.getPos());
        });
    }

    public static void resume(UUID botId, SuspensionReason reason) {
        runForBot(botId, () -> {
            EnumSet<SuspensionReason> reasons = SUSPENSIONS.get(botId);
            if (reasons == null || !updateSuspensions(reasons, reason, false)) return;
            if (!reasons.isEmpty()) return;
            SUSPENSIONS.remove(botId);
            NavigationSession session = SESSIONS.get(botId);
            ServerPlayerEntity player = session == null ? null : session.server.getPlayerManager().getPlayer(botId);
            if (session != null && player != null) {
                if (player instanceof ServerPlayerInterface playerInterface)
                    stopNavigationInputs(playerInterface.getActionPack());
                startPlanning(session, player, ReplanReason.RESUMED);
            }
        });
    }

    public static Set<SuspensionReason> suspensionReasons(UUID botId) {
        EnumSet<SuspensionReason> reasons = SUSPENSIONS.get(botId);
        return reasons == null ? Set.of() : Set.copyOf(reasons);
    }

    public static void setParticleDebug(UUID botId, boolean enabled) {
        if (enabled) PARTICLE_DEBUG.add(botId); else PARTICLE_DEBUG.remove(botId);
    }

    public static NavigationDebugSnapshot debugSnapshot(ServerPlayerEntity player) {
        NavigationSession session = SESSIONS.get(player.getUuid());
        if (session == null) return new NavigationDebugSnapshot(false, "IDLE", null, null, null,
                0, 0, suspensionReasons(player.getUuid()), player.getAir(), 0, 0,
                0, ReplanReason.INITIAL, 0, 0, 0, lastGlobalPlanningBudget);
        PathFinder.Search search = session.search;
        String phase = session.emergencySurfacing ? "EMERGENCY_SURFACE"
                : isSuspended(session) ? "SUSPENDED" : search != null ? "PLANNING"
                : session.recoveryTicks > 0 ? "RECOVERING"
                : session.override != null ? "OVERRIDE_MOVING" : "MOVING";
        return new NavigationDebugSnapshot(true, phase, activeGoal(session), session.effectiveGoal,
                session.goalDisposition, session.waypointIndex, session.path.size(),
                suspensionReasons(session.botId), player.getAir(), session.recoveries,
                session.penalizedTargets.size(), session.replans.total(), session.replans.lastReason(),
                search == null ? 0 : search.openSize(), search == null ? 0 : search.closedSize(),
                search == null ? 0 : search.expansions(), lastGlobalPlanningBudget);
    }

    private static void runForBot(UUID botId, Runnable task) {
        NavigationSession session = SESSIONS.get(botId);
        MinecraftServer server = session == null ? net.shasankp000.AIPlayer.serverInstance : session.server;
        if (server == null) return;
        if (server.isOnThread()) task.run(); else server.execute(task);
    }

    private static ServerPlayerEntity resolvePlayer(UUID botId) {
        MinecraftServer server = net.shasankp000.AIPlayer.serverInstance;
        return server == null ? null : server.getPlayerManager().getPlayer(botId);
    }

    public static void cancel(UUID botId, String reason) {
        NavigationSession session = SESSIONS.get(botId);
        if (session != null) cancel(session.server, botId, reason);
    }

    public static void cancel(MinecraftServer server, UUID botId, String reason) {
        Runnable cancel = () -> {
            NavigationSession session = SESSIONS.get(botId);
            if (session != null) finish(session, NavigationResult.Status.CANCELLED, reason);
        };
        if (server.isOnThread()) cancel.run(); else server.execute(cancel);
    }

    public static void cancelAll(MinecraftServer server, String reason) {
        Runnable cancel = () -> new ArrayList<>(SESSIONS.values())
                .forEach(session -> finish(session, NavigationResult.Status.CANCELLED, reason));
        if (server.isOnThread()) cancel.run(); else server.execute(cancel);
    }

    public static void cancelAll(String reason) {
        Set<MinecraftServer> servers = new HashSet<>();
        for (NavigationSession session : SESSIONS.values()) servers.add(session.server);
        for (MinecraftServer server : servers) cancelAll(server, reason);
    }

    private static void beginOnServer(ServerPlayerEntity player, BlockPos goal, NavigationOptions options,
                                      CompletableFuture<NavigationResult> future) {
        NavigationSession previous = SESSIONS.get(player.getUuid());
        if (previous != null) finish(previous, NavigationResult.Status.CANCELLED, "Replaced by a newer route");
        if (!player.isAlive() || player.isDisconnected()) {
            future.complete(new NavigationResult(NavigationResult.Status.PLAYER_UNAVAILABLE,
                    player.getBlockPos(), "Player is unavailable"));
            return;
        }
        NavigationSession session = new NavigationSession(player, goal, options,
                GENERATIONS.incrementAndGet(), future);
        SESSIONS.put(player.getUuid(), session);
        if (!isSuspended(session)) startPlanning(session, player, ReplanReason.INITIAL);
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        List<NavigationSession> planners = new ArrayList<>();
        for (NavigationSession session : new ArrayList<>(SESSIONS.values())) {
            if (SESSIONS.get(session.botId) != session) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.botId);
            if (player == null || !player.isAlive() || player.isDisconnected()) {
                finish(session, NavigationResult.Status.PLAYER_UNAVAILABLE, "Player became unavailable");
                continue;
            }
            if (player.getWorld().getRegistryKey() != session.dimension) {
                finish(session, NavigationResult.Status.CANCELLED, "Player changed dimension");
                continue;
            }
            session.lastPosition = player.getBlockPos();
            renderDebugRoute(session, player);
            if (player.isSubmergedInWater() && player.getAir() <= SURFACE_AIR_THRESHOLD) {
                beginEmergencySurface(session, player);
            }
            if (session.emergencySurfacing) {
                tickEmergencySurface(session, player);
                continue;
            }
            if (FoodConsumptionTool.isConsumptionInProgress(session.botId)) {
                suspend(session.botId, SuspensionReason.EATING);
            }
            if (isSuspended(session)) {
                session.resetProgressWindow(player.getPos());
                continue;
            }
            if (session.search != null) planners.add(session);
            else tickMovement(session, player);
        }
        runPlanningRoundRobin(server, planners);
    }

    private static void renderDebugRoute(NavigationSession session, ServerPlayerEntity player) {
        if (!PARTICLE_DEBUG.contains(session.botId) || player.getWorld().getTime() % 10 != 0
                || session.path.isEmpty()) return;
        int limit = Math.min(128, session.path.size());
        for (int i = 0; i < limit; i++) {
            PathFinder.PathNode node = session.path.get(i);
            Vec3d point = node.target();
            var particle = i == session.waypointIndex ? ParticleTypes.HAPPY_VILLAGER
                    : node.movement().isActionBoundary() ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
            ((ServerWorld) player.getWorld()).spawnParticles(particle, point.x, point.y + 0.15, point.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void runPlanningRoundRobin(MinecraftServer server, List<NavigationSession> planners) {
        if (planners.isEmpty()) return;
        double averageTickMs = server.getAverageTickTime();
        int budget = planningBudgetForTickMillis(averageTickMs);
        lastGlobalPlanningBudget = budget;
        long deadline = System.nanoTime() + PLANNING_TIME_BUDGET_NANOS;
        int start = Math.floorMod(planningCursor++, planners.size());
        List<Integer> caps = planners.stream().map(session -> activeOptions(session).expansionsPerTick()).toList();
        int[] shares = allocatePlanningShares(budget, caps, start);
        for (int visited = 0; visited < planners.size() && budget > 0 && System.nanoTime() < deadline; visited++) {
            int index = (start + visited) % planners.size();
            NavigationSession session = planners.get(index);
            if (SESSIONS.get(session.botId) != session || session.search == null) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.botId);
            if (player == null) continue;
            int share = shares[index];
            if (share <= 0) continue;
            int before = session.search.expansions();
            tickPlanning(session, player, share, deadline);
            int consumed = session.search == null ? share : Math.max(1, session.search.expansions() - before);
            budget -= Math.min(share, consumed);
        }
    }

    static int planningBudgetForTickMillis(double averageTickMs) {
        return averageTickMs < 40.0 ? 1_200 : averageTickMs <= 50.0 ? 600 : 200;
    }

    static int[] allocatePlanningShares(int budget, List<Integer> caps, int start) {
        int[] shares = new int[caps.size()];
        int remaining = Math.max(0, budget);
        for (int visited = 0; visited < caps.size() && remaining > 0; visited++) {
            int index = Math.floorMod(start + visited, caps.size());
            int fairShare = Math.max(1, remaining / (caps.size() - visited));
            shares[index] = Math.min(Math.max(0, caps.get(index)), fairShare);
            remaining -= shares[index];
        }
        return shares;
    }

    static boolean updateSuspensions(EnumSet<SuspensionReason> reasons, SuspensionReason reason, boolean add) {
        return add ? reasons.add(reason) : reasons.remove(reason);
    }

    private static void tickPlanning(NavigationSession session, ServerPlayerEntity player, int expansionBudget,
                                     long deadlineNanos) {
        PathFinder.SearchStatus status = session.search.advance(expansionBudget, deadlineNanos);
        if (status == PathFinder.SearchStatus.SEARCHING) return;
        if (status == PathFinder.SearchStatus.INVALID_GOAL) {
            failCurrentObjective(session, player, NavigationResult.Status.INVALID_GOAL,
                    "No safe standing or swimming position near the goal");
            return;
        }
        if (status == PathFinder.SearchStatus.NO_PATH) {
            failCurrentObjective(session, player, NavigationResult.Status.NO_PATH,
                    "No collision-safe route through loaded terrain");
            return;
        }
        session.path = session.search.result();
        session.goalDisposition = session.search.disposition();
        session.effectiveGoal = session.search.effectiveGoal();
        session.search = null;
        if (session.path.size() < 2) {
            if (session.goalDisposition == GoalDisposition.HORIZON_FRONTIER) {
                startPlanning(session, player, ReplanReason.HORIZON_ADVANCE);
            } else {
                reachCurrentObjective(session, player,
                        session.goalDisposition == GoalDisposition.NORMALIZED_FINAL
                                ? "Nearest safe destination reached" : "Already at destination");
            }
            return;
        }
        session.waypointIndex = 1;
        session.cornerPauseTicks = 0;
        session.resetProgressWindow(player.getPos());
        LOGGER.debug("Navigation {} planned {} waypoints for {}", session.generation,
                session.path.size(), player.getGameProfile().getName());
    }

    private static void tickMovement(NavigationSession session, ServerPlayerEntity player) {
        if (session.path.isEmpty() || session.waypointIndex >= session.path.size()) {
            if (session.goalDisposition == GoalDisposition.HORIZON_FRONTIER)
                startPlanning(session, player, ReplanReason.HORIZON_ADVANCE);
            else reachCurrentObjective(session, player, "Destination reached");
            return;
        }
        if (!(player instanceof ServerPlayerInterface playerInterface)) {
            finish(session, NavigationResult.Status.PLAYER_UNAVAILABLE, "Carpet action pack is unavailable");
            return;
        }
        EntityPlayerActionPack actions = playerInterface.getActionPack();
        PathFinder.PathNode waypoint = session.path.get(session.waypointIndex);
        PathFinder.PathNode previous = session.path.get(session.waypointIndex - 1);

        boolean expectedAir = waypoint.movement() == MovementType.JUMP_UP
                || waypoint.movement() == MovementType.DROP || waypoint.movement().isWaterMovement();
        if (!expectedAir && !player.isOnGround() && !player.isTouchingWater()
                && player.getVelocity().y < -0.08) {
            session.replanAfterFall = true;
        }
        if (session.replanAfterFall && player.isOnGround()) {
            stopNavigationInputs(actions);
            session.replanAfterFall = false;
            startPlanning(session, player, ReplanReason.UNEXPECTED_FALL);
            return;
        }

        if (++session.validationTicks >= 10) {
            session.validationTicks = 0;
            if (!PathFinder.isWaypointStillValid(player.getWorld(), waypoint)) {
                stopNavigationInputs(actions);
                startPlanning(session, player, ReplanReason.WAYPOINT_INVALID);
                return;
            }
        }

        if (reachedWaypoint(player, previous.target(), waypoint)) {
            stopNavigationInputs(actions);
            session.waypointIndex++;
            session.cornerPauseTicks = 1;
            session.recoveries = 0;
            session.resetProgressWindow(player.getPos());
            return;
        }

        if (session.cornerPauseTicks > 0) {
            stopNavigationInputs(actions);
            session.cornerPauseTicks--;
            return;
        }

        double deviation = distanceFromSegment(player.getPos(), previous.target(), waypoint.target());
        if (deviation > 2.5) {
            stopNavigationInputs(actions);
            startPlanning(session, player, ReplanReason.ROUTE_DEVIATION);
            return;
        }

        if (session.recoveryTicks > 0) {
            actions.setSprinting(false).setForward(-0.35F)
                    .setStrafing(session.recoveries % 2 == 0 ? 0.7F : -0.7F);
            session.recoveryTicks--;
            if (session.recoveryTicks == 0) {
                stopNavigationInputs(actions);
                startPlanning(session, player, ReplanReason.STALL_RECOVERY);
            }
            return;
        }

        steer(actions, player, waypoint, session);
        updateProgress(session, player, actions, waypoint);
    }

    private static void steer(EntityPlayerActionPack actions, ServerPlayerEntity player,
                              PathFinder.PathNode waypoint, NavigationSession session) {
        NavigationController.Observation observation = observation(player);
        NavigationController.Command command = NavigationController.decide(observation,
                new NavigationController.WaypointState(waypoint.target(), waypoint.movement(),
                        activeOptions(session).sprint(), waypoint.movement().isActionBoundary(),
                        session.jumpCooldown == 0));
        actions.look(command.yaw(), command.pitch()).setStrafing(command.strafe())
                .setForward(command.forward()).setSprinting(command.sprint());
        if (command.jump() == NavigationController.JumpCommand.ONCE) {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
            session.jumpCooldown = 6;
        } else if (command.jump() == NavigationController.JumpCommand.CONTINUOUS) {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.continuous());
        } else if (session.jumpCooldown > 0) {
            session.jumpCooldown--;
        } else {
            actions.start(EntityPlayerActionPack.ActionType.JUMP, null);
        }
    }

    private static NavigationController.Observation observation(ServerPlayerEntity player) {
        return new NavigationController.Observation(player.getPos(), player.getVelocity(), player.isOnGround(),
                player.isTouchingWater(), player.horizontalCollision, player.getAir(),
                player.getHungerManager().getFoodLevel());
    }

    private static void updateProgress(NavigationSession session, ServerPlayerEntity player,
                                       EntityPlayerActionPack actions, PathFinder.PathNode waypoint) {
        double remaining = player.getPos().distanceTo(waypoint.target());
        if (session.progressTicks == 0) session.windowStartRemaining = remaining;
        session.progressTicks++;
        if (session.progressTicks < STALL_WINDOW_TICKS) return;
        double progress = session.windowStartRemaining - remaining;
        session.progressTicks = 0;
        session.windowStartRemaining = remaining;
        if (progress >= MIN_WINDOW_PROGRESS || (!player.isOnGround() && !player.isTouchingWater())) return;
        stopNavigationInputs(actions);
        session.recoveries++;
        addPenalty(session, waypoint.getPos(), player.getWorld().getTime());
        if (session.recoveries > MAX_RECOVERIES) {
            finish(session, NavigationResult.Status.STUCK, "No movement progress after recovery attempts");
        } else {
            session.recoveryTicks = 6;
        }
    }

    private static boolean reachedWaypoint(ServerPlayerEntity player, Vec3d from, PathFinder.PathNode waypoint) {
        Vec3d target = waypoint.target();
        double horizontal = Math.hypot(player.getX() - target.x, player.getZ() - target.z);
        double vertical = Math.abs(player.getY() - target.y);
        if (waypoint.movement().targetIsWater()) return player.getPos().distanceTo(target) <= 0.75;
        boolean settled = waypoint.movement() != MovementType.JUMP_UP && waypoint.movement() != MovementType.DROP
                || player.isOnGround();
        if (horizontal <= 0.35 && vertical <= 0.65 && settled) return true;
        Vec3d edge = target.subtract(from);
        double lengthSquared = edge.x * edge.x + edge.z * edge.z;
        if (lengthSquared < 1.0E-6) return false;
        Vec3d offset = player.getPos().subtract(from);
        double projection = (offset.x * edge.x + offset.z * edge.z) / lengthSquared;
        return projection >= 1.0 && distanceFromSegment(player.getPos(), from, target) <= 0.65
                && vertical <= 0.75 && settled;
    }

    static double distanceFromSegment(Vec3d point, Vec3d start, Vec3d end) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0E-9) return Math.hypot(point.x - start.x, point.z - start.z);
        double t = ((point.x - start.x) * dx + (point.z - start.z) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point.x - (start.x + dx * t), point.z - (start.z + dz * t));
    }

    private static void startPlanning(NavigationSession session, ServerPlayerEntity player, ReplanReason reason) {
        if (SESSIONS.get(session.botId) != session) return;
        if (reason != ReplanReason.INITIAL && session.override == null) {
            double distance = player.getPos().distanceTo(new Vec3d(
                    session.goal.getX() + 0.5, session.goal.getY(), session.goal.getZ() + 0.5));
            if (session.replans.record(distance, reason)) {
                finish(session, NavigationResult.Status.STUCK,
                        "Route replanned repeatedly without progress toward the destination");
                return;
            }
        }
        session.path = List.of();
        session.waypointIndex = 0;
        NavigationOptions options = activeOptions(session);
        session.search = PathFinder.beginSearch(player.getWorld(), player.getPos(), activeGoal(session),
                options.planningHorizon(), activePenalties(session, player.getWorld().getTime()));
        session.resetProgressWindow(player.getPos());
    }

    private static Set<BlockPos> activePenalties(NavigationSession session, long gameTime) {
        pruneExpiredPenalties(session.penalizedTargets, gameTime);
        return Set.copyOf(session.penalizedTargets.keySet());
    }

    static void pruneExpiredPenalties(Map<BlockPos, Long> penalties, long gameTime) {
        penalties.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private static void addPenalty(NavigationSession session, BlockPos pos, long gameTime) {
        session.penalizedTargets.put(pos, gameTime + PENALTY_TTL_TICKS);
        while (session.penalizedTargets.size() > MAX_PENALTIES) {
            Iterator<BlockPos> oldest = session.penalizedTargets.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    private static boolean isSuspended(NavigationSession session) {
        EnumSet<SuspensionReason> reasons = SUSPENSIONS.get(session.botId);
        if (reasons == null || reasons.isEmpty()) return false;
        SuspensionReason allowedOwner = session.override == null
                ? session.standaloneSuspensionOwner : session.override.owner;
        if (allowedOwner == null) return true;
        return reasons.stream().anyMatch(reason -> !overrideAllows(allowedOwner, reason));
    }

    static boolean overrideAllows(SuspensionReason owner, SuspensionReason activeReason) {
        return activeReason == owner
                || owner == SuspensionReason.COMBAT && activeReason == SuspensionReason.THREAT;
    }

    private static BlockPos activeGoal(NavigationSession session) {
        return session.override == null ? session.goal : session.override.goal;
    }

    private static NavigationOptions activeOptions(NavigationSession session) {
        return session.override == null ? session.options : session.override.options;
    }

    private static void reachCurrentObjective(NavigationSession session, ServerPlayerEntity player, String message) {
        if (session.override == null) {
            finish(session, NavigationResult.Status.REACHED, message);
            return;
        }
        NavigationOverride completed = session.override;
        session.override = null;
        completed.future.complete(new NavigationResult(NavigationResult.Status.REACHED,
                player.getBlockPos(), message));
        startPlanning(session, player, ReplanReason.OVERRIDE_COMPLETED);
    }

    private static void failCurrentObjective(NavigationSession session, ServerPlayerEntity player,
                                             NavigationResult.Status status, String message) {
        if (session.override == null) {
            finish(session, status, message);
            return;
        }
        NavigationOverride failed = session.override;
        session.override = null;
        failed.future.complete(new NavigationResult(status, player.getBlockPos(), message));
        startPlanning(session, player, ReplanReason.OVERRIDE_COMPLETED);
    }

    private static void beginEmergencySurface(NavigationSession session, ServerPlayerEntity player) {
        if (session.emergencySurfacing) return;
        session.emergencySurfacing = true;
        session.surfaceBestY = player.getY();
        session.surfaceStallTicks = 0;
        session.search = null;
        session.path = List.of();
        if (player instanceof ServerPlayerInterface playerInterface)
            stopNavigationInputs(playerInterface.getActionPack());
    }

    private static void tickEmergencySurface(NavigationSession session, ServerPlayerEntity player) {
        if (!(player instanceof ServerPlayerInterface playerInterface)) {
            finish(session, NavigationResult.Status.PLAYER_UNAVAILABLE, "Carpet action pack is unavailable");
            return;
        }
        EntityPlayerActionPack actions = playerInterface.getActionPack();
        if (!player.isSubmergedInWater()) {
            stopNavigationInputs(actions);
            session.emergencySurfacing = false;
            startPlanning(session, player, ReplanReason.SURFACED);
            return;
        }
        if (player.getAir() <= CRITICAL_AIR_THRESHOLD) {
            finish(session, NavigationResult.Status.STUCK, "Could not reach breathable space before air became critical");
            return;
        }
        if (player.getY() > session.surfaceBestY + 0.05) {
            session.surfaceBestY = player.getY();
            session.surfaceStallTicks = 0;
        } else if (++session.surfaceStallTicks >= MAX_SURFACE_STALL_TICKS) {
            finish(session, NavigationResult.Status.STUCK, "Emergency surfacing made no upward progress");
            return;
        }
        NavigationController.Command command = NavigationController.emergencySurface(observation(player));
        actions.look(player.getYaw(), command.pitch()).setSprinting(command.sprint())
                .setStrafing(command.strafe()).setForward(command.forward())
                .start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.continuous());
    }

    private static void finish(NavigationSession session, NavigationResult.Status status, String message) {
        NavigationSession active = SESSIONS.get(session.botId);
        if (active == null || !generationMatches(active.generation, session.generation)
                || !SESSIONS.remove(session.botId, session)) return;

        MinecraftServer server = session.server;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.botId);
        BlockPos finalPosition = player != null ? player.getBlockPos() : session.lastPosition;
        if (player instanceof ServerPlayerInterface playerInterface)
            stopNavigationInputs(playerInterface.getActionPack());
        if (session.override != null && !session.override.future.isDone())
            session.override.future.complete(new NavigationResult(NavigationResult.Status.CANCELLED,
                    finalPosition, message));
        session.future.complete(new NavigationResult(status, finalPosition, message));
        LOGGER.debug("Navigation {} completed for {}: {} ({})", session.generation, session.botId, status, message);
    }

    private static void stopNavigationInputs(EntityPlayerActionPack actions) {
        actions.setForward(0.0F).setStrafing(0.0F).setSprinting(false)
                .start(EntityPlayerActionPack.ActionType.JUMP, null);
    }

    static boolean generationMatches(long activeGeneration, long callbackGeneration) {
        return activeGeneration == callbackGeneration;
    }

    private static final class NavigationSession {
        final UUID botId;
        final MinecraftServer server;
        final RegistryKey<World> dimension;
        final BlockPos goal;
        final NavigationOptions options;
        final long generation;
        final CompletableFuture<NavigationResult> future;
        PathFinder.Search search;
        List<PathFinder.PathNode> path = List.of();
        int waypointIndex;
        int progressTicks;
        int validationTicks;
        int cornerPauseTicks;
        int jumpCooldown;
        int recoveries;
        int recoveryTicks;
        double windowStartRemaining;
        GoalDisposition goalDisposition = GoalDisposition.EXACT;
        Vec3d effectiveGoal;
        boolean replanAfterFall;
        final LinkedHashMap<BlockPos, Long> penalizedTargets = new LinkedHashMap<>();
        final ReplanProgressTracker replans;
        boolean emergencySurfacing;
        double surfaceBestY;
        int surfaceStallTicks;
        NavigationOverride override;
        SuspensionReason standaloneSuspensionOwner;
        BlockPos lastPosition;

        NavigationSession(ServerPlayerEntity player, BlockPos goal, NavigationOptions options, long generation,
                          CompletableFuture<NavigationResult> future) {
            this.botId = player.getUuid();
            this.server = Objects.requireNonNull(player.getWorld().getServer());
            this.dimension = player.getWorld().getRegistryKey();
            this.goal = goal;
            this.options = options;
            this.generation = generation;
            this.future = future;
            this.lastPosition = player.getBlockPos();
            this.effectiveGoal = new Vec3d(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
            this.replans = new ReplanProgressTracker(player.getPos().distanceTo(this.effectiveGoal));
        }

        void resetProgressWindow(Vec3d position) {
            progressTicks = 0;
            windowStartRemaining = 0;
            lastPosition = BlockPos.ofFloored(position.x, position.y, position.z);
        }
    }

    private record NavigationOverride(BlockPos goal, NavigationOptions options, SuspensionReason owner,
                                      CompletableFuture<NavigationResult> future) {}
}
