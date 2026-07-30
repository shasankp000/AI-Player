package net.shasankp000.PlayerUtils;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class hotBarUtils {
    public static List<ItemStack> getHotbarItems(ServerPlayerEntity bot) {
        List<ItemStack> hotbarItems = new ArrayList<>();

        if (bot != null) {
            for (int i = 0; i < 9; i++) {
                hotbarItems.add(bot.getInventory().getStack(i));
            }
        }

        return hotbarItems;
    }

    public static ItemStack getSelectedHotbarItemStack(ServerPlayerEntity bot) {

        // Ensure the client and player are not null

        // Get the selected slot's stack
        int selectedSlot = bot.getInventory().selectedSlot;
        ItemStack selectedStack = bot.getInventory().getStack(selectedSlot);

        // Check if the slot is not empty
        if (!selectedStack.isEmpty()) {
            // Return the translation key of the item

            return selectedStack;
        }


        // Return a placeholder if there's no item in the selected slot
        return ItemStack.EMPTY;
    }
}
