package net.shasankp000.Personality;

import net.minecraft.server.level.ServerPlayer;
import net.shasankp000.GameAI.State;

/**
 * MoodEngine — derives and caches the bot's current AffectiveState from
 * game-world inputs.  Called at the end of every detectAndReact() cycle
 * (Feature 2.4) so that the mood is always fresh before any LLM prompt
 * is built.
 *
 * Design contract:
 *   • Pure computation, no I/O, no MC-thread scheduling.
 *   • Thread-safe: volatile write to currentMood is the only shared state.
 */
public class MoodEngine {

    // Singleton mood visible to PromptBuilder and WorldEventListener
    private static volatile AffectiveState currentMood = AffectiveState.NEUTRAL;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Re-evaluate and store the bot's mood from the supplied post-action
     * {@link State}.  Safe to call from any thread.
     *
     * @param bot        the bot entity (used for health / hunger cross-checks)
     * @param afterState the State recorded *after* the action was executed
     * @return the newly computed mood
     */
    public static AffectiveState update(ServerPlayer bot, State afterState) {
        AffectiveState computed = compute(bot, afterState);
        currentMood = computed;
        return computed;
    }

    /** Returns the most recently computed mood without re-evaluating. */
    public static AffectiveState getCurrent() {
        return currentMood;
    }

    /**
     * Force-set the mood externally (e.g. from a /bot mood command or a
     * world-event listener).  Prefer {@link #update} where a State is
     * available.
     */
    public static void set(AffectiveState mood) {
        currentMood = mood;
    }

    // -----------------------------------------------------------------------
    // Derivation logic
    // -----------------------------------------------------------------------

    /**
     * Derives mood from a snapshot of the bot's world-state using a
     * priority-ordered rule set.  Rules fire top-down; first match wins.
     *
     * Priority (highest → lowest):
     *  1. PANICKED  — health ≤ 4 (2 hearts) and nearby hostiles present
     *  2. AGGRESSIVE — health > 14 and hostiles present (bot is winning)
     *  3. FEARFUL    — health ≤ 8 or hunger ≤ 4 (stressed but not critical)
     *  4. BORED      — no nearby entities at all and health > 16
     *  5. HAPPY      — health > 16 and hunger > 14 and no threats
     *  6. NEUTRAL    — everything else
     */
    private static AffectiveState compute(ServerPlayer bot, State state) {
        if (state == null) return AffectiveState.NEUTRAL;

        int health  = (int) bot.getHealth();
        int hunger  = bot.getFoodData().getFoodLevel();
        boolean hasHostiles = state.getNearbyEntities() != null &&
            state.getNearbyEntities().stream().anyMatch(
                e -> e != null && e.isHostile());
        boolean hasAnyEntities = state.getNearbyEntities() != null &&
            !state.getNearbyEntities().isEmpty();

        // 1. PANICKED
        if (health <= 4 && hasHostiles) {
            return AffectiveState.PANICKED;
        }
        // 2. AGGRESSIVE
        if (health > 14 && hasHostiles) {
            return AffectiveState.AGGRESSIVE;
        }
        // 3. FEARFUL
        if (health <= 8 || hunger <= 4) {
            return AffectiveState.FEARFUL;
        }
        // 4. BORED
        if (!hasAnyEntities && health > 16) {
            return AffectiveState.BORED;
        }
        // 5. HAPPY
        if (health > 16 && hunger > 14 && !hasHostiles) {
            return AffectiveState.HAPPY;
        }
        // 6. NEUTRAL (default)
        return AffectiveState.NEUTRAL;
    }
}
