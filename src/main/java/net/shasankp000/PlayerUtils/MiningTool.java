package net.shasankp000.PlayerUtils;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Entity.LookController;
import net.shasankp000.PathFinding.NavigationService;
import net.shasankp000.PathFinding.SuspensionReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Owns server-authoritative, vanilla-timed mining sessions for fake players. */
public final class MiningTool {

    private static final Logger LOGGER = LoggerFactory.getLogger("mining-tool");
    private static final Map<UUID, MiningSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final double MAX_MINING_DISTANCE = 5.0;
    private static final int MIN_DEADLINE_TICKS = 100;
    private static final int MAX_DEADLINE_TICKS = 2_400;
    private static final int DEADLINE_MARGIN_TICKS = 40;

    private MiningTool() {}

    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ServerTickEvents.END_SERVER_TICK.register(MiningTool::tick);
        }
    }

    public static CompletableFuture<MiningResult> mineBlock(ServerPlayer bot, BlockPos targetBlockPos) {
        if (bot == null || targetBlockPos == null) {
            return CompletableFuture.completedFuture(MiningResult.failure(
                    MiningResult.Status.INVALID_TARGET, targetBlockPos, "Bot and target are required"));
        }

        MinecraftServer server = bot.level().getServer();
        if (server == null) {
            return CompletableFuture.completedFuture(MiningResult.failure(
                    MiningResult.Status.PLAYER_UNAVAILABLE, targetBlockPos, "Server is unavailable"));
        }

        UUID botId = bot.getUUID();
        long generation = GENERATIONS.incrementAndGet();
        BlockPos target = targetBlockPos.immutable();
        CompletableFuture<MiningResult> future = new CompletableFuture<>();
        Runnable begin = () -> beginSafely(server, botId, target, generation, future);

        try {
            if (server.isSameThread()) begin.run();
            else server.execute(begin);
        } catch (Throwable throwable) {
            LOGGER.error("Could not schedule mining for {} at {}", botId, target, throwable);
            future.complete(MiningResult.failure(
                    MiningResult.Status.FAILED, target, "Could not schedule mining: " + throwable.getMessage()));
        }

        future.whenComplete((result, error) -> {
            if (future.isCancelled()) {
                cancel(server, botId, generation, "Mining request was cancelled");
            }
        });
        return future;
    }

    public static void cancelAll(MinecraftServer server, String reason) {
        if (server == null) return;
        Runnable cancel = () -> new ArrayList<>(SESSIONS.values()).stream()
                .filter(session -> session.server == server)
                .forEach(session -> finish(session, MiningResult.failure(
                        MiningResult.Status.CANCELLED, session.target, reason)));
        runOnServer(server, cancel);
    }

    public static boolean isMining(UUID botId) {
        return botId != null && SESSIONS.containsKey(botId);
    }

    static int adaptiveDeadlineTicks(float destroyProgress) {
        if (!Float.isFinite(destroyProgress) || destroyProgress <= 0.0F) return -1;
        long expectedTicks = (long) Math.ceil(1.0D / destroyProgress);
        long deadline = expectedTicks * 2L + DEADLINE_MARGIN_TICKS;
        return (int) Math.max(MIN_DEADLINE_TICKS, Math.min(MAX_DEADLINE_TICKS, deadline));
    }

    static boolean generationMatches(long activeGeneration, long callbackGeneration) {
        return activeGeneration == callbackGeneration;
    }

    static int advanceActiveTicks(int activeTicks, boolean pausedForFood) {
        return pausedForFood ? activeTicks : activeTicks + 1;
    }

    static boolean deadlineExceeded(int activeTicks, int deadlineTicks) {
        return activeTicks > deadlineTicks;
    }

    private static void beginSafely(MinecraftServer server, UUID botId, BlockPos target, long generation,
                                    CompletableFuture<MiningResult> future) {
        try {
            beginOnServer(server, botId, target, generation, future);
        } catch (Throwable throwable) {
            LOGGER.error("Mining {} failed to initialize for {} at {}", generation, botId, target, throwable);
            MiningSession session = SESSIONS.get(botId);
            MiningResult failure = MiningResult.failure(
                    MiningResult.Status.FAILED, target, "Mining initialization failed: " + throwable.getMessage());
            if (session != null && generationMatches(session.generation, generation)) finish(session, failure);
            else future.complete(failure);
        }
    }

    private static void beginOnServer(MinecraftServer server, UUID botId, BlockPos target, long generation,
                                      CompletableFuture<MiningResult> future) {
        if (future.isDone()) return;

        MiningSession previous = SESSIONS.get(botId);
        if (previous != null) {
            finish(previous, MiningResult.failure(
                    MiningResult.Status.CANCELLED, previous.target, "Replaced by a newer mining request"));
        }

        ServerPlayer player = server.getPlayerList().getPlayer(botId);
        MiningResult validationFailure = validateStart(player, target);
        if (validationFailure != null) {
            future.complete(validationFailure);
            return;
        }

        if (!(player instanceof ServerPlayerInterface playerInterface)) {
            future.complete(MiningResult.failure(MiningResult.Status.PLAYER_UNAVAILABLE, target,
                    "Carpet action pack is unavailable"));
            return;
        }

        ServerLevel level = player.level();
        BlockState initialState = level.getBlockState(target);
        ItemStack bestTool = ToolSelector.selectBestToolForBlock(player, initialState);
        switchToTool(player, bestTool);
        float destroyProgress = initialState.getDestroyProgress(player, level, target);
        int deadlineTicks = adaptiveDeadlineTicks(destroyProgress);
        if (deadlineTicks < 0) {
            future.complete(MiningResult.failure(
                    MiningResult.Status.UNBREAKABLE, target, "Target block cannot be mined"));
            return;
        }

        MiningSession session = new MiningSession(botId, server, level.dimension(), target,
                initialState.getBlock(), generation, deadlineTicks, future);
        SESSIONS.put(botId, session);
        NavigationService.suspend(botId, SuspensionReason.MINING);
        if (FoodConsumptionTool.isConsumptionInProgress(botId)) {
            session.pausedForFood = true;
            playerInterface.getActionPack().start(EntityPlayerActionPack.ActionType.ATTACK, null);
        } else {
            startAttacking(session, player, playerInterface.getActionPack());
        }
        LOGGER.debug("Mining {} started for {} at {} with a {} tick deadline",
                generation, botId, target, deadlineTicks);
    }

    private static MiningResult validateStart(ServerPlayer player, BlockPos target) {
        if (player == null || !player.isAlive() || player.hasDisconnected()) {
            return MiningResult.failure(
                    MiningResult.Status.PLAYER_UNAVAILABLE, target, "Player is unavailable");
        }
        ServerLevel level = player.level();
        if (!level.isLoaded(target)) {
            return MiningResult.failure(
                    MiningResult.Status.INVALID_TARGET, target, "Target block is not loaded");
        }
        if (player.position().distanceTo(Vec3.atCenterOf(target)) > MAX_MINING_DISTANCE) {
            return MiningResult.failure(
                    MiningResult.Status.OUT_OF_RANGE, target, "Target is more than five blocks away");
        }
        if (level.getBlockState(target).isAir()) {
            return MiningResult.failure(
                    MiningResult.Status.INVALID_TARGET, target, "Target is already air");
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (MiningSession session : new ArrayList<>(SESSIONS.values())) {
            if (session.server != server || SESSIONS.get(session.botId) != session) continue;
            try {
                if (session.future.isDone()) {
                    finish(session, null);
                    continue;
                }

                ServerPlayer player = server.getPlayerList().getPlayer(session.botId);
                if (player == null || !player.isAlive() || player.hasDisconnected()) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.PLAYER_UNAVAILABLE, session.target, "Player became unavailable"));
                    continue;
                }
                if (player.level().dimension() != session.dimension) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.CANCELLED, session.target, "Player changed dimension"));
                    continue;
                }

                ServerLevel level = player.level();
                if (!level.isLoaded(session.target)) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.INVALID_TARGET, session.target, "Target block became unloaded"));
                    continue;
                }
                if (player.position().distanceTo(Vec3.atCenterOf(session.target)) > MAX_MINING_DISTANCE) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.OUT_OF_RANGE, session.target, "Player moved out of mining range"));
                    continue;
                }

                BlockState currentState = level.getBlockState(session.target);
                if (currentState.isAir()) {
                    finish(session, MiningResult.success(session.target));
                    continue;
                }
                if (currentState.getBlock() != session.initialBlock) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.TARGET_CHANGED, session.target, "Target block changed while mining"));
                    continue;
                }
                if (!(player instanceof ServerPlayerInterface playerInterface)) {
                    finish(session, MiningResult.failure(MiningResult.Status.PLAYER_UNAVAILABLE, session.target,
                            "Carpet action pack became unavailable"));
                    continue;
                }

                EntityPlayerActionPack actions = playerInterface.getActionPack();
                if (FoodConsumptionTool.isConsumptionInProgress(session.botId)) {
                    if (!session.pausedForFood) {
                        actions.start(EntityPlayerActionPack.ActionType.ATTACK, null);
                        session.pausedForFood = true;
                    }
                    continue;
                }
                if (session.pausedForFood) {
                    startAttacking(session, player, actions);
                    session.pausedForFood = false;
                }

                session.activeTicks = advanceActiveTicks(session.activeTicks, false);
                if (deadlineExceeded(session.activeTicks, session.deadlineTicks)) {
                    finish(session, MiningResult.failure(
                            MiningResult.Status.TIMED_OUT, session.target, "Mining exceeded its adaptive deadline"));
                }
            } catch (Throwable throwable) {
                LOGGER.error("Mining {} failed while ticking", session.generation, throwable);
                finish(session, MiningResult.failure(MiningResult.Status.FAILED, session.target,
                        "Mining failed: " + throwable.getMessage()));
            }
        }
    }

    private static void startAttacking(MiningSession session, ServerPlayer player,
                                       EntityPlayerActionPack actions) {
        LookController.faceBlock(player, session.target);
        actions.start(EntityPlayerActionPack.ActionType.ATTACK,
                EntityPlayerActionPack.Action.continuous());
    }

    private static void cancel(MinecraftServer server, UUID botId, long generation, String reason) {
        Runnable cancel = () -> {
            MiningSession session = SESSIONS.get(botId);
            if (session != null && generationMatches(session.generation, generation)) {
                finish(session, MiningResult.failure(
                        MiningResult.Status.CANCELLED, session.target, reason));
            }
        };
        runOnServer(server, cancel);
    }

    private static void finish(MiningSession session, MiningResult result) {
        if (!SESSIONS.remove(session.botId, session)) return;
        try {
            ServerPlayer player = session.server.getPlayerList().getPlayer(session.botId);
            if (player instanceof ServerPlayerInterface playerInterface) {
                playerInterface.getActionPack().start(EntityPlayerActionPack.ActionType.ATTACK, null);
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Could not release mining attack for {}", session.botId, throwable);
        }
        try {
            NavigationService.resume(session.botId, SuspensionReason.MINING);
        } catch (Throwable throwable) {
            LOGGER.warn("Could not resume navigation after mining for {}", session.botId, throwable);
        } finally {
            if (result != null && !session.future.isDone()) session.future.complete(result);
        }
        LOGGER.debug("Mining {} completed for {}: {}", session.generation, session.botId,
                result == null ? "externally completed" : result.status());
    }

    private static void runOnServer(MinecraftServer server, Runnable task) {
        try {
            if (server.isSameThread() || !server.isRunning()) task.run();
            else server.execute(task);
        } catch (Throwable throwable) {
            LOGGER.error("Could not schedule mining cleanup", throwable);
        }
    }

    private static void switchToTool(ServerPlayer bot, ItemStack tool) {
        for (int slot = 0; slot < 9; slot++) {
            if (bot.getInventory().getItem(slot) == tool) {
                bot.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private static final class MiningSession {
        final UUID botId;
        final MinecraftServer server;
        final ResourceKey<Level> dimension;
        final BlockPos target;
        final Block initialBlock;
        final long generation;
        final int deadlineTicks;
        final CompletableFuture<MiningResult> future;
        int activeTicks;
        boolean pausedForFood;

        MiningSession(UUID botId, MinecraftServer server, ResourceKey<Level> dimension, BlockPos target,
                      Block initialBlock, long generation, int deadlineTicks,
                      CompletableFuture<MiningResult> future) {
            this.botId = Objects.requireNonNull(botId);
            this.server = Objects.requireNonNull(server);
            this.dimension = Objects.requireNonNull(dimension);
            this.target = Objects.requireNonNull(target);
            this.initialBlock = Objects.requireNonNull(initialBlock);
            this.generation = generation;
            this.deadlineTicks = deadlineTicks;
            this.future = Objects.requireNonNull(future);
        }
    }
}
