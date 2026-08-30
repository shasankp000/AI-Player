package net.shasankp000.PlayerUtils;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.shasankp000.PathFinding.NavigationService;
import net.shasankp000.PathFinding.SuspensionReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Inventory-aware food consumption invoked only when the RL policy selects USE_ITEM. */
public final class FoodConsumptionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger("rl-food-consumption");
    private static final int INVENTORY_SEARCH_SIZE = 36;
    private static final long SERVER_CALL_TIMEOUT_SECONDS = 5L;
    private static final long CONSUMPTION_TIMEOUT_MILLIS = 6_000L;

    private static final Set<String> UNSAFE_FOODS = Set.of(
            "minecraft:chorus_fruit",
            "minecraft:poisonous_potato",
            "minecraft:pufferfish",
            "minecraft:chicken",
            "minecraft:rotten_flesh",
            "minecraft:spider_eye",
            "minecraft:suspicious_stew"
    );

    private static final Set<String> RARE_UTILITY_FOODS = Set.of(
            "minecraft:enchanted_golden_apple",
            "minecraft:golden_apple"
    );

    private static final ConcurrentHashMap<UUID, Long> PAUSE_STARTED_AT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> TOTAL_PAUSED_MILLIS = new ConcurrentHashMap<>();

    private FoodConsumptionTool() {}

    /** Returns true when at least one safe food can currently be eaten. */
    public static boolean hasSafeFood(ServerPlayer bot) {
        return bot != null && findBestFood(bot) != null;
    }

    /**
     * Stops current fake-player inputs, consumes the best safe food with vanilla
     * timing, restores inventory/input state, and reports the observed hunger gain.
     */
    public static ConsumptionResult consumeBestFood(ServerPlayer bot) {
        if (bot == null || !bot.isAlive() || bot.hasDisconnected()) {
            return ConsumptionResult.failure("bot is unavailable");
        }

        MinecraftServer server = bot.createCommandSourceStack().getServer();
        if (server == null || !server.isRunning()) {
            return ConsumptionResult.failure("server is unavailable");
        }
        if (server.isSameThread()) {
            return ConsumptionResult.failure("RL food action cannot block the server thread");
        }

        ConsumptionSession session;
        try {
            session = callOnServer(server, () -> startConsumption(bot));
        } catch (Exception e) {
            LOGGER.warn("Could not start RL food action for '{}': {}", bot.getName().getString(), e.getMessage());
            return ConsumptionResult.failure("could not start food consumption");
        }

        if (session == null) return ConsumptionResult.failure("no safe food is available");

        long deadline = System.currentTimeMillis() + CONSUMPTION_TIMEOUT_MILLIS;
        try {
            while (System.currentTimeMillis() < deadline && server.isRunning()) {
                Thread.sleep(50L);
                ConsumptionStatus status = callOnServer(server,
                        () -> new ConsumptionStatus(bot.isUsingItem(), bot.getFoodData().getFoodLevel()));
                if (!status.usingItem || status.hunger > session.hungerBefore) break;
            }

            return callOnServer(server, () -> finishConsumption(session));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return finishAfterFailure(server, session, "food action was interrupted");
        } catch (Exception e) {
            LOGGER.warn("RL food action failed for '{}': {}", bot.getName().getString(), e.getMessage());
            return finishAfterFailure(server, session, "food action failed");
        }
    }

    public static boolean isConsumptionInProgress(UUID botId) {
        return PAUSE_STARTED_AT.containsKey(botId);
    }

    /** Cumulative pause time used by timed path movement to exclude eating time. */
    public static long getTotalPausedMillis(UUID botId) {
        long completed = TOTAL_PAUSED_MILLIS.getOrDefault(botId, 0L);
        Long started = PAUSE_STARTED_AT.get(botId);
        return started == null ? completed : completed + Math.max(0L, System.currentTimeMillis() - started);
    }

    /** Cooperatively waits between autonomous actions while the RL food action runs. */
    public static void awaitResume(ServerPlayer bot) {
        if (bot == null) return;
        while (isConsumptionInProgress(bot.getUUID()) && bot.isAlive() && !bot.hasDisconnected()) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void reset() {
        PAUSE_STARTED_AT.clear();
        TOTAL_PAUSED_MILLIS.clear();
    }

    private static ConsumptionSession startConsumption(ServerPlayer bot) {
        FoodCandidate food = findBestFood(bot);
        if (food == null) return null;

        UUID botId = bot.getUUID();
        if (PAUSE_STARTED_AT.putIfAbsent(botId, System.currentTimeMillis()) != null) return null;
        NavigationService.suspend(botId, SuspensionReason.EATING);

        PausedActions pausedActions = pauseActions(bot);
        Inventory inventory = bot.getInventory();
        int previousSelectedSlot = inventory.getSelectedSlot();
        int useSlot = food.slot < 9 ? food.slot : previousSelectedSlot;
        boolean swapped = food.slot != useSlot;
        ItemStack displacedStack = swapped ? inventory.getItem(useSlot) : ItemStack.EMPTY;

        if (swapped) swapSlots(inventory, food.slot, useSlot);
        inventory.setSelectedSlot(useSlot);
        syncInventory(bot);

        ConsumptionSession session = new ConsumptionSession(
                bot,
                bot.getFoodData().getFoodLevel(),
                food,
                previousSelectedSlot,
                useSlot,
                swapped,
                displacedStack,
                pausedActions
        );

        ItemStack handStack = bot.getItemInHand(InteractionHand.MAIN_HAND);
        InteractionResult result = bot.gameMode.useItem(
                bot, bot.level(), handStack, InteractionHand.MAIN_HAND);
        if (!result.consumesAction() || !bot.isUsingItem()) {
            restoreSession(session);
            return null;
        }

        LOGGER.info("RL selected food {} for '{}' at hunger {}/20",
                food.itemId, bot.getName().getString(), session.hungerBefore);
        return session;
    }

    private static ConsumptionResult finishConsumption(ConsumptionSession session) {
        ServerPlayer bot = session.bot;
        if (bot.isUsingItem()) bot.stopUsingItem();
        int hungerAfter = bot.getFoodData().getFoodLevel();
        restoreSession(session);

        int gained = Math.max(0, hungerAfter - session.hungerBefore);
        if (gained == 0) {
            return new ConsumptionResult(false, session.hungerBefore, hungerAfter,
                    session.food.itemId, "food consumption produced no hunger gain");
        }
        return new ConsumptionResult(true, session.hungerBefore, hungerAfter,
                session.food.itemId, "ate " + session.food.itemId + " and gained " + gained + " hunger");
    }

    private static ConsumptionResult finishAfterFailure(
            MinecraftServer server, ConsumptionSession session, String message) {
        try {
            return callOnServer(server, () -> {
                if (session.bot.isUsingItem()) session.bot.stopUsingItem();
                int hungerAfter = session.bot.getFoodData().getFoodLevel();
                restoreSession(session);
                return new ConsumptionResult(false, session.hungerBefore, hungerAfter,
                        session.food.itemId, message);
            });
        } catch (Exception ignored) {
            finishPause(session.bot.getUUID());
            return new ConsumptionResult(false, session.hungerBefore,
                    session.bot.getFoodData().getFoodLevel(), session.food.itemId, message);
        }
    }

    private static FoodCandidate findBestFood(ServerPlayer bot) {
        FoodCandidate bestOrdinary = null;
        FoodCandidate bestUtility = null;
        int slots = Math.min(INVENTORY_SEARCH_SIZE, bot.getInventory().getContainerSize());

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            FoodProperties properties = stack.get(DataComponents.FOOD);
            Consumable consumable = stack.get(DataComponents.CONSUMABLE);
            if (properties == null || consumable == null || !consumable.canConsume(bot, stack)) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (UNSAFE_FOODS.contains(itemId)) continue;

            FoodCandidate candidate = new FoodCandidate(
                    slot, itemId, properties.nutrition(), properties.saturation());
            if (RARE_UTILITY_FOODS.contains(itemId)) {
                if (bestUtility == null || FoodCandidate.BEST_FIRST.compare(candidate, bestUtility) < 0) {
                    bestUtility = candidate;
                }
            } else if (bestOrdinary == null || FoodCandidate.BEST_FIRST.compare(candidate, bestOrdinary) < 0) {
                bestOrdinary = candidate;
            }
        }
        return bestOrdinary != null ? bestOrdinary : bestUtility;
    }

    private static PausedActions pauseActions(ServerPlayer bot) {
        if (!(bot instanceof ServerPlayerInterface playerInterface)) {
            if (bot.isUsingItem()) bot.stopUsingItem();
            return null;
        }

        EntityPlayerActionPack liveActions = playerInterface.getActionPack();
        boolean wasSprinting = bot.isSprinting();
        boolean wasSneaking = bot.isShiftKeyDown();
        EntityPlayerActionPack savedActions = new EntityPlayerActionPack(bot);
        savedActions.copyFrom(liveActions);
        liveActions.stopAll();
        if (bot.isUsingItem()) bot.stopUsingItem();
        return new PausedActions(savedActions, wasSprinting, wasSneaking,
                MiningTool.isMining(bot.getUUID()));
    }

    private static void restoreSession(ConsumptionSession session) {
        Inventory inventory = session.bot.getInventory();
        if (session.swapped && inventory.getItem(session.food.slot) == session.displacedStack) {
            swapSlots(inventory, session.food.slot, session.useSlot);
        }
        inventory.setSelectedSlot(session.previousSelectedSlot);
        syncInventory(session.bot);

        if (session.bot.isAlive()
                && !session.bot.hasDisconnected()
                && session.pausedActions != null
                && session.bot instanceof ServerPlayerInterface playerInterface) {
            EntityPlayerActionPack liveActions = playerInterface.getActionPack();
            liveActions.copyFrom(session.pausedActions.actionPack);
            if (session.pausedActions.miningWasActive) {
                // Mining owns ATTACK and will re-establish it on its next server tick
                // if the generation is still active. Never restore a stale attack.
                liveActions.start(EntityPlayerActionPack.ActionType.ATTACK, null);
            }
            session.bot.setShiftKeyDown(session.pausedActions.wasSneaking);
            session.bot.setSprinting(session.pausedActions.wasSprinting);
        }
        finishPause(session.bot.getUUID());
    }

    private static void finishPause(UUID botId) {
        Long started = PAUSE_STARTED_AT.remove(botId);
        if (started != null) {
            TOTAL_PAUSED_MILLIS.merge(
                    botId, Math.max(0L, System.currentTimeMillis() - started), Long::sum);
        }
        NavigationService.resume(botId, SuspensionReason.EATING);
    }

    private static <T> T callOnServer(MinecraftServer server, Callable<T> callable) throws Exception {
        if (server.isSameThread()) return callable.call();
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(SERVER_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void swapSlots(Inventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getItem(first);
        ItemStack secondStack = inventory.getItem(second);
        inventory.setItem(first, secondStack);
        inventory.setItem(second, firstStack);
    }

    private static void syncInventory(ServerPlayer bot) {
        bot.getInventory().setChanged();
        bot.containerMenu.broadcastChanges();
    }

    public record ConsumptionResult(
            boolean success,
            int hungerBefore,
            int hungerAfter,
            String foodItemId,
            String message
    ) {
        public static ConsumptionResult notAttempted() {
            return new ConsumptionResult(false, 0, 0, "", "not attempted");
        }

        public static ConsumptionResult failure(String message) {
            return new ConsumptionResult(false, 0, 0, "", message);
        }

        public int hungerGained() {
            return Math.max(0, hungerAfter - hungerBefore);
        }

        public boolean attempted() {
            return !"not attempted".equals(message);
        }
    }

    private record ConsumptionStatus(boolean usingItem, int hunger) {}

    private record ConsumptionSession(
            ServerPlayer bot,
            int hungerBefore,
            FoodCandidate food,
            int previousSelectedSlot,
            int useSlot,
            boolean swapped,
            ItemStack displacedStack,
            PausedActions pausedActions
    ) {}

    private record PausedActions(
            EntityPlayerActionPack actionPack,
            boolean wasSprinting,
            boolean wasSneaking,
            boolean miningWasActive
    ) {}

    private record FoodCandidate(int slot, String itemId, int nutrition, float saturation) {
        private static final Comparator<FoodCandidate> BEST_FIRST =
                Comparator.comparingDouble(FoodCandidate::score).reversed()
                        .thenComparing(Comparator.comparingInt(FoodCandidate::nutrition).reversed())
                        .thenComparingInt(FoodCandidate::slot);

        private double score() {
            return nutrition + saturation;
        }
    }
}
