package net.shasankp000.WorldUitls;

import net.minecraft.item.ItemStack;


public class isFoodItem {

    // Check if the given item is a food item
    public static boolean checkFoodItem(ItemStack selectedItemStack) {
        // Get the Item instance from the item registry

        return selectedItemStack.getItem().isFood();
    }


}
