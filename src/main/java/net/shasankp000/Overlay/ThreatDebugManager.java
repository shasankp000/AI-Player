package net.shasankp000.Overlay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages threat analysis debug information for rendering.
 * Thread-safe for concurrent access from game and render threads.
 */
public class ThreatDebugManager {

    private static volatile boolean debugEnabled = false;
    private static final Map<UUID, ThreatInfo> entityThreats = new ConcurrentHashMap<>();
    private static volatile UUID currentTargetUUID = null;
    private static volatile String currentAction = "";
    private static volatile ServerPlayerEntity botPlayer = null;

    /**
     * Threat information for an entity
     */
    public static class ThreatInfo {
        public final UUID entityUUID;
        public final String entityName;
        public final double baseThreat;
        public final double distance;
        public final String status; // "Targeting", "Evaluating", "Evading", etc.
        public final long timestamp;

        public ThreatInfo(UUID entityUUID, String entityName, double baseThreat, double distance, String status) {
            this.entityUUID = entityUUID;
            this.entityName = entityName;
            this.baseThreat = baseThreat;
            this.distance = distance;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isStale() {
            return System.currentTimeMillis() - timestamp > 5000; // 5 seconds
        }
    }

    /**
     * Toggle debug mode on/off
     */
    public static void toggleDebug() {
        debugEnabled = !debugEnabled;
        if (!debugEnabled) {
            clear();
        }
    }

    /**
     * Set debug mode state
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (!debugEnabled) {
            clear();
        }
    }

    /**
     * Check if debug mode is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Set the bot player for reference
     */
    public static void setBotPlayer(ServerPlayerEntity bot) {
        botPlayer = bot;
    }

    /**
     * Get the bot player
     */
    public static ServerPlayerEntity getBotPlayer() {
        return botPlayer;
    }

    /**
     * Update threat info for an entity
     */
    public static void updateThreat(UUID entityUUID, String entityName, double baseThreat, double distance, String status) {
        if (!debugEnabled) return;
        entityThreats.put(entityUUID, new ThreatInfo(entityUUID, entityName, baseThreat, distance, status));
    }

    /**
     * Set the current target entity
     */
    public static void setCurrentTarget(UUID entityUUID) {
        if (!debugEnabled) return;
        currentTargetUUID = entityUUID;
    }

    /**
     * Get the current target UUID
     */
    public static UUID getCurrentTarget() {
        return currentTargetUUID;
    }

    /**
     * Set the current action the bot is performing
     */
    public static void setCurrentAction(String action) {
        if (!debugEnabled) return;
        currentAction = action;
    }

    /**
     * Get the current action
     */
    public static String getCurrentAction() {
        return currentAction;
    }

    /**
     * Get threat info for an entity
     */
    public static ThreatInfo getThreatInfo(UUID entityUUID) {
        return entityThreats.get(entityUUID);
    }

    /**
     * Get all tracked threats
     */
    public static Map<UUID, ThreatInfo> getAllThreats() {
        // Clean up stale entries
        entityThreats.entrySet().removeIf(entry -> entry.getValue().isStale());
        return entityThreats;
    }

    /**
     * Clear all threat data
     */
    public static void clear() {
        entityThreats.clear();
        currentTargetUUID = null;
        currentAction = "";
    }

    /**
     * Remove threat info for a specific entity
     */
    public static void removeThreat(UUID entityUUID) {
        entityThreats.remove(entityUUID);
    }
}

