package net.shasankp000.Personality;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorldEventListener — Feature 2.5
 *
 * Processes discrete world events (kill, damage, respawn, time changes, etc.)
 * that don't have an associated RL cycle.  Each event:
 *   1. Optionally mutates mood via MoodEngine.
 *   2. Optionally queues an async LLM reaction via LLMContextBridge.
 *
 * Callers (e.g. event mixins, command handlers) invoke the static
 * {@code process(EventType, ...)} entry-point from the server thread.
 * LLM calls are always dispatched off-thread internally.
 */
public class WorldEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player/WorldEventListener");

    // ------------------------------------------------------------------
    // Event taxonomy
    // ------------------------------------------------------------------

    public enum EventType {
        /** Bot landed a killing blow on a mob or player. */
        BOT_KILL,
        /** Bot took damage (from any source). */
        BOT_HURT,
        /** Bot just died. */
        BOT_DEATH,
        /** Bot respawned after death. */
        BOT_RESPAWN,
        /** World just transitioned to night-time. */
        NIGHTFALL,
        /** World just transitioned to day-time. */
        SUNRISE,
        /** A nearby explosion was detected. */
        NEARBY_EXPLOSION,
        /** Bot picked up an item of interest. */
        ITEM_PICKUP,
        /** A player sent a chat message that mentions the bot. */
        PLAYER_MENTION,
        /** Generic / catch-all event for callers that don't fit a named type. */
        GENERIC
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /**
     * Process a world event.
     *
     * @param bot       the controlled bot player
     * @param type      the kind of event that occurred
     * @param detail    optional free-text detail appended to LLM context
     *                  (may be null or blank)
     */
    public static void process(ServerPlayerEntity bot, EventType type, String detail) {
        if (bot == null) return;

        LOGGER.debug("[WorldEventListener] event={} detail='{}'", type, detail);

        // 1. Mood mutation
        AffectiveState newMood = mapEventToMood(type);
        if (newMood != null) {
            MoodEngine.set(newMood);
            LOGGER.info("[WorldEventListener] Mood updated to {} after event {}", newMood, type);
        }

        // 2. Decide whether to trigger an LLM reaction
        if (shouldSpeak(type)) {
            State ctx = BotEventHandler.getCurrentState();
            String prompt = buildEventPrompt(type, detail);
            LLMContextBridge.reactAsync(bot, ctx, prompt);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Maps each event type to a mood override.
     * Returns null when the event should not change mood.
     */
    private static AffectiveState mapEventToMood(EventType type) {
        return switch (type) {
            case BOT_KILL          -> AffectiveState.AGGRESSIVE;
            case BOT_HURT          -> AffectiveState.FEARFUL;
            case BOT_DEATH         -> AffectiveState.PANICKED;
            case BOT_RESPAWN       -> AffectiveState.NEUTRAL;
            case NIGHTFALL         -> AffectiveState.FEARFUL;
            case SUNRISE           -> AffectiveState.HAPPY;
            case NEARBY_EXPLOSION  -> AffectiveState.PANICKED;
            case ITEM_PICKUP       -> AffectiveState.HAPPY;
            case PLAYER_MENTION    -> null; // mood unchanged; handled per-prompt
            case GENERIC           -> null;
        };
    }

    /** Returns true when the event warrants a voiced LLM reaction in chat. */
    private static boolean shouldSpeak(EventType type) {
        return switch (type) {
            case BOT_KILL, BOT_DEATH, BOT_RESPAWN,
                 NEARBY_EXPLOSION, PLAYER_MENTION -> true;
            default -> false;
        };
    }

    /** Constructs a short imperative prompt clause for the LLM. */
    private static String buildEventPrompt(EventType type, String detail) {
        String base = switch (type) {
            case BOT_KILL         -> "You just defeated an enemy. React briefly in character.";
            case BOT_DEATH        -> "You just died. Express how you feel in one sentence.";
            case BOT_RESPAWN      -> "You just respawned. Greet the world in one sentence.";
            case NEARBY_EXPLOSION -> "A nearby explosion startled you. React in character.";
            case PLAYER_MENTION   -> "A player mentioned you in chat. Acknowledge them.";
            default               -> "Something notable just happened. Comment in character.";
        };
        if (detail != null && !detail.isBlank()) {
            return base + " Context: " + detail;
        }
        return base;
    }
}
