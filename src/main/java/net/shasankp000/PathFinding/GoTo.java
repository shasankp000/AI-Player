package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

/** Blocking compatibility adapter for tool calls that already run away from the server thread. */
public final class GoTo {
    private GoTo() {}

    public static String goTo(CommandSourceStack source, int x, int y, int z, boolean sprint) {
        ServerPlayer bot = source.getPlayer();
        if (bot == null) return "Bot not found!";
        MinecraftServer server = source.getServer();
        if (server.isSameThread()) {
            NavigationService.navigate(bot, new BlockPos(x, y, z), NavigationOptions.of(sprint));
            return "Bot navigation started";
        }
        try {
            NavigationResult result = NavigationService.navigate(bot, new BlockPos(x, y, z),
                    NavigationOptions.of(sprint)).get(5, TimeUnit.MINUTES);
            BlockPos finalPos = result.finalPosition();
            if (result.reached()) {
                return String.format("Bot moved to position - x: %d y: %d z: %d",
                        finalPos.getX(), finalPos.getY(), finalPos.getZ());
            }
            return String.format("Navigation %s at x: %d y: %d z: %d - %s",
                    result.status().name().toLowerCase(), finalPos.getX(), finalPos.getY(), finalPos.getZ(),
                    result.message());
        } catch (Exception e) {
            return "Failed to execute goTo: " + e.getMessage();
        }
    }
}
