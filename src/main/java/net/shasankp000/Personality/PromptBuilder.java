package net.shasankp000.Personality;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.State;

/**
 * PromptBuilder — Feature 3.4 / 2.7
 *
 * Assembles a fully-formed LLM prompt by combining:
 *   • The active PersonaTemplate (system-prompt + tone instructions)
 *   • The bot's current AffectiveState (mood colouring)
 *   • A world-state summary derived from the RL {@link State} snapshot
 *   • Any caller-supplied extra context / imperative instruction
 *
 * This class is pure computation — no I/O, no MC-thread dependencies.
 * It is therefore safe to call from any thread.
 */
public class PromptBuilder {

    /**
     * Build a prompt ready to send to an LLM.
     *
     * @param bot      the controlled bot (used for name, health, dimension)
     * @param state    current RL state snapshot (may be null)
     * @param mood     the bot's current affective state
     * @param extraCtx an optional imperative clause appended at the end
     *                 (e.g. "You just killed a zombie. React briefly.");
     *                 pass null or blank to omit
     * @return the complete prompt string
     */
    public static String build(
            ServerPlayerEntity bot,
            State state,
            AffectiveState mood,
            String extraCtx) {

        PersonaTemplate persona = PersonaRegistry.getActive();

        StringBuilder sb = new StringBuilder();

        // ── System / persona preamble ──────────────────────────────────────
        sb.append("[SYSTEM]\n");
        sb.append(persona.getSystemPrompt()).append("\n");
        sb.append("Your current emotional state: ").append(mood.toPromptWord()).append(".\n");
        sb.append("Respond in character. Keep replies concise (1-3 sentences).\n");
        sb.append("[/SYSTEM]\n\n");

        // ── World-state summary ────────────────────────────────────────────
        sb.append("[WORLD]\n");
        sb.append("Bot name: ").append(bot.getName().getString()).append("\n");
        sb.append("Health: ").append((int) bot.getHealth()).append("/20\n");
        sb.append("Hunger: ").append(bot.getHungerManager().getFoodLevel()).append("/20\n");

        String dimension = bot.getCommandSource().getWorld()
                .getRegistryKey().getValue().toString();
        sb.append("Dimension: ").append(dimension).append("\n");

        if (state != null) {
            // Fix: State exposes getTimeOfDay(), not getTime()
            sb.append("Time of day: ").append(state.getTimeOfDay()).append("\n");

            long hostileCount = state.getNearbyEntities() == null ? 0L :
                    state.getNearbyEntities().stream()
                            .filter(e -> e != null && e.isHostile())
                            .count();
            if (hostileCount > 0) {
                sb.append("Nearby hostile entities: ").append(hostileCount).append("\n");
            }

            if (state.getNearbyBlocks() != null && !state.getNearbyBlocks().isEmpty()) {
                sb.append("Notable nearby blocks: ")
                  .append(String.join(", ", state.getNearbyBlocks().subList(
                          0, Math.min(5, state.getNearbyBlocks().size()))))
                  .append("\n");
            }
        }
        sb.append("[/WORLD]\n\n");

        // ── Extra context / imperative instruction ─────────────────────────
        if (extraCtx != null && !extraCtx.isBlank()) {
            sb.append("[INSTRUCTION]\n");
            sb.append(extraCtx).append("\n");
            sb.append("[/INSTRUCTION]\n");
        }

        return sb.toString();
    }
}
