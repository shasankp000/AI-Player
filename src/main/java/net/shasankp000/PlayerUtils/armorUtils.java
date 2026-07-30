package net.shasankp000.PlayerUtils;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.datafixers.util.Pair; // Import the correct Pair class

import java.util.ArrayList;
import java.util.List;


public class armorUtils {
    public static void autoEquipArmor(ServerPlayerEntity bot) {
        PlayerInventory inventory = bot.getInventory();

        // Prepare a list of equipment updates to notify clients
        List<Pair<EquipmentSlot, ItemStack>> equipmentUpdates = new ArrayList<>();

        // Iterate through all armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equippedArmor = bot.getEquippedStack(slot);

            // Find the best armor piece in the inventory for this slot
            ItemStack bestArmor = findBestArmor(inventory, slot);

            // Equip the armor if it's better than what's currently equipped
            if (!bestArmor.isEmpty() && (equippedArmor.isEmpty() || isBetterArmor(bestArmor, equippedArmor))) {
                bot.equipStack(slot, bestArmor);
                inventory.removeOne(bestArmor); // Remove the equipped armor from inventory
                System.out.println("Equipped " + bestArmor.getName().getString() + " in slot " + slot.getName());

                // Add this update to the list for notifying clients
                equipmentUpdates.add(new Pair<>(slot, bestArmor)); // Use com.mojang.datafixers.util.Pair

                bot.getInventory().armor.set(slot.getEntitySlotId(), bestArmor.copy()); // update the armor slots data for the server for the bot.
            }
        }

        // Send the equipment update packet to all nearby players
        if (!equipmentUpdates.isEmpty()) {
            bot.getServerWorld().getPlayers().forEach(player ->
                    player.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(bot.getId(), equipmentUpdates))
            );
        }
    }

    // Helper method to find the best armor for a specific slot
    private static ItemStack findBestArmor(PlayerInventory inventory, EquipmentSlot slot) {
        ItemStack bestArmor = ItemStack.EMPTY;
        int bestProtection = 0;

        for (ItemStack item : inventory.main) {
            if (item.getItem() instanceof ArmorItem armorItem && armorItem.getSlotType() == slot) {
                int protection = armorItem.getProtection();

                // Replace the bestArmor if the current item has higher protection
                if (protection > bestProtection) {
                    bestProtection = protection;
                    bestArmor = item;
                }
            }
        }
        return bestArmor;
    }

    // Helper method to compare two armor pieces
    private static boolean isBetterArmor(ItemStack newArmor, ItemStack currentArmor) {
        if (newArmor.isEmpty() || !(newArmor.getItem() instanceof ArmorItem)) {
            return false;
        }

        if (currentArmor.isEmpty() || !(currentArmor.getItem() instanceof ArmorItem)) {
            return true;
        }

        int newProtection = ((ArmorItem) newArmor.getItem()).getProtection();
        int currentProtection = ((ArmorItem) currentArmor.getItem()).getProtection();

        return newProtection > currentProtection;
    }

    public static void autoDeEquipArmor(ServerPlayerEntity bot) {
        // still a work-in-progress.

        PlayerInventory inventory = bot.getInventory();

        // Prepare a list of equipment updates to notify clients
        List<Pair<EquipmentSlot, ItemStack>> equipmentUpdates = new ArrayList<>();



        // Iterate through all armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equippedArmor = bot.getEquippedStack(slot);

            System.out.println(equippedArmor.getName().getString());

            // If the bot has armor equipped in this slot
            if (!equippedArmor.isEmpty()) {
                // Add the armor back to the inventory
                if (inventory.insertStack(equippedArmor)) {
                    // Clear the equipped armor slot
                    bot.equipStack(slot, ItemStack.EMPTY);

                    // Add this update to the list for notifying clients
                    equipmentUpdates.add(new Pair<>(slot, ItemStack.EMPTY));

                    System.out.println("De-equipped " + equippedArmor.getName().getString() + " from slot " + slot.getName());
                } else {
                    System.out.println("Inventory full! Could not de-equip " + equippedArmor.getName().getString() + " from slot " + slot.getName());
                }
            }
        }

        // Send the equipment update packet to all nearby players
        if (!equipmentUpdates.isEmpty()) {
            bot.getServerWorld().getPlayers().forEach(player ->
                    player.networkHandler.sendPacket(new EntityEquipmentUpdateS2CPacket(bot.getId(), equipmentUpdates))
            );
        }
    }


}
