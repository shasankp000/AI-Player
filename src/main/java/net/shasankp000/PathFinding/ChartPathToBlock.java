package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Entity.LookController;
import net.shasankp000.PlayerUtils.blockDetectionUnit;

import java.util.Objects;

public class ChartPathToBlock {

    public static String chart(ServerPlayer bot, BlockPos targetBlockPos, String blockType) {
        CommandSourceStack botSource = bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
        MinecraftServer server = bot.createCommandSourceStack().getServer();
        String botName = bot.getName().getString();


        // Start micro nav
        while (true) {
            // Face the block
            LookController.faceBlock(bot, targetBlockPos);

            Objects.requireNonNull(server).getCommands().performPrefixedCommand(botSource, "/player " + botName + " move forward");

            // If the bot collides with a block, stop
            Vec3 nextPos = bot.position().add(bot.getViewVector(1.0f).scale(0.1));

            // Convert manually to Vec3i
            Vec3i nextPosInt = new Vec3i(
                    Mth.floor(nextPos.x),
                    Mth.floor(nextPos.y),
                    Mth.floor(nextPos.z)
            );

            if (bot.level().getBlockState(new BlockPos(nextPosInt)).canOcclude()) {
                Objects.requireNonNull(server).getCommands().performPrefixedCommand(botSource, "/player " + botName + " stop");

                // Check if it’s the correct block
                BlockPos hitPos = blockDetectionUnit.detectBlocks(bot, blockType); // returns BlockPos instead of string
                if (hitPos.equals(targetBlockPos)) {
                    System.out.println("Bot is now in front of the target block!");
                    return "Bot is now in front of the target block! Bot is at " + bot.blockPosition().getX() + " " + bot.blockPosition().getY() + " " + bot.blockPosition().getZ();
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
