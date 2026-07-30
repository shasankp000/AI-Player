package net.shasankp000.PlayerUtils;

import net.minecraft.server.level.ServerPlayer;

public class getPlayerOxygen {

    public static int getBotOxygenLevel(ServerPlayer bot) {
        if (bot != null) {
            return bot.getAirSupply(); // Returns the bot's current oxygen level
        }
        return 0; // Default to 0 if bot is null or oxygen level cannot be retrieved
    }

}
