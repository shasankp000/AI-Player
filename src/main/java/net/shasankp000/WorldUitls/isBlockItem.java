package net.shasankp000.WorldUitls;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public class isBlockItem {

    // Check if the given item is a food item
    public static boolean checkBlockItem(ItemStack selectedItemStack) {
        // Get the Item instance from the item registry

        return (selectedItemStack.getItem() instanceof BlockItem);
    }

}
