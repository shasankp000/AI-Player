package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class getFrostLevel {

    // Method to calculate the bot's frost level
    public static int calculateFrostLevel(Player bot) {
        BlockPos botPosition = bot.blockPosition();
        BlockState blockState = bot.level().getBlockState(botPosition);

        // Start with a base frost level of 0
        int frostLevel = 0;

            // Check if the bot is in powdered snow
        if (blockState.is(Blocks.POWDER_SNOW)) {
                frostLevel += 5; // Assign a higher frost level for powdered snow
        }

        // Check for cold biomes (e.g., Snowy Tundra, Frozen Ocean)
        if (bot.level().getBiome(botPosition).value().getBaseTemperature() < 0.15f) {
                frostLevel += 2; // Assign a moderate frost level for cold biomes
        }

        // Check if the bot is wearing frost protection gear
        if (bot.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
            frostLevel -= 3; // Reduce frost level due to fire resistance effect
        }

        // Ensure frost level is non-negative
        return Math.max(frostLevel, 0);
    }

}
