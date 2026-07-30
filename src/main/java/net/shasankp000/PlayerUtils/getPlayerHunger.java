package net.shasankp000.PlayerUtils;

import net.minecraft.server.level.ServerPlayer;

public class getPlayerHunger {

    public static int getBotHungerLevel(ServerPlayer bot) {
        if (bot != null) {
            return bot.getFoodData().getFoodLevel();
        }
        return 0; // Default to 0 if bot is null or hunger cannot be retrieved
    }

}
