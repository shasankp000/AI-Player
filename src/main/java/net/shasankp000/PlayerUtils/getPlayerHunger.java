package net.shasankp000.PlayerUtils;

import net.minecraft.server.network.ServerPlayerEntity;

public class getPlayerHunger {

    public static int getBotHungerLevel(ServerPlayerEntity bot) {
        if (bot != null) {
            return bot.getHungerManager().getFoodLevel();
        }
        return 0; // Default to 0 if bot is null or hunger cannot be retrieved
    }

}
