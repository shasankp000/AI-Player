package net.shasankp000.AIProviders;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import net.shasankp000.AIPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating EmbeddingProvider instances based on current configuration.
 * Automatically selects the appropriate embedding model based on the LLM provider.
 */
public class EmbeddingProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-embedding-factory");

    /**
     * Create an embedding provider based on the current AI provider configuration.
     * This method automatically determines the correct embedding endpoint and model
     * based on the selected LLM provider from JVM arguments.
     *
     * @param ollamaAPI Ollama API instance (used as fallback)
     * @return Configured EmbeddingProvider
     */
    public static EmbeddingProvider createEmbeddingProvider(OllamaAPI ollamaAPI) {
        try {
            // Get provider from JVM argument
            String provider = System.getProperty("aiplayer.llmMode", "ollama");
            LOGGER.info("🔍 Creating embedding provider for: {}", provider);

            String embeddingModel = getDefaultEmbeddingModel(provider);
            String apiKey;
            String endpoint;

            switch (provider.toLowerCase()) {
                case "ollama":
                    LOGGER.info("✅ Using Ollama embedding model: {}", embeddingModel);
                    return new EmbeddingProvider(ollamaAPI, embeddingModel);

                case "openai":
                    apiKey = AIPlayer.CONFIG.getOpenAIKey();
                    if (apiKey == null || apiKey.isEmpty()) {
                        LOGGER.warn("⚠ OpenAI API key not configured, falling back to Ollama");
                        return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
                    }
                    LOGGER.info("✅ Using OpenAI embedding model: {}", embeddingModel);
                    return new EmbeddingProvider(
                            "https://api.openai.com",
                            apiKey,
                            embeddingModel,
                            EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE
                    );

                case "gemini":
                    apiKey = AIPlayer.CONFIG.getGeminiKey();
                    if (apiKey == null || apiKey.isEmpty()) {
                        LOGGER.warn("⚠ Gemini API key not configured, falling back to Ollama");
                        return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
                    }
                    LOGGER.info("✅ Using Gemini embedding model: {}", embeddingModel);
                    return new EmbeddingProvider(
                            "https://generativelanguage.googleapis.com",
                            apiKey,
                            embeddingModel,
                            EmbeddingProvider.AIProviderType.GEMINI
                    );

                case "grok":
                    apiKey = AIPlayer.CONFIG.getGrokKey();
                    if (apiKey == null || apiKey.isEmpty()) {
                        LOGGER.warn("⚠ Grok API key not configured, falling back to Ollama");
                        return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
                    }
                    LOGGER.info("✅ Using Grok (OpenAI-compatible) embedding model: {}", embeddingModel);
                    return new EmbeddingProvider(
                            "https://api.x.ai",
                            apiKey,
                            embeddingModel,
                            EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE
                    );

                case "custom":
                    endpoint = AIPlayer.CONFIG.getCustomApiUrl();
                    apiKey = AIPlayer.CONFIG.getCustomApiKey();

                    if (endpoint == null || endpoint.isEmpty()) {
                        LOGGER.warn("⚠ Custom endpoint not configured, falling back to Ollama");
                        return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
                    }

                    // If no API key is required for custom endpoint (e.g., LM Studio, local VLLM)
                    if (apiKey == null) {
                        apiKey = "";
                    }

                    LOGGER.info("✅ Using custom OpenAI-compatible embedding endpoint: {}", endpoint);
                    LOGGER.info("✅ Using embedding model: {}", embeddingModel);
                    return new EmbeddingProvider(
                            endpoint,
                            apiKey,
                            embeddingModel,
                            EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE
                    );

                case "claude":
                case "anthropic":
                    LOGGER.warn("⚠ Anthropic/Claude does not provide embedding endpoints, falling back to Ollama");
                    return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");

                default:
                    LOGGER.warn("⚠ Unknown provider '{}', falling back to Ollama", provider);
                    return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
            }
        } catch (Exception e) {
            LOGGER.error("❌ Failed to create embedding provider, falling back to Ollama", e);
            return new EmbeddingProvider(ollamaAPI, "nomic-embed-text");
        }
    }

    /**
     * Get the default embedding model for a given provider.
     * These are industry-standard defaults that work with most providers.
     */
    private static String getDefaultEmbeddingModel(String provider) {
        return switch (provider.toLowerCase()) {
            case "ollama" -> "nomic-embed-text";
            case "openai" -> "text-embedding-3-small"; // Latest OpenAI embedding model
            case "gemini" -> "text-embedding-004"; // Latest Gemini embedding model
            case "grok", "custom" ->
                // For OpenAI-compatible endpoints (Grok, LM Studio, VLLM, etc.)
                // Use a common embedding model name that most providers support
                    "text-embedding-ada-002";
            default -> "nomic-embed-text"; // Fallback to Ollama default
        };
    }
}

