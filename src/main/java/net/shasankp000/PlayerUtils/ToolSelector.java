package net.shasankp000.PlayerUtils;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ToolSelector {

    public static ItemStack selectBestToolForBlock(ServerPlayer bot, BlockState blockState) {
        List<ItemStack> hotbarItems = hotBarUtils.getHotbarItems(bot);
        ItemStack bestTool = ItemStack.EMPTY;
        float highestSpeed = 0.0f;

        for (ItemStack item : hotbarItems) {
            if (item.isEmpty()) continue;

            float speed = item.getDestroySpeed(blockState);
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

