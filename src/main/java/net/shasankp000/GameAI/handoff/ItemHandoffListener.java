package net.shasankp000.GameAI.handoff;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes bot item-pickup events to {@link ItemHandoffHandler}.
 *
 * <p>In Fabric API 0.155.2+26.2 {@code PlayerPickupItemCallback} no longer
 * exists.  Detection is done via {@link net.shasankp000.mixin.PlayerPickupMixin},
 * which injects into {@code PlayerEntity.pickUpItem(ItemEntity, int)} and
 * calls {@link #dispatch(Player, ItemEntity)} directly.
 *
 * <p>Call {@link #register()} once from {@code AIPlayer.onInitialize()} so
 * the registered flag is set and log messages appear as expected.
 */
public final class ItemHandoffListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff-listener");

    /**
     * playerTouch can run for the same ItemEntity on several consecutive ticks
     * before vanilla pickup removes it. Remember handled entities so one gift
     * produces one reaction and one mood update.
     */
    private static final Map<UUID, Long> HANDLED_ITEMS = new ConcurrentHashMap<>();

    /**
     * Several item entities may belong to one gift (for example, blocks thrown
     * in quick succession). Collapse those into a single acknowledgement.
     */
    private static final Map<UUID, Long> LAST_ACKNOWLEDGEMENT = new ConcurrentHashMap<>();
    private static final long ACKNOWLEDGEMENT_COOLDOWN_MS = 3_000L;
    private static final long HANDLED_ITEM_RETENTION_MS = 60_000L;

    private ItemHandoffListener() {}

    private static volatile boolean registered = false;

    /**
     * Marks the listener as active.  The actual hook is wired by
     * {@link net.shasankp000.mixin.PlayerPickupMixin} — this method just
     * records the intent so {@link #dispatch} knows to run.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        LOGGER.info("[handoff-listener] ItemHandoffListener registered (mixin-backed).");
    }

    /**
     * Called by {@link net.shasankp000.mixin.PlayerPickupMixin} on every
     * server-side item pickup.  Filters for the active bot player and
     * delegates to {@link ItemHandoffHandler}.
     *
     * @param picker     the player who picked up the item
     * @param itemEntity the item that was picked up
     */
    public static void dispatch(Player picker, ItemEntity itemEntity) {
        if (!registered) return;
        if (!(picker instanceof ServerPlayer serverPlayer)) return;
        if (BotEventHandler.bot == null) return;
        if (!serverPlayer.getUUID().equals(BotEventHandler.bot.getUUID())) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // Resolve the original thrower.
        // In 26.2, ItemEntity no longer exposes getThrower().
        // We use getOwner() as the primary signal (set when a player throws an item)
        // and fall back to checking the entity's NBT thrower UUID via the owner entity.
        ServerPlayer thrower = null;

        // Primary: getOwner() returns the entity that "owns" the item (set on throw)
        if (itemEntity.getOwner() instanceof ServerPlayer ownerPlayer
                && !ownerPlayer.getUUID().equals(serverPlayer.getUUID())) {
            thrower = ownerPlayer;
        }

        // Ignore repeated collision callbacks for the same dropped item.
        long now = System.currentTimeMillis();
        if (HANDLED_ITEMS.putIfAbsent(itemEntity.getUUID(), now) != null) {
            return;
        }

        // A burst of separately thrown items is still one handoff from the
        // player's perspective, so acknowledge the burst only once.
        boolean sendAcknowledgement = true;
        if (thrower != null) {
            Long previousAcknowledgement = LAST_ACKNOWLEDGEMENT.put(thrower.getUUID(), now);
            if (previousAcknowledgement != null
                    && now - previousAcknowledgement < ACKNOWLEDGEMENT_COOLDOWN_MS) {
                sendAcknowledgement = false;
                LOGGER.debug("[handoff-listener] Suppressed duplicate acknowledgement for '{}'",
                        thrower.getName().getString());
            }
        }

        LOGGER.debug("[handoff-listener] bot '{}' picked up '{}' (thrower={})",
                serverPlayer.getName().getString(),
                stack.getHoverName().getString(),
                thrower != null ? thrower.getName().getString() : "none");

        ItemHandoffHandler.onBotPickedUpItem(
                serverPlayer, thrower, stack, sendAcknowledgement);
        cleanupHandledItems(now);
    }

    private static void cleanupHandledItems(long now) {
        HANDLED_ITEMS.entrySet().removeIf(
                entry -> now - entry.getValue() > HANDLED_ITEM_RETENTION_MS);
    }
}
