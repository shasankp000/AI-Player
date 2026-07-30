package net.shasankp000.FilingSystem;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import net.shasankp000.AIPlayer;
import net.shasankp000.ServiceLLMClients.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating embedding clients based on the selected LLM provider.
 * Automatically selects the appropriate embedding model for each provider.
 */
public class EmbeddingClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("embedding-client-factory");
    private static final String OLLAMA_HOST = "http://localhost:11434/";
    private static EmbeddingClient cachedClient = null;
    private static String cachedMode = null;

    /**
     * Create an embedding client based on the current LLM provider configuration.
     * Uses a singleton pattern to reuse clients when the mode hasn't changed.
     *
     * @return An EmbeddingClient instance for the configured provider
     */
    public static EmbeddingClient createClient() {
        String mode = System.getProperty("aiplayer.llmMode", "ollama");

        // Return cached client if mode hasn't changed
        if (cachedClient != null && mode.equals(cachedMode)) {
            return cachedClient;
        }

        EmbeddingClient client = switch (mode) {
            case "openai", "gpt" -> {
                if (AIPlayer.CONFIG.getOpenAIKey().isEmpty()) {
                    LOGGER.warn("OpenAI API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient();
                }
                LOGGER.info("Using OpenAI embeddings (text-embedding-3-small)");
                yield new OpenAIEmbeddingClient(AIPlayer.CONFIG.getOpenAIKey());
            }
            case "anthropic", "claude" -> {
                if (AIPlayer.CONFIG.getClaudeKey().isEmpty()) {
                    LOGGER.warn("Claude API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient();
                }
                LOGGER.info("Using Anthropic embeddings (Voyage AI)");
                yield new AnthropicEmbeddingClient(AIPlayer.CONFIG.getClaudeKey());
            }
            case "google", "gemini" -> {
                if (AIPlayer.CONFIG.getGeminiKey().isEmpty()) {
                    LOGGER.warn("Gemini API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient();
                }
                LOGGER.info("Using Gemini embeddings (text-embedding-004)");
                yield new GeminiEmbeddingClient(AIPlayer.CONFIG.getGeminiKey());
            }
            case "xAI", "xai", "grok" -> {
                if (AIPlayer.CONFIG.getGrokKey().isEmpty()) {
                    LOGGER.warn("Grok API key not set - falling back to Ollama for embeddings");
                    yield createOllamaClient();
                }
                LOGGER.info("Using xAI embeddings (embedding-large-1)");
                yield new GrokEmbeddingClient(AIPlayer.CONFIG.getGrokKey());
            }
            case "custom" -> {
                if (AIPlayer.CONFIG.getCustomApiUrl().isEmpty()) {
                    LOGGER.warn("Custom API URL not set - falling back to Ollama for embeddings");
                    yield createOllamaClient();
                }
                // For custom providers, intelligently construct embedding endpoint
                String baseUrl = AIPlayer.CONFIG.getCustomApiUrl();
                String embeddingUrl = deriveEmbeddingEndpoint(baseUrl);

                LOGGER.info("Using custom embeddings endpoint: {}", embeddingUrl);
                yield new GenericEmbeddingClient(
                        AIPlayer.CONFIG.getCustomApiKey(),
                        "text-embedding-3-small", // Default model name - can be overridden
                        embeddingUrl
                );
            }
            default -> {
                LOGGER.info("Using Ollama embeddings (nomic-embed-text)");
                yield createOllamaClient();
            }
        };

        // Cache the client
        cachedClient = client;
        cachedMode = mode;

        return client;
    }

    /**
     * Create an embedding client for a specific provider, overriding the config.
     *
     * @param mode The provider mode (e.g., "openai", "anthropic", "gemini")
     * @return An EmbeddingClient instance for the specified provider
     */
    public static EmbeddingClient createClient(String mode) {
        return switch (mode) {
            case "openai", "gpt" -> {
                if (AIPlayer.CONFIG.getOpenAIKey().isEmpty()) {
                    LOGGER.error("OpenAI API key not set!");
                    yield null;
                }
                yield new OpenAIEmbeddingClient(AIPlayer.CONFIG.getOpenAIKey());
            }
            case "anthropic", "claude" -> {
                if (AIPlayer.CONFIG.getClaudeKey().isEmpty()) {
                    LOGGER.error("Claude API key not set!");
                    yield null;
                }
                yield new AnthropicEmbeddingClient(AIPlayer.CONFIG.getClaudeKey());
            }
            case "google", "gemini" -> {
                if (AIPlayer.CONFIG.getGeminiKey().isEmpty()) {
                    LOGGER.error("Gemini API key not set!");
                    yield null;
                }
                yield new GeminiEmbeddingClient(AIPlayer.CONFIG.getGeminiKey());
            }
            case "xAI", "xai", "grok" -> {
                if (AIPlayer.CONFIG.getGrokKey().isEmpty()) {
                    LOGGER.error("Grok API key not set!");
                    yield null;
                }
                yield new GrokEmbeddingClient(AIPlayer.CONFIG.getGrokKey());
            }
            case "custom" -> {
                if (AIPlayer.CONFIG.getCustomApiUrl().isEmpty()) {
                    LOGGER.error("Custom API URL not set!");
                    yield null;
                }
                String baseUrl = AIPlayer.CONFIG.getCustomApiUrl();
                String embeddingUrl = deriveEmbeddingEndpoint(baseUrl);

                LOGGER.info("Derived custom embeddings endpoint: {}", embeddingUrl);
                yield new GenericEmbeddingClient(
                        AIPlayer.CONFIG.getCustomApiKey(),
                        "text-embedding-3-small",
                        embeddingUrl
                );
            }
            default -> {
                LOGGER.info("Defaulting to Ollama embedding client");
                yield createOllamaClient();
            }
        };
    }

    /**
     * Create an Ollama embedding client.
     *
     * @return An OllamaEmbeddingClient instance
     */
    private static OllamaEmbeddingClient createOllamaClient() {
        return new OllamaEmbeddingClient(new OllamaAPI(OLLAMA_HOST));
    }

    /**
     * Intelligently derive embedding endpoint from base API URL.
     * Handles common patterns like /v1/chat/completions, /chat/completions, etc.
     *
     * @param baseUrl The base API URL (e.g., https://api.provider.com/v1/chat/completions)
     * @return The embedding endpoint URL (e.g., https://api.provider.com/v1/embeddings)
     */
    private static String deriveEmbeddingEndpoint(String baseUrl) {
        // Normalize URL - remove trailing slashes
        String normalizedUrl = baseUrl.replaceAll("/+$", "");

        // Pattern 1: URL ends with /chat/completions -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?chat/completions$")) {
            return normalizedUrl.replaceAll("/(chat/completions)$", "/embeddings");
        }

        // Pattern 2: URL ends with /completions -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?completions$")) {
            return normalizedUrl.replaceAll("/completions$", "/embeddings");
        }

        // Pattern 3: URL ends with /chat -> replace with /embeddings
        if (normalizedUrl.matches(".*/(v1/)?chat$")) {
            return normalizedUrl.replaceAll("/chat$", "/embeddings");
        }

        // Pattern 4: URL ends with /v1 -> add /embeddings
        if (normalizedUrl.endsWith("/v1")) {
            return normalizedUrl + "/embeddings";
        }

        // Pattern 5: URL has /v1/ somewhere -> ensure /v1/embeddings
        if (normalizedUrl.contains("/v1/")) {
            // Extract base up to /v1/
            String base = normalizedUrl.substring(0, normalizedUrl.indexOf("/v1/") + 3);
            return base + "/embeddings";
        }

        // Default: append /v1/embeddings to base URL
        return normalizedUrl + "/v1/embeddings";
    }

    /**
     * Clear the cached client. Call this when provider configuration changes.
     */
    public static void clearCache() {
        cachedClient = null;
        cachedMode = null;
        LOGGER.info("Embedding client cache cleared");
    }

    /**
     * Test if the current embedding client is reachable.
     *
     * @return true if the embedding service is available, false otherwise
     */
    public static boolean testConnection() {
        try {
            EmbeddingClient client = createClient();
            if (client == null) {
                LOGGER.error("❌ Embedding client is null - configuration issue detected");
                return false;
            }

            boolean reachable = client.isReachable();
            if (reachable) {
                LOGGER.info("✅ Embedding service is reachable: {} ({})",
                    client.getProvider(), client.getEmbeddingModel());
            } else {
                LOGGER.warn("⚠ Embedding service is not reachable: {}", client.getProvider());
            }
            return reachable;
        } catch (Exception e) {
            LOGGER.error("❌ Error testing embedding connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate and log embedding configuration on startup.
     * Provides detailed information about which embedding service will be used.
     */
    public static void validateConfiguration() {
        try {
            String mode = System.getProperty("aiplayer.llmMode", "ollama");
            LOGGER.info("═══════════════════════════════════════════════════════");
            LOGGER.info("🔧 Validating Embedding Configuration");
            LOGGER.info("═══════════════════════════════════════════════════════");
            LOGGER.info("Selected LLM Provider: {}", mode);

            EmbeddingClient client = createClient();
            if (client == null) {
                LOGGER.error("❌ Failed to create embedding client!");
                return;
            }

            LOGGER.info("📊 Embedding Provider: {}", client.getProvider());
            LOGGER.info("📊 Embedding Model: {}", client.getEmbeddingModel());
            LOGGER.info("📊 Embedding Dimension: {}", client.getEmbeddingDimension());

            // Test connection
            LOGGER.info("🔌 Testing embedding service connection...");
            boolean reachable = client.isReachable();
            if (reachable) {
                LOGGER.info("✅ Embedding service is operational");
            } else {
                LOGGER.warn("⚠ Embedding service is not reachable - embeddings may fail at runtime");
            }

            LOGGER.info("═══════════════════════════════════════════════════════");
        } catch (Exception e) {
            LOGGER.error("❌ Error during embedding configuration validation", e);
        }
    }

    /**
     * Get information about the current embedding client.
     *
     * @return A string describing the current embedding provider and model
     */
    public static String getEmbeddingInfo() {
        try {
            EmbeddingClient client = createClient();
            if (client == null) {
                return "No embedding client available";
            }
            return String.format("%s - %s (%d dimensions)",
                    client.getProvider(),
                    client.getEmbeddingModel(),
                    client.getEmbeddingDimension());
        } catch (Exception e) {
            return "Error retrieving embedding info: " + e.getMessage();
        }
    }
}

