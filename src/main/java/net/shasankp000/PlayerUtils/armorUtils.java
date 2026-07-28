package net.shasankp000.PlayerUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.equipment.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.datafixers.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class armorUtils {
    public static void autoEquipArmor(ServerPlayerEntity bot) {
        PlayerInventory inventory = bot.getInventory();
        List<Pair<EquipmentSlot, ItemStack>> equipmentUpdates = new ArrayList<>();

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equippedArmor = bot.getEquippedStack(slot);
            ItemStack bestArmor = findBestArmor(inventory, slot);

            if (!bestArmor.isEmpty() && (equippedArmor.isEmpty() || isBetterArmor(bestArmor, equippedArmor))) {
                bot.equipStack(slot, bestArmor.copy());
                inventory.removeOne(bestArmor);
                System.out.println("Equipped " + bestArmor.getName().getString() + " in slot " + slot.getName());

                equipmentUpdates.add(new Pair<>(slot, bot.getEquippedStack(slot)));

                int armorSlotId = getArmorSlotId(slot);
                if (armorSlotId != -1) {
                    bot.getInventory().armor.set(armorSlotId, bot.getEquippedStack(slot).copy());
                }
            }
        }

        if (!equipmentUpdates.isEmpty()) {
            bot.getServerWorld().getPlayers().forEach(player ->
                    player.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(bot.getId(), equipmentUpdates))
            );
        }
    }

	private static ItemStack findBestArmor(PlayerInventory inventory, EquipmentSlot slot) {
		ItemStack bestArmor = ItemStack.EMPTY;
		int bestProtection = -1;

		for (ItemStack itemStack : inventory.main) {
			var equippable = itemStack.get(DataComponentTypes.EQUIPPABLE);

			if (equippable != null && equippable.slot() == slot) {
				int protection = getProtectionValue(itemStack);
				if (protection > bestProtection) {
					bestProtection = protection;
					bestArmor = itemStack;
				}
			}
		}
		return bestArmor;
	}

    private static boolean isBetterArmor(ItemStack newArmor, ItemStack currentArmor) {
        if (newArmor.isEmpty()) return false;
        if (currentArmor.isEmpty()) return true;
        return getProtectionValue(newArmor) > getProtectionValue(currentArmor);
    }

    private static int getProtectionValue(ItemStack stack) {
        var modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            for (var entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.ARMOR)) {
                    return (int) entry.modifier().value();
                }
            }
        }
        return 0;
    }

    private static int getArmorSlotId(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
            default -> -1;
        };
    }

    public static void autoDeEquipArmor(ServerPlayerEntity bot) {
        PlayerInventory inventory = bot.getInventory();
        List<Pair<EquipmentSlot, ItemStack>> equipmentUpdates = new ArrayList<>();

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equippedArmor = bot.getEquippedStack(slot);

            if (!equippedArmor.isEmpty()) {
                System.out.println(equippedArmor.getName().getString());
                if (inventory.insertStack(equippedArmor.copy())) {
                    bot.equipStack(slot, ItemStack.EMPTY);
                    equipmentUpdates.add(new Pair<>(slot, ItemStack.EMPTY));

                    int armorSlotId = getArmorSlotId(slot);
                    if (armorSlotId != -1) {
                        bot.getInventory().armor.set(armorSlotId, ItemStack.EMPTY);
                    }
                    System.out.println("De-equipped " + equippedArmor.getName().getString() + " from slot " + slot.getName());
                }
            }
        }

        if (!equipmentUpdates.isEmpty()) {
            bot.getServerWorld().getPlayers().forEach(player ->
                    player.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(bot.getId(), equipmentUpdates))
            );
        }
    }
}