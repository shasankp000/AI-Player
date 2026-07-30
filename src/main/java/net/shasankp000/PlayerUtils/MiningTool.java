package net.shasankp000.PlayerUtils;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;

import java.util.concurrent.*;

import net.shasankp000.Entity.LookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiningTool {

    private static final long ATTACK_INTERVAL_MS = 200;
    public static final Logger LOGGER = LoggerFactory.getLogger("mining-tool");

    public static CompletableFuture<String> mineBlock(ServerPlayerEntity bot, BlockPos targetBlockPos) {
        CompletableFuture<String> miningResult = new CompletableFuture<>();
        try {


                ScheduledExecutorService miningExecutor = Executors.newSingleThreadScheduledExecutor();

                // Step 1: Face the block
                LookController.faceBlock(bot, targetBlockPos);

                // Step 2: Select best tool
                BlockState blockState = bot.getWorld().getBlockState(targetBlockPos);
                ItemStack bestTool = ToolSelector.selectBestToolForBlock(bot, blockState);

                // Step 3: Switch to that tool
                switchToTool(bot, bestTool);

                // Step 4: Start mining loop
                ScheduledFuture<?> task = miningExecutor.scheduleAtFixedRate(() -> {
                    BlockState currentState = bot.getWorld().getBlockState(targetBlockPos);

                    if (currentState.isAir()) {
                        System.out.println("✅ Mining complete!");
                        miningResult.complete("Mining complete!");
                        miningExecutor.shutdownNow();
                        return;
                    }

                    bot.swingHand(bot.getActiveHand());
                    bot.interactionManager.tryBreakBlock(targetBlockPos);
                    System.out.println("⛏️ Mining...");

                }, 0, ATTACK_INTERVAL_MS, TimeUnit.MILLISECONDS);

                // In case something else cancels this process
                miningResult.whenComplete((result, error) -> {
                    if (!task.isCancelled() && !task.isDone()) {
                        task.cancel(true);
                    }
                    if (!miningExecutor.isShutdown()) {
                        miningExecutor.shutdownNow();
                    }
                });


        }
        catch (Exception e) {
            LOGGER.error("Error in mining tool! {}", e.getMessage());
        }

        return miningResult;
    }

    private static void switchToTool(ServerPlayerEntity bot, ItemStack tool) {
        for (int i = 0; i < 9; i++) {
            if (bot.getInventory().getStack(i) == tool) {
                bot.getInventory().selectedSlot = i;
                break;
            }
        }
    }

}


