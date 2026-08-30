package net.shasankp000.GameAI.autonomous;

import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.AIPlayer;
import net.shasankp000.GameAI.companion.BotStance;
import net.shasankp000.GameAI.companion.CompanionController;
import net.shasankp000.PathFinding.NavigationOptions;
import net.shasankp000.PathFinding.NavigationResult;
import net.shasankp000.PathFinding.NavigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final ConcurrentHashMap<java.util.UUID, NearbyBedSleepController> RL_CONTROLLERS =
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
            ServerPlayer bot = resolveBot();
            if (bot == null) return false;

            ServerLevel level = bot.level();
            if (!isNight(level)) {
                resetForDay();
                return false;
            }
            if (!isEligible(bot)) return false;

            long now = level.getGameTime();
            synchronized (this) {
                return lastAttemptGameTime == Long.MIN_VALUE
                        || now - lastAttemptGameTime >= RETRY_COOLDOWN_TICKS;
            }
        }, false);
        return Boolean.TRUE.equals(eligible);
    }

    boolean isBotSleeping() {
        return Boolean.TRUE.equals(callOnServer(() -> {
            ServerPlayer bot = resolveBot();
            return bot != null && bot.isSleeping();
        }, false));
    }

    /** Execute one RL-selected sleep attempt for the supplied bot. */
    public static boolean attemptFromRl(ServerPlayer bot) {
        if (bot == null) return false;
        AutonomousGoalEngine engine = AutonomousManager.getInstance()
                .getEngine(bot.getName().getString());
        if (engine != null && engine.isPlayerControlled()) return false;
        NearbyBedSleepController controller = RL_CONTROLLERS.computeIfAbsent(
                bot.getUUID(), ignored -> new NearbyBedSleepController(bot.getName().getString(),
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
            ServerPlayer bot = resolveBot();
            if (bot == null || !isEligible(bot)) return null;
            return new NavigationContext(bot, bot.blockPosition().distManhattan(target.standPos()) <= 1);
        }, null);
        if (navigation == null) return false;

        if (!navigation.alreadyThere()) {
            try {
                NavigationResult navigationResult = NavigationService.navigate(navigation.bot(), target.standPos(),
                        NavigationOptions.of(false)).get(5, TimeUnit.MINUTES);
                LOGGER.debug("[sleep] Navigation to bed {} returned: {}", target.headPos(), navigationResult.status());
                if (!navigationResult.reached()) return false;
            } catch (Exception e) {
                LOGGER.warn("[sleep] Navigation to bed {} failed: {}", target.headPos(), e.getMessage());
                return false;
            }
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
        ServerPlayer bot = resolveBot();
        if (bot == null || !isEligible(bot)) return null;

        ServerLevel level = bot.level();
        synchronized (this) {
            if (stopped) return null;
            lastAttemptGameTime = level.getGameTime();
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

    private BedTarget findNearestBed(ServerPlayer bot) {
        ServerLevel level = bot.level();
        BlockPos origin = bot.blockPosition();
        BedTarget nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > SEARCH_RADIUS_SQUARED) continue;

                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

                    BlockState state = level.getBlockState(pos);
                    BlockPos footPos = state.getBlock() instanceof BedBlock
                            ? pos.relative(state.getValue(BedBlock.FACING).getOpposite())
                            : pos;
                    if (!(state.getBlock() instanceof BedBlock)
                            || state.getValue(BedBlock.PART) != BedPart.HEAD
                            || state.getValue(BedBlock.OCCUPIED)
                            || !hasHeadroom(level, pos)
                            || !hasHeadroom(level, footPos)
                            || isFailed(pos)
                            || !bedRuleAllowsSleep(level, pos)) {
                        continue;
                    }

                    BlockPos standPos = findStandingPosition(level, bot, pos, state);
                    if (standPos == null) continue;

                    double exactDistance = origin.distSqr(pos);
                    if (exactDistance < nearestDistance) {
                        nearestDistance = exactDistance;
                        nearest = new BedTarget(pos.immutable(), standPos.immutable(), false);
                    }
                }
            }
        }
        return nearest;
    }

    private BedTarget placeInventoryBed(ServerPlayer bot) {
        int bedSlot = findBedSlot(bot.getInventory());
        if (bedSlot < 0) return null;

        PlacementSite site = findPlacementSite(bot);
        if (site == null) return null;

        Inventory inventory = bot.getInventory();
        int previousSelected = inventory.getSelectedSlot();
        int hotbarSlot = bedSlot < 9 ? bedSlot : findHotbarSlot(inventory, previousSelected);
        boolean swapped = bedSlot != hotbarSlot;

        if (swapped) swapSlots(inventory, bedSlot, hotbarSlot);
        inventory.setSelectedSlot(hotbarSlot);

        try {
            ItemStack handStack = bot.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(handStack.getItem() instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof BedBlock)) {
                return null;
            }

            Vec3 hitLocation = Vec3.atCenterOf(site.supportPos()).add(0.0, 0.5, 0.0);
            BlockHitResult hit = new BlockHitResult(
                    hitLocation, Direction.UP, site.supportPos(), false);
            bot.gameMode.useItemOn(bot, bot.level(), handStack, InteractionHand.MAIN_HAND, hit);

            BlockState placedState = bot.level().getBlockState(site.footPos());
            BlockPos headPos = normalizeBedHead(bot.level(), site.footPos(), placedState);
            if (headPos == null) {
                LOGGER.warn("[sleep] Bed placement for '{}' did not create a complete bed", botName);
                return null;
            }

            BlockState headState = bot.level().getBlockState(headPos);
            BlockPos standPos = findStandingPosition(bot.level(), bot, headPos, headState);
            return standPos == null ? null
                    : new BedTarget(headPos.immutable(), standPos.immutable(), true);
        } finally {
            if (swapped) swapSlots(inventory, bedSlot, hotbarSlot);
            inventory.setSelectedSlot(previousSelected);
            inventory.setChanged();
            bot.containerMenu.broadcastChanges();
        }
    }

    private PlacementSite findPlacementSite(ServerPlayer bot) {
        ServerLevel level = bot.level();
        BlockPos origin = bot.blockPosition();
        Direction facing = bot.getDirection();
        List<BlockPos> candidates = new ArrayList<>();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -PLACEMENT_RADIUS; dx <= PLACEMENT_RADIUS; dx++) {
                for (int dz = -PLACEMENT_RADIUS; dz <= PLACEMENT_RADIUS; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    candidates.add(origin.offset(dx, dy, dz));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));

        for (BlockPos foot : candidates) {
            BlockPos head = foot.relative(facing);
            if (foot.equals(origin) || head.equals(origin)) continue;
            if (!validBedCell(level, foot) || !validBedCell(level, head)) continue;
            if (!hasSolidSupport(level, foot.below()) || !hasSolidSupport(level, head.below())) continue;
            if (!hasHeadroom(level, foot) || !hasHeadroom(level, head)) continue;
            if (!level.getWorldBorder().isWithinBounds(foot)
                    || !level.getWorldBorder().isWithinBounds(head)) continue;
            if (foot.distToCenterSqr(bot.position()) > 25.0) continue;
            return new PlacementSite(foot.immutable(), foot.below().immutable());
        }
        return null;
    }

    private BlockPos findStandingPosition(ServerLevel level, ServerPlayer bot,
                                          BlockPos headPos, BlockState headState) {
        Direction facing = headState.getValue(BedBlock.FACING);
        BlockPos footPos = headPos.relative(facing.getOpposite());
        BlockPos origin = bot.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos bedHalf : List.of(headPos, footPos)) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0 && dy == 0) continue;
                        candidates.add(bedHalf.offset(dx, dy, dz));
                    }
                }
            }
        }

        return candidates.stream()
                .distinct()
                .filter(pos -> isStandable(level, pos))
                .min(Comparator.comparingDouble(origin::distSqr))
                .orElse(null);
    }

    private SleepResult startSleeping(BedTarget target) {
        ServerPlayer bot = resolveBot();
        if (bot == null || !isEligible(bot)) return SleepResult.CANCELLED;

        ServerLevel level = bot.level();
        BlockState state = level.getBlockState(target.headPos());
        if (!(state.getBlock() instanceof BedBlock)
                || state.getValue(BedBlock.PART) != BedPart.HEAD
                || state.getValue(BedBlock.OCCUPIED)
                || !bedRuleAllowsSleep(level, target.headPos())) {
            return SleepResult.FAILED;
        }

        Either<Player.BedSleepingProblem, net.minecraft.util.Unit> result =
                bot.startSleepInBed(target.headPos());
        if (result.right().isPresent()) return SleepResult.SUCCESS;

        result.left().ifPresent(problem -> LOGGER.warn(
                "[sleep] Bot '{}' could not sleep at {}: {}",
                botName, target.headPos(), problem.message().getString()));
        return SleepResult.FAILED;
    }

    private boolean isEligible(ServerPlayer bot) {
        if (stopped || playerControlled.getAsBoolean() || !bot.isAlive() || bot.isSpectator()
                || bot.isSleeping()
                || CompanionController.getInstance().getStance(botName) != BotStance.WANDER) {
            return false;
        }
        ServerLevel level = bot.level();
        return isNight(level) && bedRuleAllowsSleep(level, bot.blockPosition());
    }

    private static boolean isNight(ServerLevel level) {
        long timeOfDay = Math.floorMod(level.getDefaultClockTime(), 24_000L);
        return timeOfDay >= 12_000L;
    }

    private static boolean bedRuleAllowsSleep(ServerLevel level, BlockPos pos) {
        BedRule rule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
        return !rule.explodes() && rule.canSleep(level);
    }

    private static boolean validBedCell(ServerLevel level, BlockPos pos) {
        return level.isInsideBuildHeight(pos)
                && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).canBeReplaced();
    }

    private static boolean hasSolidSupport(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean hasHeadroom(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.isInsideBuildHeight(above)
                && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.isInsideBuildHeight(pos) || !level.isInsideBuildHeight(pos.above())) return false;
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return body.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty()
                && hasSolidSupport(level, pos.below());
    }

    private static BlockPos normalizeBedHead(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) return null;
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? pos
                : pos.relative(state.getValue(BedBlock.FACING));
        BlockState headState = level.getBlockState(head);
        return headState.getBlock() instanceof BedBlock
                && headState.getValue(BedBlock.PART) == BedPart.HEAD ? head : null;
    }

    private static int findBedSlot(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof BedBlock) {
                return slot;
            }
        }
        return -1;
    }

    private static int findHotbarSlot(Inventory inventory, int fallback) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).isEmpty()) return slot;
        }
        return fallback;
    }

    private static void swapSlots(Inventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getItem(first);
        ItemStack secondStack = inventory.getItem(second);
        inventory.setItem(first, secondStack);
        inventory.setItem(second, firstStack);
    }

    private synchronized boolean isFailed(BlockPos pos) {
        return failedBeds.contains(pos);
    }

    private synchronized void markFailed(BlockPos pos) {
        failedBeds.add(pos.immutable());
    }

    private synchronized void resetForDay() {
        failedBeds.clear();
        lastAttemptGameTime = Long.MIN_VALUE;
    }

    private ServerPlayer resolveBot() {
        MinecraftServer server = AIPlayer.serverInstance;
        return server == null ? null : server.getPlayerList().getPlayerByName(botName);
    }

    private <T> T callOnServer(ThrowingSupplier<T> task, T fallback) {
        MinecraftServer server = AIPlayer.serverInstance;
        if (server == null || stopped) return fallback;
        try {
            if (server.isSameThread()) return task.get();

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
    private record NavigationContext(ServerPlayer bot, boolean alreadyThere) {}
    private enum SleepResult { SUCCESS, FAILED, CANCELLED }
}
