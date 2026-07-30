package net.shasankp000.PlayerUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks player aggression towards bot and determines if retaliation is warranted
 * Uses a threshold system: bot only retaliates after multiple hostile actions
 */
public class PlayerRetaliationTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerRetaliationTracker");

    // Threshold for marking player as hostile
    private static final int HOSTILE_THRESHOLD = 3; // 3 hits within timeout period
    private static final long HOSTILE_TIMEOUT = 30000; // 30 seconds timeout
    private static final long FORGIVENESS_TIMEOUT = 60000; // Forgive after 60s of no attacks


    // Track hits from each player: bot UUID -> player UUID -> hit data
    private static final Map<UUID, Map<UUID, PlayerHitData>> playerHits = new ConcurrentHashMap<>();

    // Track hostile status: bot UUID -> player UUID -> hostile status
    private static final Map<UUID, Map<UUID, HostilePlayerData>> hostilePlayers = new ConcurrentHashMap<>();

    private static class PlayerHitData {
        int hitCount;
        long lastHitTime;
        long firstHitTime;

        PlayerHitData() {
            this.hitCount = 1;
            this.lastHitTime = System.currentTimeMillis();
            this.firstHitTime = this.lastHitTime;
        }

        void addHit() {
            this.hitCount++;
            this.lastHitTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - lastHitTime) > HOSTILE_TIMEOUT;
        }
    }

    private static class HostilePlayerData {
        double threatLevel;
        long markedHostileTime;
        long lastSeenTime;

        HostilePlayerData(double threat) {
            this.threatLevel = threat;
            this.markedHostileTime = System.currentTimeMillis();
            this.lastSeenTime = this.markedHostileTime;
        }

        void updateThreat(double newThreat) {
            this.threatLevel = newThreat;
            this.lastSeenTime = System.currentTimeMillis();
        }

        boolean shouldForgive() {
            return (System.currentTimeMillis() - lastSeenTime) > FORGIVENESS_TIMEOUT;
        }
    }

    /**
     * Record a hit from a player to the bot
     * @return true if player should now be marked as hostile
     */
    public static boolean recordPlayerHit(ServerPlayer bot, Player attacker) {
        UUID botId = bot.getUUID();
        UUID attackerId = attacker.getUUID();

        // Get or create hit map for this bot
        Map<UUID, PlayerHitData> botHitMap = playerHits.computeIfAbsent(botId, k -> new ConcurrentHashMap<>());

        // Get or create hit data for this attacker
        PlayerHitData hitData = botHitMap.get(attackerId);
        if (hitData == null || hitData.isExpired()) {
            // New tracking or expired - start fresh
            hitData = new PlayerHitData();
            botHitMap.put(attackerId, hitData);
            LOGGER.info("📊 Player {} hit bot {} (1/{})",
                attacker.getName().getString(), bot.getName().getString(), HOSTILE_THRESHOLD);
        } else {
            // Add to existing count
            hitData.addHit();
            LOGGER.info("📊 Player {} hit bot {} ({}/{}) - {} between hits",
                attacker.getName().getString(), bot.getName().getString(),
                hitData.hitCount, HOSTILE_THRESHOLD,
                (hitData.lastHitTime - hitData.firstHitTime) + "ms");
        }

        // Check if threshold reached
        if (hitData.hitCount >= HOSTILE_THRESHOLD) {
            markPlayerHostile(bot, attacker);
            return true;
        }

        return false;
    }

    /**
     * Mark a player as hostile towards the bot
     */
    public static void markPlayerHostile(ServerPlayer bot, Player player) {
        UUID botId = bot.getUUID();
        UUID playerId = player.getUUID();

        // Calculate threat level based on player's equipment
        double threat = calculatePlayerThreatLevel(bot, player);

        Map<UUID, HostilePlayerData> botHostileMap = hostilePlayers.computeIfAbsent(botId, k -> new ConcurrentHashMap<>());
        botHostileMap.put(playerId, new HostilePlayerData(threat));

        LOGGER.warn("⚔ Player {} marked as HOSTILE to bot {} (Threat: {})",
            player.getName().getString(), bot.getName().getString(), String.format("%.1f", threat));
    }

    /**
     * Check if a player is hostile towards the bot
     */
    public static boolean isPlayerHostile(ServerPlayer bot, Player player) {
        Map<UUID, HostilePlayerData> botHostileMap = hostilePlayers.get(bot.getUUID());
        if (botHostileMap == null) {
            return false;
        }

        HostilePlayerData hostileData = botHostileMap.get(player.getUUID());
        if (hostileData == null) {
            return false;
        }

        // Check forgiveness timeout
        if (hostileData.shouldForgive()) {
            LOGGER.info("✓ Player {} forgiven by bot {} (60s no attacks)",
                player.getName().getString(), bot.getName().getString());
            botHostileMap.remove(player.getUUID());
            return false;
        }

        // Update last seen time
        hostileData.lastSeenTime = System.currentTimeMillis();

        return true;
    }

    /**
     * Get threat level for a hostile player
     */
    public static double getPlayerThreatLevel(ServerPlayer bot, Player player) {
        Map<UUID, HostilePlayerData> botHostileMap = hostilePlayers.get(bot.getUUID());
        if (botHostileMap == null) {
            return 0.0;
        }

        HostilePlayerData hostileData = botHostileMap.get(player.getUUID());
        if (hostileData == null) {
            return 0.0;
        }

        // Recalculate threat based on current equipment
        double currentThreat = calculatePlayerThreatLevel(bot, player);
        hostileData.updateThreat(currentThreat);

        return currentThreat;
    }

    /**
     * Calculate threat level based on player's equipment, health, distance
     */
    private static double calculatePlayerThreatLevel(ServerPlayer bot, Player player) {
        double baseThreat = 20.0; // Base threat for any hostile player

        // Distance factor (closer = more threatening)
        double distance = Math.sqrt(player.distanceToSqr(bot));
        double distanceFactor = 30.0 / Math.max(distance, 1.0);

        // Weapon threat
        double weaponThreat = 0.0;
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString();

            // Ranged weapons (highest threat)
            if (itemId.contains("bow") || itemId.contains("crossbow")) {
                weaponThreat = 15.0;
            }
            // Melee weapons
            else if (itemId.contains("sword") || itemId.contains("axe")) {
                weaponThreat = 10.0;
                // Diamond/netherite bonus
                if (itemId.contains("diamond") || itemId.contains("netherite")) {
                    weaponThreat += 5.0;
                }
            }
            // Trident
            else if (itemId.contains("trident")) {
                weaponThreat = 12.0;
            }
        }

        // Armor threat (well-armored player is more dangerous)
        double armorThreat = 0.0;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack armorPiece = player.getItemBySlot(slot);
            if (!armorPiece.isEmpty()) {
                String armorId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(armorPiece.getItem()).toString();
                if (armorId.contains("diamond") || armorId.contains("netherite")) {
                    armorThreat += 3.0;
                } else if (armorId.contains("iron")) {
                    armorThreat += 2.0;
                } else {
                    armorThreat += 1.0;
                }
            }
        }

        // Health factor (low health player is less threatening)
        double healthRatio = player.getHealth() / player.getMaxHealth();
        double healthFactor = healthRatio * 10.0;

        double totalThreat = baseThreat + distanceFactor + weaponThreat + armorThreat + healthFactor;

        LOGGER.debug("Player {} threat breakdown: base={}, dist={}, weapon={}, armor={}, health={} -> Total={}",
            player.getName().getString(), baseThreat,
            String.format("%.1f", distanceFactor),
            String.format("%.1f", weaponThreat),
            String.format("%.1f", armorThreat),
            String.format("%.1f", healthFactor),
            String.format("%.1f", totalThreat));

        return totalThreat;
    }

    /**
     * Clear hostile status for a player (for testing or manual forgiveness)
     */
    public static void clearHostileStatus(ServerPlayer bot, Player player) {
        Map<UUID, HostilePlayerData> botHostileMap = hostilePlayers.get(bot.getUUID());
        if (botHostileMap != null) {
            botHostileMap.remove(player.getUUID());
            LOGGER.info("✓ Cleared hostile status for player {} towards bot {}",
                player.getName().getString(), bot.getName().getString());
        }
    }

    /**
     * Clear all tracking data for a bot (when bot dies/leaves)
     */
    public static void clearBotData(ServerPlayer bot) {
        UUID botId = bot.getUUID();
        playerHits.remove(botId);
        hostilePlayers.remove(botId);
        LOGGER.info("Cleared all retaliation data for bot {}", bot.getName().getString());
    }
}
