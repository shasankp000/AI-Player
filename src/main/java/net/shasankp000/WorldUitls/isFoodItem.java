package net.shasankp000.WorldUitls;

import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;


public class isFoodItem {

    // Check if the given item is a food item
    public static boolean checkFoodItem(ItemStack selectedItemStack) {
        // 1.20.6, get the Item's component map, run it against DataComponentTypes to check if it's a food

        ComponentMap componentMap = selectedItemStack.getComponents();

        return componentMap.contains(DataComponentTypes.FOOD);

    }


}
