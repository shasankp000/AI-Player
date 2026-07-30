package net.shasankp000.PathFinding;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.shasankp000.Entity.LookController;
import net.shasankp000.PlayerUtils.blockDetectionUnit;

import java.util.Objects;

public class ChartPathToBlock {

    public static String chart(ServerPlayerEntity bot, BlockPos targetBlockPos, String blockType) {
        ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);
        MinecraftServer server = bot.getServer();
        String botName = bot.getName().getString();


        // Start micro nav
        while (true) {
            // Face the block
            LookController.faceBlock(bot, targetBlockPos);

            Objects.requireNonNull(server).getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move forward");

            // If the bot collides with a block, stop
            Vec3d nextPos = bot.getPos().add(bot.getRotationVec(1.0f).multiply(0.1));

            // Convert manually to Vec3i
            Vec3i nextPosInt = new Vec3i(
                    MathHelper.floor(nextPos.x),
                    MathHelper.floor(nextPos.y),
                    MathHelper.floor(nextPos.z)
            );

            if (bot.getWorld().getBlockState(new BlockPos(nextPosInt)).isOpaque()) {
                Objects.requireNonNull(server).getCommandManager().executeWithPrefix(botSource, "/player " + botName + " stop");

                // Check if it’s the correct block
                BlockPos hitPos = blockDetectionUnit.detectBlocks(bot, blockType); // returns BlockPos instead of string
                if (hitPos.equals(targetBlockPos)) {
                    System.out.println("Bot is now in front of the target block!");
                    return "Bot is now in front of the target block! Bot is at " + bot.getBlockPos().getX() + " " + bot.getBlockPos().getY() + " " + bot.getBlockPos().getZ();
                } else {
                    System.out.println("Hit obstacle that is not target block! Need to adjust.");
                    return "Hit obstacle that is not target block! Need to adjust.";
                }
            }

            // Sleep for a short tick (pseudo)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
