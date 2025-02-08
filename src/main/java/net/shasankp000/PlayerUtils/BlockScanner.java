package net.shasankp000.PlayerUtils;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

public class BlockScanner {
    public static CompletableFuture<List<String>> detectNearbyBlocksAsync(ServerPlayerEntity bot, int radius) {
        return CompletableFuture.supplyAsync(() -> detectNearbyBlocks(bot, radius));
    }

    private static List<String> detectNearbyBlocks(ServerPlayerEntity bot, int radius) {
        List<String> blocks = new ArrayList<>();
        BlockPos botPos = bot.getBlockPos();
        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;

                    BlockPos pos = botPos.add(dx, dy, dz);
                    Block block = bot.getEntityWorld().getBlockState(pos).getBlock();
                    String blockName = block.getName().getString();
                    blocks.add(blockName);

                }
            }
        }
        return blocks;
    }
}

