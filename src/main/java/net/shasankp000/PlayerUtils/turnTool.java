package net.shasankp000.PlayerUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;


public class turnTool {
    public static void turn(CommandSourceStack botSource, String direction) {

        MinecraftServer server = botSource.getServer();
        String botName = botSource.getTextName();
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " turn " + direction);

    }
}
