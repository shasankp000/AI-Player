package net.shasankp000.GameAI.handoff;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.BotEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes bot item-pickup events to {@link ItemHandoffHandler}.
 *
 * <p>In Fabric API 0.116.9+1.21.1 {@code PlayerPickupItemCallback} no longer
 * exists.  Detection is done via {@link net.shasankp000.mixin.PlayerPickupMixin},
 * which injects into {@code PlayerEntity.pickUpItem(ItemEntity, int)} and
 * calls {@link #dispatch(PlayerEntity, ItemEntity)} directly.
 *
 * <p>Call {@link #register()} once from {@code AIPlayer.onInitialize()} so
 * the registered flag is set and log messages appear as expected.
 */
public final class ItemHandoffListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff-listener");

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
    public static void dispatch(PlayerEntity picker, ItemEntity itemEntity) {
        if (!registered) return;
        if (!(picker instanceof ServerPlayerEntity serverPlayer)) return;
        if (BotEventHandler.bot == null) return;
        if (!serverPlayer.getUuid().equals(BotEventHandler.bot.getUuid())) return;

        ItemStack stack = itemEntity.getStack();
        if (stack.isEmpty()) return;

        // Resolve the original thrower.
        // In 1.21.1, ItemEntity no longer exposes getThrower().
        // We use getOwner() as the primary signal (set when a player throws an item)
        // and fall back to checking the entity's NBT thrower UUID via the owner entity.
        ServerPlayerEntity thrower = null;

        // Primary: getOwner() returns the entity that "owns" the item (set on throw)
        if (itemEntity.getOwner() instanceof ServerPlayerEntity ownerPlayer
                && !ownerPlayer.getUuid().equals(serverPlayer.getUuid())) {
            thrower = ownerPlayer;
        }

        // Fallback: scan the item entity's NBT for the Thrower UUID written by vanilla
        // (net.minecraft.entity.ItemEntity stores it under the "Thrower" key).
        if (thrower == null) {
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            itemEntity.writeNbt(nbt);
            if (nbt.containsUuid("Thrower")) {
                java.util.UUID throwerId = nbt.getUuid("Thrower");
                net.minecraft.server.MinecraftServer srv = serverPlayer.getServer();
                if (srv != null && !throwerId.equals(serverPlayer.getUuid())) {
                    ServerPlayerEntity candidate = srv.getPlayerManager().getPlayer(throwerId);
                    if (candidate != null) {
                        thrower = candidate;
                    }
                }
            }
        }

        LOGGER.debug("[handoff-listener] bot '{}' picked up '{}' (thrower={})",
                serverPlayer.getName().getString(),
                stack.getName().getString(),
                thrower != null ? thrower.getName().getString() : "none");

        ItemHandoffHandler.onBotPickedUpItem(serverPlayer, thrower, stack);
    }
}
