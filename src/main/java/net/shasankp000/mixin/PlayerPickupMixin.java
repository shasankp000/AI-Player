package net.shasankp000.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.shasankp000.GameAI.handoff.ItemHandoffListener;
import net.shasankp000.GameAI.handoff.TradeListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link ItemEntity#playerTouch(Player)} to power both
 * Feature 4 (smart item handoff) and Feature 7 (sneak-throw trade system).
 *
 * <p>{@code PlayerPickupItemCallback} was removed from Fabric API before
 * version {@code 0.116.9+1.21.1}, so a mixin is the correct approach for
 * this Minecraft version.
 *
 * <p>Dispatch order:
 * <ol>
 *   <li>{@link TradeListener#dispatch} — checked first; if it returns
 *       {@code true} the item was consumed by the trade and we cancel
 *       the vanilla pickup via {@link CallbackInfo#cancel()}.</li>
 *   <li>{@link ItemHandoffListener#dispatch} — only reached when TradeListener
 *       did not intercept (i.e. the throw was not a sneak-trade confirmation).
 *       Handles normal bot item-handoff reactions.</li>
 * </ol>
 */
@Mixin(ItemEntity.class)
public class PlayerPickupMixin {

    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void aiPlayer_onPlayerCollision(Player player, CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity)(Object)this;

        // TradeListener gets first priority — it returns true only in Phase 2
        // when it has consumed the confirmation throw item itself.
        boolean consumed = TradeListener.dispatch(player, itemEntity);
        if (consumed) {
            ci.cancel();
            return;
        }
        // ItemHandoffListener runs second for non-trade pickups by the bot.
        ItemHandoffListener.dispatch(player, itemEntity);
    }
}
