package net.shasankp000.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.Entity.EntityDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for determining combat strategies against hostile entities
 * Analyzes bot's inventory, enemy types, and threat levels to recommend optimal combat approach
 */
public class CombatStrategyUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("combat-strategy");

    // Note: Entity threat calculations are delegated to RLAgent.getEntityRisk()
    // which has comprehensive threat values for all mob types

    /**
     * Combat strategy recommendation
     */
    public static class CombatStrategy {
        public final String strategy; // "RANGED_COMBAT", "SHIELD_ADVANCE", "MELEE_RUSH", "TACTICAL_RETREAT", "EVASIVE_MELEE"
        public final double confidence; // 0.0 to 100.0 - how good this strategy is
        public final String primaryWeapon; // Recommended weapon
        public final String secondaryTool; // Shield, etc
        public final double estimatedRisk; // Risk level of this strategy
        public final String reason; // Why this strategy was chosen

        public CombatStrategy(String strategy, double confidence, String primaryWeapon,
                            String secondaryTool, double estimatedRisk, String reason) {
            this.strategy = strategy;
            this.confidence = confidence;
            this.primaryWeapon = primaryWeapon;
            this.secondaryTool = secondaryTool;
            this.estimatedRisk = estimatedRisk;
            this.reason = reason;
        }
    }

    /**
     * Determine the best combat strategy based on bot's inventory and enemy types
     */
    public static CombatStrategy determineCombatStrategy(ServerPlayerEntity bot, List<EntityDetails> nearbyEntities) {
        LOGGER.debug("🎯 Analyzing combat strategy for {} nearby hostile entities", nearbyEntities.size());

        // Parallel execution: analyze inventory AND enemies simultaneously
        // This utilizes multiple CPU cores for faster computation
        try {
            java.util.concurrent.ForkJoinPool pool = java.util.concurrent.ForkJoinPool.commonPool();

            // Task 1: Analyze inventory (parallel)
            java.util.concurrent.Future<InventoryAnalysis> invFuture = pool.submit(() -> analyzeInventory(bot));

            // Task 2: Analyze enemies (parallel, while inventory analysis runs)
            java.util.concurrent.Future<EnemyAnalysis> enemyFuture = pool.submit(() -> analyzeEnemies(nearbyEntities));

            // Wait for both results (fast due to parallel execution)
            InventoryAnalysis inventory = invFuture.get(50, TimeUnit.MILLISECONDS);
            EnemyAnalysis enemies = enemyFuture.get(50, TimeUnit.MILLISECONDS);

            LOGGER.debug("Inventory: Ranged={}, Melee={}, Shield={}, Arrows={}",
                inventory.hasRangedWeapon, inventory.hasMeleeWeapon, inventory.hasShield, inventory.arrowCount);
            LOGGER.debug("Enemies: Ranged={}, Melee={}, Explosive={}, AvgDist={}",
                enemies.rangedCount, enemies.meleeCount, enemies.explosiveCount,
                String.format("%.1f", enemies.averageDistance));

            // Determine strategy based on analysis (fast)
            return selectOptimalStrategy(inventory, enemies);

        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.warn("Combat strategy analysis timeout - using default");
            return new CombatStrategy("TACTICAL_RETREAT", 50.0, "none", "none", 75.0, "Analysis timeout");
        } catch (Exception e) {
            LOGGER.error("Combat strategy analysis error: {}", e.getMessage());
            return new CombatStrategy("TACTICAL_RETREAT", 50.0, "none", "none", 100.0, "Analysis error");
        }
    }

    /**
     * Analyze bot's combat-relevant inventory
     */
    private static class InventoryAnalysis {
        boolean hasRangedWeapon = false;
        boolean hasMeleeWeapon = false;
        boolean hasShield = false;
        int arrowCount = 0;
        String bestRangedWeapon = "none";
        String bestMeleeWeapon = "none";
        int totalCombatItems = 0;
    }

    private static InventoryAnalysis analyzeInventory(ServerPlayerEntity bot) {
        InventoryAnalysis analysis = new InventoryAnalysis();

        LOGGER.debug("Starting inventory analysis for bot: {}", bot.getName().getString());

        // Check all inventory slots
        for (int i = 0; i < bot.getInventory().size(); i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            // Use item name string (same method as RangedWeaponUtils - more reliable!)
            String itemName = item.getName().getString().toLowerCase();
            String itemId = net.minecraft.registry.Registries.ITEM.getId(item).toString().toLowerCase();

            LOGGER.debug("Slot {}: itemName='{}', itemId='{}', count={}",
                i, itemName, itemId, stack.getCount());

            // Ranged weapons - check BOTH name and registry ID for reliability
            if (itemName.contains("bow") || itemName.contains("crossbow") ||
                itemId.contains("bow") || itemId.contains("crossbow")) {
                analysis.hasRangedWeapon = true;
                analysis.totalCombatItems++;

                if (itemName.contains("crossbow") || itemId.contains("crossbow")) {
                    analysis.bestRangedWeapon = "crossbow";
                } else if ((itemName.contains("bow") || itemId.contains("bow")) &&
                          !analysis.bestRangedWeapon.equals("crossbow")) {
                    analysis.bestRangedWeapon = "bow";
                }
            }

            // Arrows - check BOTH name and registry ID
            if (itemName.contains("arrow") || itemId.contains("arrow")) {
                analysis.arrowCount += stack.getCount();
            }

            // Melee weapons (swords, axes, tridents)
            if (itemName.contains("sword") || itemName.contains("axe") || itemName.contains("trident") ||
                itemId.contains("sword") || itemId.contains("axe") || itemId.contains("trident")) {
                analysis.hasMeleeWeapon = true;
                analysis.totalCombatItems++;

                // Prioritize: netherite > diamond > iron > stone > wood
                if (itemName.contains("netherite") && itemName.contains("sword")) {
                    analysis.bestMeleeWeapon = "netherite_sword";
                } else if ((itemName.contains("diamond") && itemName.contains("sword")) &&
                          !analysis.bestMeleeWeapon.contains("netherite")) {
                    analysis.bestMeleeWeapon = "diamond_sword";
                } else if ((itemName.contains("iron") && itemName.contains("sword")) &&
                          !analysis.bestMeleeWeapon.contains("diamond") &&
                          !analysis.bestMeleeWeapon.contains("netherite")) {
                    analysis.bestMeleeWeapon = "iron_sword";
                } else if (itemName.contains("axe") && analysis.bestMeleeWeapon.equals("none")) {
                    analysis.bestMeleeWeapon = itemId;
                }
            }

            // Shield - check BOTH name and ID
            if (itemName.contains("shield") || itemId.contains("shield")) {
                analysis.hasShield = true;
                analysis.totalCombatItems++;
            }
        }

        // Log summary
        LOGGER.info("Inventory Analysis Complete: Ranged={} ({}), Melee={} ({}), Shield={}, Arrows={}",
            analysis.hasRangedWeapon, analysis.bestRangedWeapon,
            analysis.hasMeleeWeapon, analysis.bestMeleeWeapon,
            analysis.hasShield, analysis.arrowCount);

        return analysis;
    }

    /**
     * Analyze enemy composition and threat level
     */
    private static class EnemyAnalysis {
        int rangedCount = 0;
        int meleeCount = 0;
        int explosiveCount = 0;
        int totalHostile = 0;
        double averageDistance = 0.0;
        double highestThreat = 0.0;
        String mostDangerousEnemy = "none";
        EntityDetails closestEnemy = null;
    }

    private static EnemyAnalysis analyzeEnemies(List<EntityDetails> nearbyEntities) {
        EnemyAnalysis analysis = new EnemyAnalysis();
        double totalDistance = 0.0;

        for (EntityDetails entity : nearbyEntities) {
            if (!entity.isHostile()) continue;

            analysis.totalHostile++;
            String entityType = entity.getName().toLowerCase();

            // Calculate distance
            double distance = Math.sqrt(
                Math.pow(entity.getX(), 2) +
                Math.pow(entity.getY(), 2) +
                Math.pow(entity.getZ(), 2)
            );
            totalDistance += distance;

            // Update closest enemy
            if (analysis.closestEnemy == null || distance < Math.sqrt(
                Math.pow(analysis.closestEnemy.getX(), 2) +
                Math.pow(analysis.closestEnemy.getY(), 2) +
                Math.pow(analysis.closestEnemy.getZ(), 2))) {
                analysis.closestEnemy = entity;
            }

            // Categorize enemy type
            if (entityType.contains("creeper")) {
                analysis.explosiveCount++;
            } else if (isRangedMob(entityType)) {
                analysis.rangedCount++;
            } else {
                analysis.meleeCount++;
            }

            // Track highest threat - use dummy coordinates (0,0,0) for relative positioning
            // The actual bot position will be passed in the combat strategy determination
            double threat = net.shasankp000.GameAI.RLAgent.getEntityRisk(entity, 0, 0, 0);
            if (threat > analysis.highestThreat) {
                analysis.highestThreat = threat;
                analysis.mostDangerousEnemy = entityType;
            }
        }

        if (analysis.totalHostile > 0) {
            analysis.averageDistance = totalDistance / analysis.totalHostile;
        }

        return analysis;
    }

    /**
     * Check if entity is a ranged attacker
     */
    private static boolean isRangedMob(String entityType) {
        return entityType.contains("skeleton") || entityType.contains("blaze") ||
               entityType.contains("pillager") || entityType.contains("breeze") ||
               entityType.contains("witch") || entityType.contains("ghast");
    }

    /**
     * Get threat score for an entity using RLAgent's comprehensive risk calculation
     * This delegates to RLAgent.getEntityRisk which has detailed threat values for all mobs
     */
    public static double getEntityThreatScore(EntityDetails entity, int botX, int botY, int botZ) {
        // Use RLAgent's entity risk calculation which includes distance-based scaling
        return net.shasankp000.GameAI.RLAgent.getEntityRisk(entity, botX, botY, botZ);
    }

    /**
     * Select the optimal combat strategy
     */
    private static CombatStrategy selectOptimalStrategy(InventoryAnalysis inventory, EnemyAnalysis enemies) {
        // NO ENEMIES - No combat needed
        if (enemies.totalHostile == 0) {
            return new CombatStrategy("NONE", 100.0, "none", "none", 0.0,
                "No hostile entities nearby");
        }

        // WARDEN DETECTED - Always flee!
        if (enemies.mostDangerousEnemy.contains("warden")) {
            return new CombatStrategy("TACTICAL_RETREAT", 100.0, "none", "none", 150.0,
                "Warden detected - tactical retreat is only option");
        }

        // EXPLOSIVE THREAT (Creeper) - Prioritize ranged or shield block
        if (enemies.explosiveCount > 0) {
            if (inventory.hasRangedWeapon && inventory.arrowCount > 0) {
                return new CombatStrategy("RANGED_COMBAT", 90.0, inventory.bestRangedWeapon,
                    "none", 30.0, "Creeper threat - ranged combat safest");
            } else if (inventory.hasShield) {
                return new CombatStrategy("SHIELD_ADVANCE", 70.0, inventory.bestMeleeWeapon,
                    "shield", 50.0, "Creeper threat - shield block while closing distance");
            } else {
                return new CombatStrategy("TACTICAL_RETREAT", 60.0, "none", "none", 80.0,
                    "Creeper without counter - retreat recommended");
            }
        }

        // RANGED ENEMIES - Multiple strategies based on inventory
        if (enemies.rangedCount > 0) {
            double avgDist = enemies.averageDistance;

            // Strategy 1: Ranged duel (if bot has ranged weapon)
            if (inventory.hasRangedWeapon && inventory.arrowCount >= enemies.rangedCount * 3) {
                double confidence = 85.0;
                if (avgDist > 15.0) confidence = 95.0; // Excellent at range
                if (avgDist < 8.0) confidence = 70.0; // Harder at close range

                return new CombatStrategy("RANGED_COMBAT", confidence, inventory.bestRangedWeapon,
                    inventory.hasShield ? "shield" : "none", 25.0,
                    "Sufficient arrows for ranged duel");
            }

            // Strategy 2: Shield advance (if bot has shield + melee weapon)
            if (inventory.hasShield && inventory.hasMeleeWeapon) {
                double confidence = 75.0;
                double risk = 40.0;

                if (avgDist < 10.0) {
                    confidence = 85.0; // Better when already close
                    risk = 35.0;
                }

                if (enemies.rangedCount > 2) {
                    confidence -= 15.0; // Harder with multiple archers
                    risk += 20.0;
                }

                return new CombatStrategy("SHIELD_ADVANCE", confidence, inventory.bestMeleeWeapon,
                    "shield", risk, "Shield + melee can close gap safely");
            }

            // Strategy 3: Evasive melee (risky - zigzag approach)
            if (inventory.hasMeleeWeapon && avgDist < 15.0) {
                double confidence = 50.0;
                double risk = 70.0;

                if (enemies.rangedCount == 1) {
                    confidence = 65.0; // Easier with single archer
                    risk = 55.0;
                }

                return new CombatStrategy("EVASIVE_MELEE", confidence, inventory.bestMeleeWeapon,
                    "none", risk, "No shield - evasive zigzag approach required");
            }

            // Strategy 4: Retreat if no good options
            return new CombatStrategy("TACTICAL_RETREAT", 70.0, "none", "none", 60.0,
                "Insufficient equipment for ranged enemy engagement");
        }

        // MELEE ONLY ENEMIES - Simpler strategies
        if (enemies.meleeCount > 0) {
            double avgDist = enemies.averageDistance;

            // Overwhelmed check
            if (enemies.meleeCount > 5) {
                if (!inventory.hasRangedWeapon || inventory.arrowCount < 10) {
                    return new CombatStrategy("TACTICAL_RETREAT", 75.0, "none", "none", 70.0,
                        "Overwhelmed by melee enemies - retreat");
                }

                return new CombatStrategy("RANGED_COMBAT", 80.0, inventory.bestRangedWeapon,
                    "none", 50.0, "Multiple melee enemies - ranged combat safer");
            }

            // Melee rush (close combat)
            if (inventory.hasMeleeWeapon) {
                double confidence = 80.0;
                double risk = 40.0;

                if (avgDist < 5.0) {
                    confidence = 90.0; // Already in melee range
                    risk = 30.0;
                }

                if (inventory.hasShield) {
                    confidence += 10.0; // Shield adds safety
                    risk -= 10.0;
                }

                return new CombatStrategy("MELEE_RUSH", confidence, inventory.bestMeleeWeapon,
                    inventory.hasShield ? "shield" : "none", risk,
                    "Standard melee combat effective against melee mobs");
            }

            // Ranged kiting if available
            if (inventory.hasRangedWeapon && inventory.arrowCount > 5) {
                return new CombatStrategy("RANGED_COMBAT", 70.0, inventory.bestRangedWeapon,
                    "none", 35.0, "Kite melee enemies with ranged attacks");
            }

            // No weapons - retreat
            return new CombatStrategy("TACTICAL_RETREAT", 80.0, "none", "none", 90.0,
                "No combat equipment - retreat necessary");
        }

        // FALLBACK - Should not reach here
        return new CombatStrategy("TACTICAL_RETREAT", 50.0, "none", "none", 50.0,
            "Unknown situation - default to retreat");
    }

    /**
     * Calculate the combat risk value for RL risk assessment
     * Positive = risky, Negative = encouraged
     */
    public static double calculateCombatRisk(CombatStrategy strategy, int botHealth) {
        double baseRisk = strategy.estimatedRisk;

        // Adjust for bot health
        if (botHealth < 5) {
            baseRisk += 40.0; // Critical health - avoid combat
        } else if (botHealth < 10) {
            baseRisk += 20.0; // Low health - combat risky
        } else if (botHealth > 15) {
            baseRisk -= 10.0; // High health - combat safer
        }

        // Invert confidence to risk (high confidence = low RL risk)
        double confidenceBonus = (strategy.confidence - 50.0) / 2.0; // -25 to +25
        baseRisk -= confidenceBonus;

        // Strategic modifiers
        switch (strategy.strategy) {
            case "TACTICAL_RETREAT":
                baseRisk = Math.max(baseRisk, 10.0); // Retreat always has some risk baseline
                break;
            case "RANGED_COMBAT":
                baseRisk -= 15.0; // Ranged combat generally safer
                break;
            case "MELEE_RUSH":
                baseRisk += 10.0; // Melee more risky
                break;
            case "EVASIVE_MELEE":
                baseRisk += 25.0; // Very risky without shield
                break;
            case "SHIELD_ADVANCE":
                baseRisk -= 5.0; // Shield provides good protection
                break;
        }

        return baseRisk;
    }
}

