package net.shasankp000.PlayerUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Utility class for intelligent weapon management
 * Handles automatic weapon selection for optimal combat effectiveness
 */
public class WeaponUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("weapon-utils");

    /**
     * Weapon analysis result
     */
    public static class WeaponAnalysis {
        public final ItemStack weapon;
        public final int slot;
        public final double attackDamage;
        public final double attackSpeed;
        public final double dps; // Damage Per Second
        public final String weaponType;
        public final int enchantmentLevel; // Total enchantment quality

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
            // Weighted scoring: DPS + enchantment quality
            return dps + (enchantmentLevel * 0.5);
        }
    }

    /**
     * Find the best melee weapon in bot's inventory
     * Returns slot number (0-8 for hotbar, 9-35 for main inventory)
     * Returns -1 if no weapon found
     */
    public static int findBestMeleeWeapon(ServerPlayer bot) {
        LOGGER.debug("🗡 Analyzing bot's inventory for best melee weapon...");

        List<WeaponAnalysis> weapons = new ArrayList<>();

        // Scan hotbar (slots 0-8) and main inventory (slots 9-35)
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Check if item is a melee weapon
            if (isMeleeWeapon(item)) {
                double attackDamage = getAttackDamage(stack);
                double attackSpeed = getAttackSpeed(stack);
                String weaponType = getWeaponType(item);
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

        // Sort by overall score (DPS + enchantments)
        weapons.sort((w1, w2) -> Double.compare(w2.getOverallScore(), w1.getOverallScore()));

        WeaponAnalysis best = weapons.getFirst();
        LOGGER.info("⚔ Best weapon: {} in slot {} (DMG: {}, DPS: {}, Score: {})",
            best.weaponType, best.slot, String.format("%.1f", best.attackDamage),
            String.format("%.2f", best.dps), String.format("%.2f", best.getOverallScore()));

        return best.slot;
    }

    /**
     * Check if an item is a melee weapon
     */
    private static boolean isMeleeWeapon(Item item) {
        String itemId = itemId(item);
        return itemId.endsWith("_sword") ||
               itemId.endsWith("_axe") ||
               itemId.endsWith("_pickaxe") ||
               itemId.endsWith("_shovel") ||
               itemId.endsWith("_hoe") ||
               itemId.endsWith("trident") ||
               itemId.endsWith("mace");
    }

    /**
     * Get weapon type name
     */
    private static String getWeaponType(Item item) {
        String itemId = itemId(item);
        if (itemId.endsWith("_sword")) return "Sword";
        if (itemId.endsWith("_axe")) return "Axe";
        if (itemId.endsWith("trident")) return "Trident";
        if (itemId.endsWith("mace")) return "Mace";
        if (itemId.endsWith("_pickaxe")) return "Pickaxe";
        if (itemId.endsWith("_shovel")) return "Shovel";
        if (itemId.endsWith("_hoe")) return "Hoe";
        return "Unknown";
    }

    /**
     * Get attack damage from item stack
     */
    private static double getAttackDamage(ItemStack stack) {
        String itemId = itemId(stack.getItem());
        if (itemId.endsWith("trident")) return 9.0;
        if (itemId.endsWith("mace")) return 6.0;

        double materialBonus = materialScore(itemId);
        if (itemId.endsWith("_sword")) return materialBonus + 3.0;
        if (itemId.endsWith("_axe")) return materialBonus + 5.0;
        if (itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel") || itemId.endsWith("_hoe")) return materialBonus + 1.0;
        return 1.0;
    }

    /**
     * Get attack speed from item stack
     */
    private static double getAttackSpeed(ItemStack stack) {
        String itemId = itemId(stack.getItem());

        // Default attack speeds for different weapon types
        double attackSpeed = 4.0; // Default (fist/no weapon)

        if (itemId.endsWith("_sword")) {
            attackSpeed = 1.6; // Swords are fast
        } else if (itemId.endsWith("_axe")) {
            // Axes are slower but hit harder
            if (itemId.contains("wooden") || itemId.contains("golden") || itemId.contains("stone")) {
                attackSpeed = 0.8;
            } else if (itemId.contains("iron")) {
                attackSpeed = 0.9;
            } else if (itemId.contains("diamond") || itemId.contains("netherite")) {
                attackSpeed = 1.0;
            }
        } else if (itemId.endsWith("trident")) {
            attackSpeed = 1.1; // Trident is fairly fast
        } else if (itemId.endsWith("mace")) {
            attackSpeed = 0.7; // Mace is slow but powerful
        } else if (itemId.endsWith("_pickaxe") || itemId.endsWith("_shovel") || itemId.endsWith("_hoe")) {
            attackSpeed = 1.2; // Tools (pickaxe, shovel, hoe) are moderately fast
        }

        return attackSpeed;
    }

    /**
     * Analyze enchantments and return total quality score
     * Note: In 1.20.6+, enchantments are stored in DataComponents
     * For simplicity, we use item rarity/material as a proxy for enchantment quality
     */
    private static int analyzeEnchantments(ItemStack stack) {
        int score = 0;

        score += (int) materialScore(itemId(stack.getItem()));

        // Check for enchantment glint (indicates item is enchanted)
        if (stack.hasFoil()) {
            score += 5; // Bonus for any enchantments
        }

        return score;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static double materialScore(String itemId) {
        if (itemId.contains("netherite")) return 10.0;
        if (itemId.contains("diamond")) return 8.0;
        if (itemId.contains("iron")) return 5.0;
        if (itemId.contains("golden")) return 4.0;
        if (itemId.contains("stone")) return 3.0;
        if (itemId.contains("wooden")) return 2.0;
        return 1.0;
    }

    /**
     * Equip the best melee weapon from inventory
     * Returns true if weapon was successfully equipped, false otherwise
     */
    public static boolean equipBestMeleeWeapon(ServerPlayer bot) {
        int bestWeaponSlot = findBestMeleeWeapon(bot);

        if (bestWeaponSlot == -1) {
            LOGGER.warn("No melee weapons available to equip");
            return false;
        }

        // If weapon is already in hand, we're done
        if (bot.getInventory().getSelectedSlot() == bestWeaponSlot && bestWeaponSlot < 9) {
            LOGGER.debug("✓ Best weapon already equipped");
            return true;
        }

        try {
            // If weapon is in main inventory (slot 9-35), swap it to hotbar first
            if (bestWeaponSlot >= 9) {
                // Find empty hotbar slot or use slot 0
                int targetHotbarSlot = 0;
                for (int i = 0; i < 9; i++) {
                    if (bot.getInventory().getItem(i).isEmpty()) {
                        targetHotbarSlot = i;
                        break;
                    }
                }

                // Swap weapon to hotbar
                ItemStack weaponStack = bot.getInventory().getItem(bestWeaponSlot);
                ItemStack hotbarStack = bot.getInventory().getItem(targetHotbarSlot);
                bot.getInventory().setItem(bestWeaponSlot, hotbarStack);
                bot.getInventory().setItem(targetHotbarSlot, weaponStack);

                LOGGER.info("Moved weapon from slot {} to hotbar slot {}", bestWeaponSlot, targetHotbarSlot);
                bestWeaponSlot = targetHotbarSlot;
            }

            // Select the hotbar slot with the best weapon
            bot.getInventory().setSelectedSlot(bestWeaponSlot);
            LOGGER.info("✅ Equipped best melee weapon from slot {}", bestWeaponSlot);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to equip best weapon: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Calculate optimal bow draw time based on distance to target
     * Returns draw time in TICKS (20 ticks = 1 second)
     *
     * Strategy:
     * - Close range (0-7m): Quick shots (10-15 ticks) for rapid fire
     * - Medium range (7-15m): Medium draw (15-20 ticks) for balance
     * - Long range (15-30m): Full draw (20-25 ticks) for maximum velocity
     * - Very long range (30+m): Maximum draw (25 ticks) for best accuracy
     */
    public static int calculateOptimalDrawTime(double distanceToTarget) {
        int drawTime;

        if (distanceToTarget <= 7.0) {
            // CLOSE QUARTERS: Rapid fire mode (50-75% draw)
            drawTime = (int) (10 + (distanceToTarget / 7.0) * 5); // 10-15 ticks
            LOGGER.debug("⚡ Close quarters rapid fire: {} ticks for {}m", drawTime, String.format("%.1f", distanceToTarget));
        } else if (distanceToTarget <= 15.0) {
            // MEDIUM RANGE: Balanced draw (75-100% draw)
            drawTime = (int) (15 + ((distanceToTarget - 7.0) / 8.0) * 5); // 15-20 ticks
            LOGGER.debug("🎯 Medium range balanced shot: {} ticks for {}m", drawTime, String.format("%.1f", distanceToTarget));
        } else if (distanceToTarget <= 30.0) {
            // LONG RANGE: Full draw for velocity (100-125% draw)
            drawTime = (int) (20 + ((distanceToTarget - 15.0) / 15.0) * 5); // 20-25 ticks
            LOGGER.debug("🏹 Long range full draw: {} ticks for {}m", drawTime, String.format("%.1f", distanceToTarget));
        } else {
            // VERY LONG RANGE: Maximum draw (125% draw)
            drawTime = 25;
            LOGGER.debug("🎯 Sniper mode max draw: {} ticks for {}m", drawTime, String.format("%.1f", distanceToTarget));
        }

        return drawTime;
    }

    /**
     * Calculate projectile speed based on bow draw time
     * Full draw (20 ticks) = 3.0 blocks/tick
     * Partial draw scales linearly
     */
    public static double calculateProjectileSpeed(int drawTime) {
        // Minecraft bow mechanics: speed increases with draw time up to 20 ticks
        double maxSpeed = 3.0; // Fully charged arrow
        double minSpeed = 1.0; // Uncharged arrow

        // Linear scaling from 0 to 20 ticks
        double speedFraction = Math.min(drawTime / 20.0, 1.0);
        return minSpeed + (maxSpeed - minSpeed) * speedFraction;
    }
}
