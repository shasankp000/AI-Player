package net.shasankp000.GameAI.autonomous;

import com.mojang.datafixers.util.Either;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.shasankp000.AIPlayer;
import net.shasankp000.GameAI.companion.BotStance;
import net.shasankp000.GameAI.companion.CompanionController;
import net.shasankp000.PathFinding.GoTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Per-bot deterministic survival controller for finding, placing, and using beds.
 * Minecraft world and inventory access is marshalled onto the server thread.
 */
public final class NearbyBedSleepController {

    private static final Logger LOGGER = LoggerFactory.getLogger("nearby-bed-sleep");
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_RADIUS_SQUARED = SEARCH_RADIUS * SEARCH_RADIUS;
    private static final int PLACEMENT_RADIUS = 4;
    private static final long RETRY_COOLDOWN_TICKS = 20L * 30L;
    private static final long SERVER_CALL_TIMEOUT_SECONDS = 15L;
    private static final ConcurrentHashMap<UUID, NearbyBedSleepController> RL_CONTROLLERS =
            new ConcurrentHashMap<>();

    private final String botName;
    private final BooleanSupplier playerControlled;
    private final Set<BlockPos> failedBeds = new HashSet<>();

    private long lastAttemptGameTime = Long.MIN_VALUE;
    private volatile boolean stopped;

    NearbyBedSleepController(String botName, BooleanSupplier playerControlled) {
        this.botName = botName;
        this.playerControlled = playerControlled;
    }

    boolean shouldQueueAttempt() {
        Boolean eligible = callOnServer(() -> {
            ServerPlayerEntity bot = resolveBot();
            if (bot == null) return false;

            ServerWorld level = bot.getWorld();
            if (!isNight(level)) {
                resetForDay();
                return false;
            }
            if (!isEligible(bot)) return false;

            long now = level.getTime();
            synchronized (this) {
                return lastAttemptGameTime == Long.MIN_VALUE
                        || now - lastAttemptGameTime >= RETRY_COOLDOWN_TICKS;
            }
        }, false);
        return Boolean.TRUE.equals(eligible);
    }

    boolean isBotSleeping() {
        return Boolean.TRUE.equals(callOnServer(() -> {
            ServerPlayerEntity bot = resolveBot();
            return bot != null && bot.isSleeping();
        }, false));
    }

    /** Execute one RL-selected sleep attempt for the supplied bot. */
    public static boolean attemptFromRl(ServerPlayerEntity bot) {
        if (bot == null) return false;
        AutonomousGoalEngine engine = AutonomousManager.getInstance()
                .getEngine(bot.getName().getString());
        if (engine != null && engine.isPlayerControlled()) return false;
        NearbyBedSleepController controller = RL_CONTROLLERS.computeIfAbsent(
                bot.getUuid(), ignored -> new NearbyBedSleepController(bot.getName().getString(),
                        () -> {
                            AutonomousGoalEngine activeEngine = AutonomousManager.getInstance()
                                    .getEngine(bot.getName().getString());
                            return activeEngine != null && activeEngine.isPlayerControlled();
                        }));
        return controller.attemptSleep();
    }

    boolean attemptSleep() {
        BedTarget target = callOnServer(this::prepareTarget, null);
        if (target == null || Thread.currentThread().isInterrupted()) return false;

        NavigationContext navigation = callOnServer(() -> {
            ServerPlayerEntity bot = resolveBot();
            if (bot == null || !isEligible(bot)) return null;
            ServerCommandSource source = bot.getCommandSource()
                    .withSilent()
                    .withMaxLevel(4);
            return new NavigationContext(source,
                    bot.getBlockPos().getManhattanDistance(target.standPos()) <= 1);
        }, null);
        if (navigation == null) return false;

        if (!navigation.alreadyThere()) {
            String navigationResult = GoTo.goTo(navigation.source(),
                    target.standPos().getX(), target.standPos().getY(), target.standPos().getZ(), false);
            LOGGER.debug("[sleep] Navigation to bed {} returned: {}", target.headPos(), navigationResult);
        }

        SleepResult result = callOnServer(() -> startSleeping(target), SleepResult.FAILED);
        if (result == SleepResult.SUCCESS) {
            LOGGER.info("[sleep] Bot '{}' is sleeping in {}bed at {}",
                    botName, target.placedByBot() ? "a newly placed " : "", target.headPos());
        } else if (result == SleepResult.FAILED && !stopped) {
            markFailed(target.headPos());
        }
        return result == SleepResult.SUCCESS;
    }

