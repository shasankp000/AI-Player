package net.shasankp000.PlayerUtils;

import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
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
    public static int findBestMeleeWeapon(ServerPlayerEntity bot) {
        LOGGER.debug("🗡 Analyzing bot's inventory for best melee weapon...");

        List<WeaponAnalysis> weapons = new ArrayList<>();

        // Scan hotbar (slots 0-8) and main inventory (slots 9-35)
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.getInventory().getStack(slot);
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
        return item instanceof SwordItem ||
               item instanceof AxeItem ||
               item instanceof TridentItem ||
               item instanceof MaceItem ||
               item instanceof PickaxeItem ||
               item instanceof ShovelItem ||
               item instanceof HoeItem;
    }

    /**
     * Get weapon type name
     */
    private static String getWeaponType(Item item) {
        if (item instanceof SwordItem) return "Sword";
        if (item instanceof AxeItem) return "Axe";
        if (item instanceof TridentItem) return "Trident";
        if (item instanceof MaceItem) return "Mace";
        if (item instanceof PickaxeItem) return "Pickaxe";
        if (item instanceof ShovelItem) return "Shovel";
        if (item instanceof HoeItem) return "Hoe";
        return "Unknown";
    }

    /**
     * Get attack damage from item stack
     */
    private static double getAttackDamage(ItemStack stack) {
        Item item = stack.getItem();

        // Get base attack damage from item attributes
        double baseDamage = 1.0; // Default fist damage

        if (item instanceof ToolItem toolItem) {
            // Tools have attack damage from material
            baseDamage = toolItem.getMaterial().getAttackDamage();
        } else if (item instanceof SwordItem swordItem) {
            // Swords have attack damage from material + bonus
            baseDamage = swordItem.getMaterial().getAttackDamage() + 3.0; // Sword bonus
        } else if (item instanceof TridentItem) {
            baseDamage = 9.0; // Trident base damage
        } else if (item instanceof MaceItem) {
            baseDamage = 6.0; // Mace base damage (1.21+)
        }

        // Add Sharpness enchantment bonus (if present)
        // Note: In 1.20.6+, enchantments are stored in components, not NBT
        // For simplicity, we'll skip enchantment bonuses in base damage calculation
        // since weapons will still be ranked correctly by material/type

        return baseDamage;
    }

    /**
     * Get attack speed from item stack
     */
    private static double getAttackSpeed(ItemStack stack) {
        Item item = stack.getItem();

        // Default attack speeds for different weapon types
        double attackSpeed = 4.0; // Default (fist/no weapon)

        if (item instanceof SwordItem) {
            attackSpeed = 1.6; // Swords are fast
        } else if (item instanceof AxeItem) {
            // Axes are slower but hit harder
            ToolMaterial material = ((AxeItem) item).getMaterial();
            if (material == ToolMaterials.WOOD || material == ToolMaterials.GOLD) {
                attackSpeed = 0.8;
            } else if (material == ToolMaterials.STONE) {
                attackSpeed = 0.8;
            } else if (material == ToolMaterials.IRON) {
                attackSpeed = 0.9;
            } else if (material == ToolMaterials.DIAMOND || material == ToolMaterials.NETHERITE) {
                attackSpeed = 1.0;
            }
        } else if (item instanceof TridentItem) {
            attackSpeed = 1.1; // Trident is fairly fast
        } else if (item instanceof MaceItem) {
            attackSpeed = 0.7; // Mace is slow but powerful
        } else if (item instanceof ToolItem) {
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

        // Use material tier as enchantment proxy
        // Higher tier items are more likely to have good enchantments
        if (stack.getItem() instanceof ToolItem toolItem) {
            ToolMaterial material = toolItem.getMaterial();
            if (material == ToolMaterials.NETHERITE) {
                score += 10; // Best material
            } else if (material == ToolMaterials.DIAMOND) {
                score += 8;
            } else if (material == ToolMaterials.IRON) {
                score += 5;
            } else if (material == ToolMaterials.STONE) {
                score += 3;
            } else if (material == ToolMaterials.GOLD) {
                score += 4; // Gold has enchantability but low durability
            }
        }

        // Check for enchantment glint (indicates item is enchanted)
        if (stack.hasGlint()) {
            score += 5; // Bonus for any enchantments
        }

        return score;
    }

    /**
     * Equip the best melee weapon from inventory
     * Returns true if weapon was successfully equipped, false otherwise
     */
    public static boolean equipBestMeleeWeapon(ServerPlayerEntity bot) {
        int bestWeaponSlot = findBestMeleeWeapon(bot);

        if (bestWeaponSlot == -1) {
            LOGGER.warn("No melee weapons available to equip");
            return false;
        }

        // If weapon is already in hand, we're done
        if (bot.getInventory().selectedSlot == bestWeaponSlot && bestWeaponSlot < 9) {
            LOGGER.debug("✓ Best weapon already equipped");
            return true;
        }

        try {
            // If weapon is in main inventory (slot 9-35), swap it to hotbar first
            if (bestWeaponSlot >= 9) {
                // Find empty hotbar slot or use slot 0
                int targetHotbarSlot = 0;
                for (int i = 0; i < 9; i++) {
                    if (bot.getInventory().getStack(i).isEmpty()) {
                        targetHotbarSlot = i;
                        break;
                    }
                }

                // Swap weapon to hotbar
                ItemStack weaponStack = bot.getInventory().getStack(bestWeaponSlot);
                ItemStack hotbarStack = bot.getInventory().getStack(targetHotbarSlot);
                bot.getInventory().setStack(bestWeaponSlot, hotbarStack);
                bot.getInventory().setStack(targetHotbarSlot, weaponStack);

                LOGGER.info("Moved weapon from slot {} to hotbar slot {}", bestWeaponSlot, targetHotbarSlot);
                bestWeaponSlot = targetHotbarSlot;
            }

            // Select the hotbar slot with the best weapon
            bot.getInventory().selectedSlot = bestWeaponSlot;
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

