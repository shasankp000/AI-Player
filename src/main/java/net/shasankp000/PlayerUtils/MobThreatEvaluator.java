package net.shasankp000.PlayerUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates special threat levels for specific mob types
 * Handles phase-based threat analysis (e.g., creeper explosion states)
 */
public class MobThreatEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger("mob-threat-evaluator");

    /**
     * Creeper explosion phases
     */
    public enum CreeperPhase {
        IDLE,           // Not ignited, normal state
        IGNITED,        // Fuse started (flashing/expanding)
        CRITICAL        // About to explode (very close to detonation)
    }

    /**
     * Enhanced threat info for a mob with phase-specific data
     */
    public static class MobThreatInfo {
        public final Entity mob;
        public final double baseThreat;
        public final double phaseThreat;
        public final double totalThreat;
        public final String mobType;
        public final CreeperPhase creeperPhase; // null if not a creeper
        public final boolean requiresShield;
        public final boolean requiresDistance;
        public final String recommendedAction;

        public MobThreatInfo(Entity mob, double baseThreat, double phaseThreat,
                             CreeperPhase creeperPhase, boolean requiresShield,
                             boolean requiresDistance, String recommendedAction) {
            this.mob = mob;
            this.baseThreat = baseThreat;
            this.phaseThreat = phaseThreat;
            this.totalThreat = baseThreat + phaseThreat;
            this.mobType = mob.getName().getString();
            this.creeperPhase = creeperPhase;
            this.requiresShield = requiresShield;
            this.requiresDistance = requiresDistance;
            this.recommendedAction = recommendedAction;
        }
    }

    /**
     * Evaluate threat level for a specific mob with phase awareness
     */
    public static MobThreatInfo evaluateMobThreat(Entity mob, ServerPlayerEntity bot) {
        if (!(mob instanceof LivingEntity livingMob)) {
            return null; // Not a living mob
        }

        double baseThreat = calculateBaseThreat(livingMob, bot);

        // Special handling for creepers
        if (mob instanceof CreeperEntity creeper) {
            return evaluateCreeperThreat(creeper, bot, baseThreat);
        }

        // Default mob threat (no special phases)
        return new MobThreatInfo(
            mob,
            baseThreat,
            0.0,  // No phase threat
            null, // Not a creeper
            false, // No shield required
            false, // No distance required
            "attack" // Default action
        );
    }

    /**
     * Calculate base threat for any living mob
     * Based on: health, distance, attack damage, armor
     */
    private static double calculateBaseThreat(LivingEntity mob, ServerPlayerEntity bot) {
        double distance = Math.sqrt(mob.squaredDistanceTo(bot));
        float health = mob.getHealth();
        float maxHealth = mob.getMaxHealth();

        // Base threat increases as mob gets closer
        double distanceThreat = Math.max(0, 30.0 - distance * 2.0);

        // Health-based threat (healthy mobs are more dangerous)
        double healthThreat = (health / maxHealth) * 15.0;

        // Hostile mobs get bonus threat
        double hostilityBonus = mob instanceof HostileEntity ? 20.0 : 0.0;

        return distanceThreat + healthThreat + hostilityBonus;
    }

    /**
     * Evaluate creeper-specific threat with explosion phase analysis
     */
    private static MobThreatInfo evaluateCreeperThreat(CreeperEntity creeper, ServerPlayerEntity bot, double baseThreat) {
        double distance = Math.sqrt(creeper.squaredDistanceTo(bot));

        // Detect creeper explosion phase
        CreeperPhase phase = detectCreeperPhase(creeper);

        double phaseThreat = 0.0;
        boolean requiresShield = false;
        boolean requiresDistance = false;
        String recommendedAction = "attack";

        switch (phase) {
            case IDLE:
                // Normal creeper - moderate threat
                phaseThreat = 10.0;
                recommendedAction = "attack";
                LOGGER.debug("Creeper {} is IDLE (distance: {}m)", creeper.getId(), String.format("%.1f", distance));
                break;

            case IGNITED:
                // Fuse started - HIGH THREAT! Need to create distance
                phaseThreat = 40.0;
                requiresDistance = true;

                if (distance < 3.0) {
                    // Very close - sprint away immediately!
                    recommendedAction = "flee_immediate";
                    phaseThreat += 30.0; // Extra threat for being too close
                } else {
                    // Medium distance - back away while attacking if possible
                    recommendedAction = "keep_distance";
                }

                LOGGER.warn("⚠ Creeper {} is IGNITED! Distance: {}m - ACTION: {}",
                    creeper.getId(), String.format("%.1f", distance), recommendedAction);
                break;

            case CRITICAL:
                // About to explode - CRITICAL THREAT!
                phaseThreat = 80.0;
                requiresShield = true; // Shield can reduce blast damage
                requiresDistance = true;

                if (distance < 5.0) {
                    // Too close to escape - BLOCK with shield if available
                    recommendedAction = "block_explosion";
                    LOGGER.error("🔥 Creeper {} CRITICAL EXPLOSION! Distance: {}m - BLOCKING!",
                        creeper.getId(), String.format("%.1f", distance));
                } else {
                    // Try to get out of blast radius
                    recommendedAction = "flee_immediate";
                    LOGGER.error("🔥 Creeper {} CRITICAL EXPLOSION! Distance: {}m - FLEEING!",
                        creeper.getId(), String.format("%.1f", distance));
                }
                break;
        }

        return new MobThreatInfo(
            creeper,
            baseThreat,
            phaseThreat,
            phase,
            requiresShield,
            requiresDistance,
            recommendedAction
        );
    }

    /**
     * Detect which explosion phase a creeper is in
     * Uses fuse time and client flash state
     */
    private static CreeperPhase detectCreeperPhase(CreeperEntity creeper) {
        int fuseTime = creeper.getFuseSpeed();

        // Not ignited - fuse speed is -1 when idle
        if (fuseTime <= 0) {
            return CreeperPhase.IDLE;
        }

        // Use ignited state to determine phase
        // Note: getFuseSpeed() returns fuse speed multiplier, not timer
        // If ignited and close to explosion
        if (creeper.getTarget() != null) {
            // Creeper is tracking a target (bot)
            // Check distance to determine criticality
            double distanceToTarget = Math.sqrt(creeper.squaredDistanceTo(creeper.getTarget()));

            if (distanceToTarget < 2.0) {
                // Very close = critical phase (about to blow)
                return CreeperPhase.CRITICAL;
            } else if (distanceToTarget < 4.0) {
                // Close = ignited phase (fuse started)
                return CreeperPhase.IGNITED;
            }
        }

        // Fallback: If we reach here with positive fuse time, assume ignited
        // This shouldn't happen often, but handle gracefully
        return CreeperPhase.IDLE; // Default safe state
    }

    /**
     * Get enhanced threat map for all nearby mobs
     * Returns map of mob UUID -> MobThreatInfo
     */
    public static Map<String, MobThreatInfo> evaluateAllMobThreats(Iterable<Entity> mobs, ServerPlayerEntity bot) {
        Map<String, MobThreatInfo> threatMap = new HashMap<>();

        for (Entity mob : mobs) {
            if (mob instanceof LivingEntity) {
                MobThreatInfo info = evaluateMobThreat(mob, bot);
                if (info != null) {
                    threatMap.put(mob.getUuidAsString(), info);
                }
            }
        }

        return threatMap;
    }

    /**
     * Check if any creepers are in critical explosion phase nearby
     */
    public static boolean hasCriticalCreeperThreat(Iterable<Entity> mobs, ServerPlayerEntity bot) {
        for (Entity mob : mobs) {
            if (mob instanceof CreeperEntity creeper) {
                MobThreatInfo info = evaluateCreeperThreat(creeper, bot, 0.0);
                if (info.creeperPhase == CreeperPhase.CRITICAL) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the most dangerous creeper (by phase and distance)
     */
    public static CreeperEntity getMostDangerousCreeper(Iterable<Entity> mobs, ServerPlayerEntity bot) {
        CreeperEntity mostDangerous = null;
        double highestThreat = 0.0;

        for (Entity mob : mobs) {
            if (mob instanceof CreeperEntity creeper) {
                MobThreatInfo info = evaluateCreeperThreat(creeper, bot, 0.0);
                if (info.totalThreat > highestThreat) {
                    highestThreat = info.totalThreat;
                    mostDangerous = creeper;
                }
            }
        }

        return mostDangerous;
    }
}

