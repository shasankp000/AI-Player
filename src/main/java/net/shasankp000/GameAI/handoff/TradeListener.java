package net.shasankp000.GameAI.handoff;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.PathFinding.NavigationService;
import net.shasankp000.PathFinding.SuspensionReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the two-phase sneak-throw trade protocol (Feature 7).
 *
 * <h2>Phase 1 — Offer detection</h2>
 * When a player throws an item <em>while sneaking</em> that lands within
 * {@value #TRADE_RANGE} blocks of the bot AND no session already exists for
 * that player, a {@link TradeSession} is opened and the bot announces its
 * counter-offer in chat.
 *
 * <h2>Phase 2 — Delivery confirmation</h2>
 * When the same player throws a second item matching the original offer item
 * the bot completes the swap: it removes the counter-offer from its inventory
 * and drops it at its feet for the player to collect.
 *
 * <p>Sessions expire after {@link TradeSession#EXPIRY_TICKS} ticks (~30 s).
 *
 * <p>In Fabric API 0.155.2+26.2 {@code PlayerPickupItemCallback} no longer
 * exists.  Detection is done via {@link net.shasankp000.mixin.PlayerPickupMixin},
 * which injects into {@code PlayerEntity.pickUpItem(ItemEntity, int)} and calls
 * {@link #dispatch(Player, ItemEntity)} directly.
 *
 * <p><b>MC 26.2 note:</b> {@code ItemEntity} has no {@code getThrower()}
 * method.  Thrower identity is resolved via {@code itemEntity.getOwner()}:
 * if the owner is a {@link ServerPlayer} it is treated as the thrower.
 */
public final class TradeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    /** Radius in blocks within which a thrown item triggers a trade. */
    private static final double TRADE_RANGE = 6.0;

    /** Active sessions, keyed by thrower UUID. */
    private static final Map<UUID, TradeSession> SESSIONS = new ConcurrentHashMap<>();

    private static volatile boolean registered = false;

    private TradeListener() {}

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Marks the listener as active.  The actual hook is wired by
     * {@link net.shasankp000.mixin.PlayerPickupMixin} — this method just
     * records intent so {@link #dispatch} knows to run.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        LOGGER.info("[trade] TradeListener registered (mixin-backed).");
    }

    // ── Tick-based expiry ────────────────────────────────────────────────────

    /**
     * Prunes expired sessions.  Hook this into a server tick event or call it
     * from any per-tick path inside {@link BotEventHandler}.
     */
    public static void tickPrune(long currentTick) {
        SESSIONS.entrySet().removeIf(e -> e.getValue().isExpired(currentTick));
        if (SESSIONS.isEmpty() && BotEventHandler.bot != null)
            NavigationService.resume(BotEventHandler.bot.getUUID(), SuspensionReason.TRADE);
    }

    // ── Dispatch (called by PlayerPickupMixin) ───────────────────────────────

    /**
     * Called by {@link net.shasankp000.mixin.PlayerPickupMixin} on every
     * server-side item pickup.  Returns {@code true} if the item was consumed
     * by the trade system and the mixin should discard it (cancel vanilla
     * pickup), or {@code false} to let normal pickup proceed.
     *
     * @param picker     the player who picked up the item
     * @param itemEntity the item entity being picked up
     * @return {@code true} to suppress vanilla pickup, {@code false} to pass through
     */
    public static boolean dispatch(Player picker, ItemEntity itemEntity) {
        if (!registered) return false;

        // FIX: MC 26.2 has no ItemEntity#getThrower().
        // Use getOwner(): if the entity owner is a ServerPlayerEntity we treat
        // them as the thrower.  getOwner() returns the Entity whose UUID was
        // stored via setOwner() when the item was thrown by a player.
        ServerPlayer thrower = null;
        if (itemEntity.getOwner() instanceof ServerPlayer ownerPlayer) {
            thrower = ownerPlayer;
        }
        if (thrower == null) return false;

        UUID throwerUuid = thrower.getUUID();

        ServerPlayer bot = BotEventHandler.bot;
        if (bot == null) return false;

        // Ignore if the bot itself threw the item
        if (throwerUuid.equals(bot.getUUID())) return false;

        // We no longer need a separate getPlayerManager lookup — thrower is already resolved above.
        // Keep server reference only for potential future use.
        MinecraftServer server = bot.createCommandSourceStack().getServer();
        if (server == null) return false;

        double distToBot = itemEntity.position().distanceTo(bot.position());
        if (distToBot > TRADE_RANGE) return false;

        if (!thrower.isShiftKeyDown()) return false;

        ItemStack thrown = itemEntity.getItem();
        if (thrown.isEmpty()) return false;

        long currentTick = bot.level().getGameTime();
        tickPrune(currentTick);

        TradeSession existing = SESSIONS.get(throwerUuid);

        if (existing == null) {
            // ── Phase 1: open a new session ──────────────────────────────────
            ItemStack counterOffer = TradeEvaluator.evaluate(thrown, bot);

            if (counterOffer.isEmpty()) {
                thrower.sendSystemMessage(
                    Component.literal("§e[" + bot.getName().getString() + "] §fSorry, I have nothing fair to offer for that."),
                    false);
                return false;
            }

            TradeSession session = new TradeSession(throwerUuid, thrown, counterOffer, currentTick);
            SESSIONS.put(throwerUuid, session);
            NavigationService.suspend(bot.getUUID(), SuspensionReason.TRADE);

            String botName = bot.getName().getString();
            String offName = TradeEvaluator.displayName(thrown);
            String ctrName = TradeEvaluator.displayName(counterOffer);
            thrower.sendSystemMessage(
                Component.literal("§e[" + botName + "] §fI'll trade my §b" + ctrName
                    + "§f for your §b" + offName
                    + "§f. Throw the §b" + offName + "§f again to confirm!"),
                false);

            LOGGER.info("[trade] Session opened: {} offers {} for {}",
                thrower.getName().getString(), offName, ctrName);

            // Return false: let the bot pick up Phase-1 thrown item normally
            return false;

        } else {
            // ── Phase 2: complete the trade ──────────────────────────────────
            if (thrown.getItem() != existing.offeredItem.getItem()) {
                thrower.sendSystemMessage(
                    Component.literal("§e[" + bot.getName().getString() + "] §fThat's not what we agreed on."),
                    false);
                return false;
            }

            boolean removed = removeOneFromBotInventory(bot, existing.counterOfferItem);
            if (!removed) {
                thrower.sendSystemMessage(
                    Component.literal("§e[" + bot.getName().getString()
                        + "] §fI can't find that item in my inventory anymore. Trade cancelled."),
                    false);
                SESSIONS.remove(throwerUuid);
                resumeTradeNavigationIfIdle(bot);
                return false;
            }

            bot.getInventory().add(thrown.copy());
            itemEntity.discard();

            dropItemNearBot(bot, existing.counterOfferItem.copy());

            String botName = bot.getName().getString();
            thrower.sendSystemMessage(
                Component.literal("§e[" + botName + "] §fDeal! Enjoy your §b"
                    + TradeEvaluator.displayName(existing.counterOfferItem) + "§f!"),
                false);

            LOGGER.info("[trade] Trade completed: {} gave {}, received {}",
                thrower.getName().getString(),
                TradeEvaluator.displayName(existing.offeredItem),
                TradeEvaluator.displayName(existing.counterOfferItem));

            SESSIONS.remove(throwerUuid);
            resumeTradeNavigationIfIdle(bot);
            // Return true: item was consumed by trade, suppress vanilla pickup
            return true;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean removeOneFromBotInventory(ServerPlayer bot, ItemStack template) {
        for (int slot = 0; slot < bot.getInventory().getContainerSize(); slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == template.getItem()) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    bot.getInventory().setItem(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static void dropItemNearBot(ServerPlayer bot, ItemStack stack) {
        ServerLevel world = bot.level();
        Vec3 pos = bot.position().add(0, 0.5, 0);
        ItemEntity ie = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        ie.setDeltaMovement(0, 0.1, 0);
        world.addFreshEntity(ie);
    }

    private static void resumeTradeNavigationIfIdle(ServerPlayer bot) {
        if (SESSIONS.isEmpty()) NavigationService.resume(bot.getUUID(), SuspensionReason.TRADE);
    }

    // ── Public session access ────────────────────────────────────────────────

    /** Returns the active session for {@code playerUuid}, or {@code null}. */
    public static TradeSession getSession(UUID playerUuid) {
        return SESSIONS.get(playerUuid);
    }

    /**
     * Forcibly removes a session (used by {@code /bot trade cancel}).
     */
    public static void cancelSession(UUID playerUuid) {
        SESSIONS.remove(playerUuid);
        if (SESSIONS.isEmpty() && BotEventHandler.bot != null)
            NavigationService.resume(BotEventHandler.bot.getUUID(), SuspensionReason.TRADE);
    }
}
