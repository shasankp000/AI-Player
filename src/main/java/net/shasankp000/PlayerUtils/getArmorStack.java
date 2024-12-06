package net.shasankp000.PlayerUtils;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class getArmorStack {

    public static Map<String, ItemStack> getArmorItems(ServerPlayerEntity bot) {
        Map<String, ItemStack> armorItems = new HashMap<>();
        armorItems.put("helmet", bot.getEquippedStack(EquipmentSlot.HEAD));
        armorItems.put("chestplate", bot.getEquippedStack(EquipmentSlot.CHEST));
        armorItems.put("leggings", bot.getEquippedStack(EquipmentSlot.LEGS));
        armorItems.put("boots", bot.getEquippedStack(EquipmentSlot.FEET));
        return armorItems;
    }


}

