package net.shasankp000.PlayerUtils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class getFrostLevel {

    // Method to calculate the bot's frost level
    public static int calculateFrostLevel(PlayerEntity bot) {
        BlockPos botPosition = bot.getBlockPos();
        BlockState blockState = bot.getWorld().getBlockState(botPosition);

        // Start with a base frost level of 0
        int frostLevel = 0;

            // Check if the bot is in powdered snow
        if (blockState.isOf(Blocks.POWDER_SNOW)) {
                frostLevel += 5; // Assign a higher frost level for powdered snow
        }

        // Check for cold biomes (e.g., Snowy Tundra, Frozen Ocean)
        if (bot.getWorld().getBiome(botPosition).value().getTemperature() < 0.15f) {
                frostLevel += 2; // Assign a moderate frost level for cold biomes
        }

        // Check if the bot is wearing frost protection gear
        if (bot.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE)) {
            frostLevel -= 3; // Reduce frost level due to fire resistance effect
        }

        // Ensure frost level is non-negative
        return Math.max(frostLevel, 0);
    }

}
