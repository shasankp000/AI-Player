package net.shasankp000.PlayerUtils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Utility class for intelligent weapon management - Refactored for 1.21.4
 */
public class WeaponUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("weapon-utils");

    public static class WeaponAnalysis {
        public final ItemStack weapon;
        public final int slot;
        public final double attackDamage;
        public final double attackSpeed;
        public final double dps;
        public final String weaponType;
        public final int enchantmentLevel;

        public WeaponAnalysis(ItemStack weapon, int slot, double attackDamage, double attackSpeed,
                              String weaponType, int enchantmentLevel) {
            this.weapon = weapon;
            this.slot = slot;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
            this.dps = attackDamage * attackSpeed;
            this.weaponType = weaponType;
            this.enchantmentLevel = enchantmentLevel;
        }

        public double getOverallScore() {
            return dps + (enchantmentLevel * 0.5);
        }
    }

    public static int findBestMeleeWeapon(ServerPlayerEntity bot) {
        LOGGER.debug("🗡 Analyzing bot's inventory for best melee weapon...");

        List<WeaponAnalysis> weapons = new ArrayList<>();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            // Updated check: Does it have damage modifiers or is it a known weapon class?
            if (isMeleeWeapon(stack)) {
                double attackDamage = getAttackDamage(stack);
                double attackSpeed = getAttackSpeed(stack);
                String weaponType = getWeaponType(stack.getItem());
                int enchantmentLevel = analyzeEnchantments(stack);

                WeaponAnalysis analysis = new WeaponAnalysis(
                    stack, slot, attackDamage, attackSpeed, weaponType, enchantmentLevel
                );

                weapons.add(analysis);

                LOGGER.debug("  Slot {}: {} - DMG: {}, SPD: {}, DPS: {}, Score: {}",
                    slot, weaponType, String.format("%.1f", attackDamage),
                    String.format("%.2f", attackSpeed), String.format("%.2f", analysis.dps),
                    String.format("%.2f", analysis.getOverallScore()));
            }
        }

        if (weapons.isEmpty()) {
            LOGGER.debug("❌ No melee weapons found in inventory");
            return -1;
        }

        weapons.sort((w1, w2) -> Double.compare(w2.getOverallScore(), w1.getOverallScore()));

        // .getFirst() is fine in Java 21+, otherwise use .get(0)
        WeaponAnalysis best = weapons.get(0);
        LOGGER.info("⚔ Best weapon: {} in slot {} (DMG: {}, DPS: {}, Score: {})",
            best.weaponType, best.slot, String.format("%.1f", best.attackDamage),
            String.format("%.2f", best.dps), String.format("%.2f", best.getOverallScore()));

        return best.slot;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        Item item = stack.getItem();
        // Modern check: Does it have the Tool component or Attribute Modifiers?
        return item instanceof SwordItem ||
               item instanceof AxeItem ||
               item instanceof MaceItem ||
               item instanceof TridentItem ||
               stack.getComponents().contains(DataComponentTypes.ATTRIBUTE_MODIFIERS);
    }

    private static String getWeaponType(Item item) {
        if (item instanceof SwordItem) return "Sword";
        if (item instanceof AxeItem) return "Axe";
        if (item instanceof TridentItem) return "Trident";
        if (item instanceof MaceItem) return "Mace";
        if (item instanceof PickaxeItem) return "Pickaxe";
        if (item instanceof ShovelItem) return "Shovel";
        if (item instanceof HoeItem) return "Hoe";
        return "Custom/Tool";
    }

    private static double getAttackDamage(ItemStack stack) {
        // Read directly from the new Data Component system
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        double damage = 1.0; // Base hand damage

        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                // We only care about damage applied to the main hand
                if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE) &&
                    entry.slot().matches(net.minecraft.entity.EquipmentSlot.MAINHAND)) {
                    damage += entry.modifier().value();
                }
            }
        }
        return damage;
    }

    private static double getAttackSpeed(ItemStack stack) {
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        double speed = 4.0; // Default player attack speed

        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.ATTACK_SPEED) &&
                    entry.slot().matches(net.minecraft.entity.EquipmentSlot.MAINHAND)) {
                    // This modifier is usually negative (e.g., -2.4), so speed becomes 1.6
                    speed += entry.modifier().value();
                }
            }
        }
        return speed;
    }

    private static int analyzeEnchantments(ItemStack stack) {
        int score = 0;
        // 1.21.4 Way: Get the Enchantments component
        var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        
        if (enchantments != null && !enchantments.isEmpty()) {
            // Add score based on total number of levels
            score += enchantments.getEnchantments().size() * 5;
        }

        if (stack.hasGlint()) {
            score += 2;
        }

        return score;
    }

    public static boolean equipBestMeleeWeapon(ServerPlayerEntity bot) {
        int bestWeaponSlot = findBestMeleeWeapon(bot);

        if (bestWeaponSlot == -1) {
            LOGGER.warn("No melee weapons available to equip");
            return false;
        }

        // Field access for selectedSlot in 1.21.4 Yarn
        if (bot.getInventory().selectedSlot == bestWeaponSlot) {
            LOGGER.debug("✓ Best weapon already equipped");
            return true;
        }

        try {
            if (bestWeaponSlot >= 9) {
                // Use built-in swap helper
                bot.getInventory().swapSlotWithHotbar(bestWeaponSlot);
                bot.getInventory().selectedSlot = 0;
            } else {
                bot.getInventory().selectedSlot = bestWeaponSlot;
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to equip best weapon: {}", e.getMessage());
            return false;
        }
    }

    public static int calculateOptimalDrawTime(double distanceToTarget) {
        if (distanceToTarget <= 7.0) return 12;
        if (distanceToTarget <= 15.0) return 18;
        return 20; // Max draw
    }

    public static double calculateProjectileSpeed(int drawTime) {
        double speedFraction = Math.min(drawTime / 20.0, 1.0);
        return 1.0 + (2.0 * speedFraction);
    }
}