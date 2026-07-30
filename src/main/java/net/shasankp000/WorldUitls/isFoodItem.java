package net.shasankp000.WorldUitls;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;


public class isFoodItem {

    // Check if the given item is a food item
    public static boolean checkFoodItem(ItemStack selectedItemStack) {
        // 1.20.6, get the Item's component map, run it against DataComponentTypes to check if it's a food

        DataComponentMap componentMap = selectedItemStack.getComponents();

        return componentMap.has(DataComponents.FOOD);

    }


}
