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
        // Optional, self-hosted / private embedding endpoint (OpenAI-compatible
        // /v1/embeddings). Keeps RAG memory embeddings fully private — nothing is
        // sent to Google/Gemini. Configure EMBEDDING_API_URL (and optionally
        // EMBEDDING_API_KEY / EMBEDDING_MODEL) in settings.json5.
        String embeddingUrl = AIPlayer.CONFIG.getEmbeddingApiUrl();
        if (embeddingUrl == null || embeddingUrl.trim().isEmpty()) {
            LOGGER.warn("⚠ No private EMBEDDING_API_URL configured — embeddings are disabled (RAG memory search will not run).");
            return null;
        }

        String apiKey = AIPlayer.CONFIG.getEmbeddingApiKey();
        String model = AIPlayer.CONFIG.getEmbeddingModel();
        if (model == null || model.trim().isEmpty()) {
            model = "local-embedding";
        }

        LOGGER.info("✅ Using private embedding endpoint: {} (model: {})", embeddingUrl, model);
        return new EmbeddingProvider(
                embeddingUrl.trim().replaceAll("/+$", ""),
                apiKey == null ? "" : apiKey,
                model,
                EmbeddingProvider.AIProviderType.OPENAI_COMPATIBLE
        );
    }

        return new EmbeddingProvider(
                "https://generativelanguage.googleapis.com",
                apiKey,
                "text-embedding-004",
                EmbeddingProvider.AIProviderType.GEMINI
        );
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

