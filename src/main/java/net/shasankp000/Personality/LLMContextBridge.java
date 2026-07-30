package net.shasankp000.Personality;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.State;
import net.shasankp000.OllamaClient.ollamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * LLMContextBridge — Feature 2.7
 *
 * Single choke-point that wires together:
 *   PromptBuilder  →  ollamaClient (local Ollama)
 *                 →  (future) LLMServiceHandler (service-based providers)
 *
 * Callers should always use {@link #reactAsync} so that HTTP I/O never
 * blocks the MC server thread.
 *
 * The result (bot chat message) is executed back on the server thread via
 * {@code server.execute()} so Minecraft API calls remain thread-safe.
 */
public class LLMContextBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player/LLMContextBridge");

    /**
     * Build a prompt from current mood + state + extra context, then fire
     * an async request to the configured LLM backend.  On success the bot
     * posts the reply in chat.  Errors are logged but never propagate to
     * the caller.
     *
     * @param bot        the controlled bot
     * @param state      current RL state snapshot (may be null)
     * @param extraCtx   additional imperative instruction appended to the prompt
     *                   (e.g. "You just killed a zombie. React briefly.")
     * @return a CompletableFuture that resolves to the raw LLM reply string
     *         (empty string on failure)
     */
    public static CompletableFuture<String> reactAsync(
            ServerPlayerEntity bot,
            State state,
            String extraCtx) {

        // 1. Build the full prompt off-thread (PromptBuilder is pure computation)
        return CompletableFuture.supplyAsync(() -> {
            try {
                AffectiveState mood = MoodEngine.getCurrent();
                String prompt = PromptBuilder.build(bot, state, mood, extraCtx);

                LOGGER.debug("[LLMContextBridge] Sending prompt ({}chars) mood={}",
                        prompt.length(), mood);

                // 2. Route to the correct backend
                String reply = dispatchToBackend(bot, prompt);

                if (reply != null && !reply.isBlank()) {
                    LOGGER.info("[LLMContextBridge] LLM reply: {}", reply);
                    // 3. Post to Minecraft chat on the server thread
                    postChatSafe(bot, reply);
                    return reply;
                }
            } catch (Exception e) {
                LOGGER.error("[LLMContextBridge] LLM call failed", e);
            }
            return "";
        });
    }

    // ------------------------------------------------------------------
    // Backend dispatch
    // ------------------------------------------------------------------

    /**
     * Routes the prompt to the appropriate LLM backend.
     *
     * Current routing logic:
     *   • If ollamaClient reports a reachable local endpoint → use Ollama.
     *   • Otherwise (future) → delegate to LLMServiceHandler for remote
     *     providers (OpenAI-compatible, Gemini, etc.).
     *
     * The check is intentionally cheap: ollamaClient exposes a static
     * {@code isAvailable()} guard that returns false quickly when Ollama
     * is not running, avoiding long timeouts on the server thread.
     */
    private static String dispatchToBackend(ServerPlayerEntity bot, String prompt) {
        // Primary: local Ollama
        if (ollamaClient.isAvailable()) {
            try {
                return ollamaClient.sendMessage(prompt);
            } catch (Exception e) {
                LOGGER.warn("[LLMContextBridge] Ollama call failed, no fallback configured: {}",
                        e.getMessage());
            }
        } else {
            LOGGER.debug("[LLMContextBridge] Ollama not available; skipping LLM call.");
        }
        // Secondary: service-based handler (stubbed — wire when Feature 2.6 lands)
        // return LLMServiceHandler.complete(prompt);
        return null;
    }

    // ------------------------------------------------------------------
    // Thread-safe chat post
    // ------------------------------------------------------------------

    /**
     * Schedules a chat message on the MC server thread.
     * This is required because {@code bot.sendMessage} touches MC internals
     * and must not be called from a background thread.
     */
    private static void postChatSafe(ServerPlayerEntity bot, String message) {
        if (bot.getServer() == null) return;
        bot.getServer().execute(() -> {
            try {
                net.shasankp000.ChatUtils.ChatUtils.sendChatMessages(
                        bot.getCommandSource().withSilent().withMaxLevel(4),
                        message
                );
            } catch (Exception e) {
                LOGGER.error("[LLMContextBridge] Failed to post chat message", e);
            }
        });
    }
}
