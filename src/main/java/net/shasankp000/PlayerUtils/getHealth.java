package net.shasankp000.PlayerUtils;

import net.minecraft.server.network.ServerPlayerEntity;

public class getHealth {

    public static float getBotHealthLevel(ServerPlayerEntity bot) {
        if (bot != null) {
            return bot.getHealth();
        }
        return 0.0f; // Default to 0 if bot is null or hunger cannot be retrieved
    }

}
