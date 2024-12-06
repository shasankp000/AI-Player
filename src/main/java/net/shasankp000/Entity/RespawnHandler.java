package net.shasankp000.Entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class RespawnHandler {
    public static void registerRespawnListener(ServerPlayerEntity bot) {

        String botName = bot.getName().getString();

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer.getName().getString().equals(botName)) { // Replace "Steve" with your bot's name
                System.out.println("Detected bot respawn for " + newPlayer.getName().getString());
                AutoFaceEntity.handleBotRespawn(newPlayer); // Pass the new bot instance
            }
        });
    }
}

