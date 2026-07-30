package net.shasankp000.PlayerUtils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class hotBarUtils {
    public static List<ItemStack> getHotbarItems(ServerPlayer bot) {
        List<ItemStack> hotbarItems = new ArrayList<>();

        if (bot != null) {
            for (int i = 0; i < 9; i++) {
                hotbarItems.add(bot.getInventory().getItem(i));
            }
        }

        return hotbarItems;
    }

    public static ItemStack getSelectedHotbarItemStack(ServerPlayer bot) {

        // Ensure the client and player are not null

        // Get the selected slot's stack
        int selectedSlot = bot.getInventory().getSelectedSlot();
        ItemStack selectedStack = bot.getInventory().getItem(selectedSlot);

        // Check if the slot is not empty
        if (!selectedStack.isEmpty()) {
            // Return the translation key of the item

            return selectedStack;
        }


        // Return a placeholder if there's no item in the selected slot
        return ItemStack.EMPTY;
    }
}