    synchronized void shutdown() {
        stopped = true;
        failedBeds.clear();
    }

    private BedTarget prepareTarget() {
        ServerPlayerEntity bot = resolveBot();
        if (bot == null || !isEligible(bot)) return null;

        ServerWorld level = bot.getWorld();
        synchronized (this) {
            if (stopped) return null;
            lastAttemptGameTime = level.getTime();
        }

        BedTarget existing = findNearestBed(bot);
        if (existing != null) {
            LOGGER.info("[sleep] Bot '{}' found a bed at {}", botName, existing.headPos());
            return existing;
        }

        BedTarget placed = placeInventoryBed(bot);
        if (placed != null) {
            LOGGER.info("[sleep] Bot '{}' placed a carried bed at {}", botName, placed.headPos());
        } else {
            LOGGER.debug("[sleep] Bot '{}' found no usable bed or placement location", botName);
        }
        return placed;
    }

    private BedTarget findNearestBed(ServerPlayerEntity bot) {
        ServerWorld level = bot.getWorld();
        BlockPos origin = bot.getBlockPos();
        BedTarget nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > SEARCH_RADIUS_SQUARED) continue;

                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!level.isInBuildLimit(pos) || !isChunkLoaded(level, pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    BlockPos footPos = state.getBlock() instanceof BedBlock
                            ? pos.offset(state.get(BedBlock.FACING).getOpposite())
                            : pos;
                    if (!(state.getBlock() instanceof BedBlock)
                            || state.get(BedBlock.PART) != BedPart.HEAD
                            || state.get(BedBlock.OCCUPIED)
                            || !hasHeadroom(level, pos)
                            || !hasHeadroom(level, footPos)
                            || isFailed(pos)
                            || !bedRuleAllowsSleep(level, pos)) {
                        continue;
                    }

                    BlockPos standPos = findStandingPosition(level, bot, pos, state);
                    if (standPos == null) continue;

                    double exactDistance = origin.getSquaredDistance(pos);
                    if (exactDistance < nearestDistance) {
                        nearestDistance = exactDistance;
                        nearest = new BedTarget(pos.toImmutable(), standPos.toImmutable(), false);
                    }
                }
            }
        }
        return nearest;
    }

    private BedTarget placeInventoryBed(ServerPlayerEntity bot) {
        int bedSlot = findBedSlot(bot.getInventory());
        if (bedSlot < 0) return null;

        PlacementSite site = findPlacementSite(bot);
        if (site == null) return null;

        PlayerInventory inventory = bot.getInventory();
        int previousSelected = inventory.getSelectedSlot();
        int hotbarSlot = bedSlot < 9 ? bedSlot : findHotbarSlot(inventory, previousSelected);
        boolean swapped = bedSlot != hotbarSlot;

        if (swapped) swapSlots(inventory, bedSlot, hotbarSlot);
        inventory.setSelectedSlot(hotbarSlot);

        try {
            ItemStack handStack = bot.getStackInHand(Hand.MAIN_HAND);
            if (!(handStack.getItem() instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof BedBlock)) {
                return null;
            }

            Vec3d hitLocation = Vec3d.ofCenter(site.supportPos()).add(0.0, 0.5, 0.0);
            BlockHitResult hit = new BlockHitResult(
                    hitLocation, Direction.UP, site.supportPos(), false);
            bot.interactionManager.interactBlock(bot, bot.getWorld(), handStack, Hand.MAIN_HAND, hit);

            BlockState placedState = bot.getWorld().getBlockState(site.footPos());
            BlockPos headPos = normalizeBedHead(bot.getWorld(), site.footPos(), placedState);
            if (headPos == null) {
                LOGGER.warn("[sleep] Bed placement for '{}' did not create a complete bed", botName);
                return null;
            }

            BlockState headState = bot.getWorld().getBlockState(headPos);
            BlockPos standPos = findStandingPosition(bot.getWorld(), bot, headPos, headState);
            return standPos == null ? null
                    : new BedTarget(headPos.toImmutable(), standPos.toImmutable(), true);
        } finally {
            if (swapped) swapSlots(inventory, bedSlot, hotbarSlot);
            inventory.setSelectedSlot(previousSelected);
            inventory.markDirty();
            bot.playerScreenHandler.sendContentUpdates();
        }
    }

    private PlacementSite findPlacementSite(ServerPlayerEntity bot) {
        ServerWorld level = bot.getWorld();
        BlockPos origin = bot.getBlockPos();
        Direction facing = bot.getFacing();
        List<BlockPos> candidates = new ArrayList<>();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -PLACEMENT_RADIUS; dx <= PLACEMENT_RADIUS; dx++) {
                for (int dz = -PLACEMENT_RADIUS; dz <= PLACEMENT_RADIUS; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    candidates.add(origin.add(dx, dy, dz));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> origin.getSquaredDistance(pos)));

        for (BlockPos foot : candidates) {
            BlockPos head = foot.offset(facing);
            if (foot.equals(origin) || head.equals(origin)) continue;
            if (!validBedCell(level, foot) || !validBedCell(level, head)) continue;
            if (!hasSolidSupport(level, foot.down()) || !hasSolidSupport(level, head.down())) continue;
            if (!hasHeadroom(level, foot) || !hasHeadroom(level, head)) continue;
            if (!level.getWorldBorder().contains(foot)
                    || !level.getWorldBorder().contains(head)) continue;
            if (foot.getSquaredDistance(bot.getPos()) > 25.0) continue;
            return new PlacementSite(foot.toImmutable(), foot.down().toImmutable());
        }
        return null;
    }

    private BlockPos findStandingPosition(ServerWorld level, ServerPlayerEntity bot,
                                          BlockPos headPos, BlockState headState) {
        Direction facing = headState.get(BedBlock.FACING);
        BlockPos footPos = headPos.offset(facing.getOpposite());
        BlockPos origin = bot.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos bedHalf : List.of(headPos, footPos)) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        candidates.add(bedHalf.add(dx, dy, dz));
                    }
                }
            }
        }

        return candidates.stream()
                .distinct()
                .filter(pos -> isStandable(level, pos))
                .min(Comparator.comparingDouble(pos -> origin.getSquaredDistance(pos)))
                .orElse(null);
    }

    private SleepResult startSleeping(BedTarget target) {
        ServerPlayerEntity bot = resolveBot();
        if (bot == null || !isEligible(bot)) return SleepResult.CANCELLED;

        ServerWorld level = bot.getWorld();
        BlockState state = level.getBlockState(target.headPos());
        if (!(state.getBlock() instanceof BedBlock)
                || state.get(BedBlock.PART) != BedPart.HEAD
                || state.get(BedBlock.OCCUPIED)
                || !bedRuleAllowsSleep(level, target.headPos())) {
            return SleepResult.FAILED;
        }

        Either<PlayerEntity.SleepFailureReason, net.minecraft.util.Unit> result =
                bot.trySleep(target.headPos());
        if (result.right().isPresent()) return SleepResult.SUCCESS;

        result.left().ifPresent(problem -> LOGGER.warn(
                "[sleep] Bot '{}' could not sleep at {}: {}",
                botName, target.headPos(), problem.getMessage().getString()));
        return SleepResult.FAILED;
    }

    private boolean isEligible(ServerPlayerEntity bot) {
        if (stopped || playerControlled.getAsBoolean() || !bot.isAlive() || bot.isSpectator()
                || bot.isSleeping()
                || CompanionController.getInstance().getStance(botName) != BotStance.WANDER) {
            return false;
        }
        ServerWorld level = bot.getWorld();
        return isNight(level) && bedRuleAllowsSleep(level, bot.getBlockPos());
    }

    private static boolean isNight(ServerWorld level) {
        long timeOfDay = Math.floorMod(level.getTimeOfDay(), 24_000L);
        return timeOfDay >= 12_000L;
    }

    private static boolean bedRuleAllowsSleep(ServerWorld level, BlockPos pos) {
        // 1.21.8 yarn: the dimension's bed rules govern sleeping — beds explode in the
        // Nether/End (bedWorks() == false), everywhere else they work.
        return level.getDimension().bedWorks();
    }

    private static boolean isChunkLoaded(ServerWorld level, BlockPos pos) {
        return level.getChunk(
                ChunkSectionPos.getSectionCoord(pos.getX()),
                ChunkSectionPos.getSectionCoord(pos.getZ()),
                ChunkStatus.FULL, false) != null;
    }

    private static boolean validBedCell(ServerWorld level, BlockPos pos) {
        return level.isInBuildLimit(pos)
                && isChunkLoaded(level, pos)
                && level.getBlockState(pos).isReplaceable();
    }

    private static boolean hasSolidSupport(ServerWorld level, BlockPos pos) {
        return level.getBlockState(pos).isSideSolidFullSquare(level, pos, Direction.UP);
    }

    private static boolean hasHeadroom(ServerWorld level, BlockPos pos) {
        BlockPos above = pos.up();
        return level.isInBuildLimit(above)
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static boolean isStandable(ServerWorld level, BlockPos pos) {
        if (!level.isInBuildLimit(pos) || !level.isInBuildLimit(pos.up())) return false;
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.up());
        return body.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.up()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.up()).isEmpty()
                && hasSolidSupport(level, pos.down());
    }

    private static BlockPos normalizeBedHead(ServerWorld level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return null;
        BlockPos head = state.get(BedBlock.PART) == BedPart.HEAD
                ? pos
                : pos.offset(state.get(BedBlock.FACING));
        BlockState headState = level.getBlockState(head);
        return headState.getBlock() instanceof BedBlock
                && headState.get(BedBlock.PART) == BedPart.HEAD ? head : null;
    }

    private static int findBedSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof BedBlock) {
                return slot;
            }
        }
        return -1;
    }

    private static int findHotbarSlot(PlayerInventory inventory, int fallback) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isEmpty()) return slot;
        }
        return fallback;
    }

    private static void swapSlots(PlayerInventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getStack(first);
        ItemStack secondStack = inventory.getStack(second);
        inventory.setStack(first, secondStack);
        inventory.setStack(second, firstStack);
    }

    private synchronized boolean isFailed(BlockPos pos) {
        return failedBeds.contains(pos);
    }

    private synchronized void markFailed(BlockPos pos) {
        failedBeds.add(pos.toImmutable());
    }

    private synchronized void resetForDay() {
        failedBeds.clear();
        lastAttemptGameTime = Long.MIN_VALUE;
    }

    private ServerPlayerEntity resolveBot() {
        MinecraftServer server = AIPlayer.serverInstance;
        return server == null ? null : server.getPlayerManager().getPlayer(botName);
    }

    private <T> T callOnServer(ThrowingSupplier<T> task, T fallback) {
        MinecraftServer server = AIPlayer.serverInstance;
        if (server == null || stopped) return fallback;
        try {
            if (server.isOnThread()) return task.get();

            CompletableFuture<T> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            return future.get(SERVER_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception e) {
            LOGGER.error("[sleep] Server-thread operation failed for '{}': {}", botName, e.getMessage(), e);
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private record BedTarget(BlockPos headPos, BlockPos standPos, boolean placedByBot) {}
    private record PlacementSite(BlockPos footPos, BlockPos supportPos) {}
    private record NavigationContext(ServerCommandSource source, boolean alreadyThere) {}
    private enum SleepResult { SUCCESS, FAILED, CANCELLED }
}
