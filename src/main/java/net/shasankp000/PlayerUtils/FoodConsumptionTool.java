package net.shasankp000.PlayerUtils;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
    public static boolean hasSafeFood(ServerPlayerEntity bot) {
        return bot != null && findBestFood(bot) != null;
    }

    /**
     * Stops current fake-player inputs, consumes the best safe food with vanilla
     * timing, restores inventory/input state, and reports the observed hunger gain.
     */
    public static ConsumptionResult consumeBestFood(ServerPlayerEntity bot) {
        if (bot == null || !bot.isAlive() || bot.isDisconnected()) {
            return ConsumptionResult.failure("bot is unavailable");
        }

        MinecraftServer server = bot.getServer();
        if (server == null || !server.isRunning()) {
            return ConsumptionResult.failure("server is unavailable");
        }
        if (server.isOnThread()) {
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
                        () -> new ConsumptionStatus(bot.isUsingItem(), bot.getHungerManager().getFoodLevel()));
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
    public static void awaitResume(ServerPlayerEntity bot) {
        if (bot == null) return;
        while (isConsumptionInProgress(bot.getUuid()) && bot.isAlive() && !bot.isDisconnected()) {
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

    private static ConsumptionSession startConsumption(ServerPlayerEntity bot) {
        FoodCandidate food = findBestFood(bot);
        if (food == null) return null;

        UUID botId = bot.getUuid();
        if (PAUSE_STARTED_AT.putIfAbsent(botId, System.currentTimeMillis()) != null) return null;

        PausedActions pausedActions = pauseActions(bot);
        PlayerInventory inventory = bot.getInventory();
        int previousSelectedSlot = inventory.getSelectedSlot();
        int useSlot = food.slot < 9 ? food.slot : previousSelectedSlot;
        boolean swapped = food.slot != useSlot;
        ItemStack displacedStack = swapped ? inventory.getStack(useSlot) : ItemStack.EMPTY;

        if (swapped) swapSlots(inventory, food.slot, useSlot);
        inventory.setSelectedSlot(useSlot);
        syncInventory(bot);

        ConsumptionSession session = new ConsumptionSession(
                bot,
                bot.getHungerManager().getFoodLevel(),
                food,
                previousSelectedSlot,
                useSlot,
                swapped,
                displacedStack,
                pausedActions
        );

        ItemStack handStack = bot.getStackInHand(Hand.MAIN_HAND);
        ActionResult result = bot.interactionManager.interactItem(bot, bot.getWorld(), handStack, Hand.MAIN_HAND);
        if (!result.isAccepted() || !bot.isUsingItem()) {
            restoreSession(session);
            return null;
        }

        LOGGER.info("RL selected food {} for '{}' at hunger {}/20",
                food.itemId, bot.getName().getString(), session.hungerBefore);
        return session;
    }

    private static ConsumptionResult finishConsumption(ConsumptionSession session) {
        ServerPlayerEntity bot = session.bot;
        if (bot.isUsingItem()) bot.stopUsingItem();
        int hungerAfter = bot.getHungerManager().getFoodLevel();
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
                int hungerAfter = session.bot.getHungerManager().getFoodLevel();
                restoreSession(session);
                return new ConsumptionResult(false, session.hungerBefore, hungerAfter,
                        session.food.itemId, message);
            });
        } catch (Exception ignored) {
            finishPause(session.bot.getUuid());
            return new ConsumptionResult(false, session.hungerBefore,
                    session.bot.getHungerManager().getFoodLevel(), session.food.itemId, message);
        }
    }

    private static FoodCandidate findBestFood(ServerPlayerEntity bot) {
        FoodCandidate bestOrdinary = null;
        FoodCandidate bestUtility = null;
        int slots = Math.min(INVENTORY_SEARCH_SIZE, bot.getInventory().size());

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = bot.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            FoodComponent properties = stack.get(DataComponentTypes.FOOD);
            ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
            if (properties == null || consumable == null || !consumable.canConsume(bot, stack)) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
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

    private static PausedActions pauseActions(ServerPlayerEntity bot) {
        if (!(bot instanceof ServerPlayerInterface playerInterface)) {
            if (bot.isUsingItem()) bot.stopUsingItem();
            return null;
        }

        EntityPlayerActionPack liveActions = playerInterface.getActionPack();
        boolean wasSprinting = bot.isSprinting();
        boolean wasSneaking = bot.isSneaking();
        EntityPlayerActionPack savedActions = new EntityPlayerActionPack(bot);
        savedActions.copyFrom(liveActions);
        liveActions.stopAll();
        if (bot.isUsingItem()) bot.stopUsingItem();
        return new PausedActions(savedActions, wasSprinting, wasSneaking);
    }

    private static void restoreSession(ConsumptionSession session) {
        PlayerInventory inventory = session.bot.getInventory();
        if (session.swapped && inventory.getStack(session.food.slot) == session.displacedStack) {
            swapSlots(inventory, session.food.slot, session.useSlot);
        }
        inventory.setSelectedSlot(session.previousSelectedSlot);
        syncInventory(session.bot);

        if (session.bot.isAlive()
                && !session.bot.isDisconnected()
                && session.pausedActions != null
                && session.bot instanceof ServerPlayerInterface playerInterface) {
            playerInterface.getActionPack().copyFrom(session.pausedActions.actionPack);
            session.bot.setSneaking(session.pausedActions.wasSneaking);
            session.bot.setSprinting(session.pausedActions.wasSprinting);
        }
        finishPause(session.bot.getUuid());
    }

    private static void finishPause(UUID botId) {
        Long started = PAUSE_STARTED_AT.remove(botId);
        if (started != null) {
            TOTAL_PAUSED_MILLIS.merge(
                    botId, Math.max(0L, System.currentTimeMillis() - started), Long::sum);
        }
    }

    private static <T> T callOnServer(MinecraftServer server, Callable<T> callable) throws Exception {
        if (server.isOnThread()) return callable.call();
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

    private static void swapSlots(PlayerInventory inventory, int first, int second) {
        ItemStack firstStack = inventory.getStack(first);
        ItemStack secondStack = inventory.getStack(second);
        inventory.setStack(first, secondStack);
        inventory.setStack(second, firstStack);
    }

    private static void syncInventory(ServerPlayerEntity bot) {
        bot.getInventory().markDirty();
        bot.playerScreenHandler.sendContentUpdates();
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
            ServerPlayerEntity bot,
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
            boolean wasSneaking
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
