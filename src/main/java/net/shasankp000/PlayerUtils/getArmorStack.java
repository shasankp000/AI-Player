package net.shasankp000.PlayerUtils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class getArmorStack {

    public static Map<String, ItemStack> getArmorItems(ServerPlayer bot) {
        Map<String, ItemStack> armorItems = new HashMap<>();
        armorItems.put("helmet", bot.getItemBySlot(EquipmentSlot.HEAD));
        armorItems.put("chestplate", bot.getItemBySlot(EquipmentSlot.CHEST));
        armorItems.put("leggings", bot.getItemBySlot(EquipmentSlot.LEGS));
        armorItems.put("boots", bot.getItemBySlot(EquipmentSlot.FEET));
        return armorItems;
    }


}

