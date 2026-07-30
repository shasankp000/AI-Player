package net.shasankp000.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.PlayerUtils.PlayerRetaliationTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to detect when bot player entities are damaged by other players
 * This enables the player retaliation system
 */
@Mixin(ServerPlayerEntity.class)
public class PlayerDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"))
    private void onPlayerDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity bot = (ServerPlayerEntity) (Object) this;

        // Check if damage source is another player
        Entity attacker = source.getAttacker();
        if (attacker instanceof PlayerEntity playerAttacker) {
            // Don't track damage from the bot to itself
            if (playerAttacker.getUuid().equals(bot.getUuid())) {
                return;
            }

            // Record the hit for retaliation tracking
            PlayerRetaliationTracker.recordPlayerHit(bot, playerAttacker);
        }
    }
}

