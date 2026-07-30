package net.shasankp000.PlayerUtils;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class ToolSelector {

    public static ItemStack selectBestToolForBlock(ServerPlayerEntity bot, BlockState blockState) {
        List<ItemStack> hotbarItems = hotBarUtils.getHotbarItems(bot);
        ItemStack bestTool = ItemStack.EMPTY;
        float highestSpeed = 0.0f;

        for (ItemStack item : hotbarItems) {
            if (item.isEmpty()) continue;

            float speed = item.getMiningSpeedMultiplier(blockState);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                bestTool = item;
            }
        }

        // If none has a speed > 1.0, just use whatever is selected
        if (highestSpeed <= 1.0f) {
            return hotBarUtils.getSelectedHotbarItemStack(bot);
        }

        return bestTool;
    }
}

