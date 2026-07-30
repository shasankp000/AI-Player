package net.shasankp000.FilingSystem;

import net.shasankp000.AIPlayer;
import net.shasankp000.ServiceLLMClients.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

public class LLMClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("llm-client-factory");

    /**
     * Creates an LLMClient for providers that only need an API key.
     * For Player2, use {@link #createClient(String, UUID, Consumer)} instead.
     */
    public static LLMClient createClient(String mode) {
        return createClient(mode, null, null);
    }

    /**
     * Creates an LLMClient for the given provider mode.
     *
     * <p>{@code playerUUID} and {@code chatCallback} are only used by the
     * {@code "player2"} case — they are ignored for every other provider.
     *
     * @param mode         provider string from config / JVM flag
     * @param playerUUID   the Minecraft player UUID (required for Player2)
     * @param chatCallback receives in-game chat messages (required for Player2)
     */
    public static LLMClient createClient(String mode, UUID playerUUID, Consumer<String> chatCallback) {
        return switch (mode) {
            case "openai", "gpt" -> {
                if (AIPlayer.CONFIG.getOpenAIKey().isEmpty()) {
                    LOGGER.error("OpenAI API key not set in config!");
                    yield null;
                }
                yield new OpenAIClient(AIPlayer.CONFIG.getOpenAIKey(), AIPlayer.CONFIG.getSelectedLanguageModel());
            }
            case "anthropic", "claude" -> {
                if (AIPlayer.CONFIG.getClaudeKey().isEmpty()) {
                    LOGGER.error("Claude API key not set in config!");
                    yield null;
                }
                yield new AnthropicClient(AIPlayer.CONFIG.getClaudeKey(), AIPlayer.CONFIG.getSelectedLanguageModel());
            }
            case "google", "gemini" -> {
                if (AIPlayer.CONFIG.getGeminiKey().isEmpty()) {
                    LOGGER.error("Gemini API key not set in config!");
                    yield null;
                }
                yield new GeminiClient(AIPlayer.CONFIG.getGeminiKey(), AIPlayer.CONFIG.getSelectedLanguageModel());
            }
            case "xAI", "xai", "grok" -> {
                if (AIPlayer.CONFIG.getGrokKey().isEmpty()) {
                    LOGGER.error("Grok API key not set in config!");
                    yield null;
                }
                yield new GrokClient(AIPlayer.CONFIG.getGrokKey(), AIPlayer.CONFIG.getSelectedLanguageModel());
            }
            case "custom" -> {
                if (AIPlayer.CONFIG.getCustomApiUrl().isEmpty()) {
                    LOGGER.error("Custom API URL not set in config!");
                    yield null;
                }
                yield new GenericOpenAIClient(AIPlayer.CONFIG.getCustomApiKey(), AIPlayer.CONFIG.getSelectedLanguageModel(), AIPlayer.CONFIG.getCustomApiUrl());
            }
            case "player2" -> {
                if (playerUUID == null || chatCallback == null) {
                    LOGGER.error("Player2 requires playerUUID and chatCallback — use createClient(mode, playerUUID, chatCallback)!");
                    yield null;
                }
                yield new Player2Client(playerUUID, AIPlayer.CONFIG.getSelectedLanguageModel(), chatCallback);
            }
            default -> {
                LOGGER.error("Unsupported LLM provider: {}. Set aiplayer.llmMode=custom for an OpenAI-compatible endpoint.", mode);
                yield null;
            }
        };
    }
}
