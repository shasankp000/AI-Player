package net.shasankp000.PlayerUtils;

import net.minecraft.server.network.ServerPlayerEntity;

public class getPlayerOxygen {

    public static int getBotOxygenLevel(ServerPlayerEntity bot) {
        if (bot != null) {
            return bot.getAir(); // Returns the bot's current oxygen level
        }
        return 0; // Default to 0 if bot is null or oxygen level cannot be retrieved
    }

}
