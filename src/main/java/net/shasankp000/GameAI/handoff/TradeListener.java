package net.shasankp000.GameAI.handoff;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.shasankp000.GameAI.BotEventHandler;
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
 * <p>In Fabric API 0.116.9+1.21.1 {@code PlayerPickupItemCallback} no longer
 * exists.  Detection is done via {@link net.shasankp000.mixin.PlayerPickupMixin},
 * which injects into {@code PlayerEntity.pickUpItem(ItemEntity, int)} and calls
 * {@link #dispatch(PlayerEntity, ItemEntity)} directly.
 *
 * <p><b>MC 1.21.1 note:</b> {@code ItemEntity} has no {@code getThrower()}
 * method.  Thrower identity is resolved via {@code itemEntity.getOwner()}:
 * if the owner is a {@link ServerPlayerEntity} it is treated as the thrower.
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
    public static boolean dispatch(PlayerEntity picker, ItemEntity itemEntity) {
        if (!registered) return false;

        // FIX: MC 1.21.1 has no ItemEntity#getThrower().
        // Use getOwner(): if the entity owner is a ServerPlayerEntity we treat
        // them as the thrower.  getOwner() returns the Entity whose UUID was
        // stored via setOwner() when the item was thrown by a player.
        ServerPlayerEntity thrower = null;
        if (itemEntity.getOwner() instanceof ServerPlayerEntity ownerPlayer) {
            thrower = ownerPlayer;
        }
        if (thrower == null) return false;

        UUID throwerUuid = thrower.getUuid();

        ServerPlayerEntity bot = BotEventHandler.bot;
        if (bot == null) return false;

        // Ignore if the bot itself threw the item
        if (throwerUuid.equals(bot.getUuid())) return false;

        // We no longer need a separate getPlayerManager lookup — thrower is already resolved above.
        // Keep server reference only for potential future use.
        MinecraftServer server = bot.getServer();
        if (server == null) return false;

        double distToBot = itemEntity.getPos().distanceTo(bot.getPos());
        if (distToBot > TRADE_RANGE) return false;

        if (!thrower.isSneaking()) return false;

        ItemStack thrown = itemEntity.getStack();
        if (thrown.isEmpty()) return false;

        long currentTick = bot.getServerWorld().getTime();
        tickPrune(currentTick);

        TradeSession existing = SESSIONS.get(throwerUuid);

        if (existing == null) {
            // ── Phase 1: open a new session ──────────────────────────────────
            ItemStack counterOffer = TradeEvaluator.evaluate(thrown, bot);

            if (counterOffer.isEmpty()) {
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString() + "] §fSorry, I have nothing fair to offer for that."),
                    false);
                return false;
            }

            TradeSession session = new TradeSession(throwerUuid, thrown, counterOffer, currentTick);
            SESSIONS.put(throwerUuid, session);

            String botName = bot.getName().getString();
            String offName = TradeEvaluator.displayName(thrown);
            String ctrName = TradeEvaluator.displayName(counterOffer);
            thrower.sendMessage(
                Text.literal("§e[" + botName + "] §fI'll trade my §b" + ctrName
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
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString() + "] §fThat's not what we agreed on."),
                    false);
                return false;
            }

            boolean removed = removeOneFromBotInventory(bot, existing.counterOfferItem);
            if (!removed) {
                thrower.sendMessage(
                    Text.literal("§e[" + bot.getName().getString()
                        + "] §fI can't find that item in my inventory anymore. Trade cancelled."),
                    false);
                SESSIONS.remove(throwerUuid);
                return false;
            }

            bot.getInventory().insertStack(thrown.copy());
            itemEntity.discard();

            dropItemNearBot(bot, existing.counterOfferItem.copy());

            String botName = bot.getName().getString();
            thrower.sendMessage(
                Text.literal("§e[" + botName + "] §fDeal! Enjoy your §b"
                    + TradeEvaluator.displayName(existing.counterOfferItem) + "§f!"),
                false);

            LOGGER.info("[trade] Trade completed: {} gave {}, received {}",
                thrower.getName().getString(),
                TradeEvaluator.displayName(existing.offeredItem),
                TradeEvaluator.displayName(existing.counterOfferItem));

            SESSIONS.remove(throwerUuid);
            // Return true: item was consumed by trade, suppress vanilla pickup
            return true;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean removeOneFromBotInventory(ServerPlayerEntity bot, ItemStack template) {
        for (int slot = 0; slot < bot.getInventory().size(); slot++) {
            ItemStack stack = bot.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.getItem() == template.getItem()) {
                stack.decrement(1);
                if (stack.isEmpty()) {
                    bot.getInventory().setStack(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static void dropItemNearBot(ServerPlayerEntity bot, ItemStack stack) {
        ServerWorld world = bot.getServerWorld();
        Vec3d pos = bot.getPos().add(0, 0.5, 0);
        ItemEntity ie = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        ie.setVelocity(0, 0.1, 0);
        world.spawnEntity(ie);
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
    }
}
