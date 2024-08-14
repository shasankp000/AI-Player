package net.shasankp000.PathFinding;


import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.shasankp000.PathFinding.PathFinder.calculatePath;
import static net.shasankp000.PathFinding.PathFinder.simplifyPath;
import static net.shasankp000.PathFinding.PathTracer.tracePathOutput;

public class GoTo {

    public static String goTo(ServerCommandSource botSource, int x, int y, int z) {

            List<BlockPos> path;

            MinecraftServer server = botSource.getServer();
            ServerPlayerEntity bot = botSource.getPlayer();

            if (bot!=null) {

                System.out.println("Found bot: " + botSource.getName() );
                path = calculatePath(bot.getBlockPos(), new BlockPos(x, y, z));
                path = simplifyPath(path);
            }
            else {

                System.out.println("Bot not found!");
                throw new RuntimeException();
            }


        return tracePathOutput(server, botSource, botSource.getName(), path);  // Use join() to wait for the future to complete

    }


}
