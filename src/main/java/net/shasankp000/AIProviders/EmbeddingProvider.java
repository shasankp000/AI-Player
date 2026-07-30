package net.shasankp000.AIProviders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified embedding provider that supports multiple AI providers
 * Automatically uses the appropriate API endpoint based on configuration
 */
public class EmbeddingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-embeddings");
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private final String baseUrl;
    private final String apiKey;
    private final String embeddingModel;
    private final AIProviderType providerType;
    private final OllamaAPI ollamaAPI; // Fallback for Ollama

    public enum AIProviderType {
        OLLAMA,
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        GEMINI,
        MISTRAL,
        COHERE
    }

    /**
     * Constructor for Ollama provider (no API key needed)
     */
    public EmbeddingProvider(OllamaAPI ollamaAPI, String embeddingModel) {
        this.ollamaAPI = ollamaAPI;
        this.baseUrl = "http://localhost:11434";
        this.apiKey = null;
        this.embeddingModel = embeddingModel;
        this.providerType = AIProviderType.OLLAMA;
        LOGGER.info("📊 Embedding provider initialized: Ollama ({})", embeddingModel);
    }

    /**
     * Constructor for non-Ollama providers (requires API key and endpoint)
     */
    public EmbeddingProvider(String baseUrl, String apiKey, String embeddingModel, AIProviderType providerType) {
        this.ollamaAPI = null;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.providerType = providerType;
        LOGGER.info("📊 Embedding provider initialized: {} ({})", providerType, embeddingModel);
    }

    /**
     * Generate embeddings using the configured provider
     *
     * @param text The text to generate embeddings for
     * @return List of embedding values (doubles)
     */
    public List<Double> generateEmbeddings(String text) throws IOException, InterruptedException {
        switch (providerType) {
            case OLLAMA:
                return generateOllamaEmbeddings(text);
            case OPENAI_COMPATIBLE:
                return generateOpenAICompatibleEmbeddings(text);
            case ANTHROPIC:
                throw new UnsupportedOperationException("Anthropic does not provide embedding endpoints");
            case GEMINI:
                return generateGeminiEmbeddings(text);
            case MISTRAL:
                return generateMistralEmbeddings(text);
            case COHERE:
                return generateCohereEmbeddings(text);
            default:
                throw new IllegalStateException("Unknown provider type: " + providerType);
        }
    }

    /**
     * Generate embeddings using Ollama API
     */
    private List<Double> generateOllamaEmbeddings(String text) throws IOException, InterruptedException {
        if (ollamaAPI != null) {
            // Use ollama4j library for Ollama
            try {
                return ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, text);
            } catch (Exception e) {
                LOGGER.error("❌ Failed to generate Ollama embeddings using library, trying direct API", e);
            }
        }

        // Fallback to direct API call
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        requestJson.addProperty("prompt", text);

        String endpoint = baseUrl + "/api/embeddings";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Ollama embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray embeddingArray = responseJson.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Generate embeddings using OpenAI-compatible API (OpenAI, LM Studio, vLLM, etc.)
     */
    private List<Double> generateOpenAICompatibleEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("input", text);
        requestJson.addProperty("model", embeddingModel);

        String endpoint = baseUrl + "/v1/embeddings";
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOGGER.error("OpenAI-compatible API error: {}", response.body());
            throw new IOException("OpenAI-compatible embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray dataArray = responseJson.getAsJsonArray("data");
        JsonObject firstEmbedding = dataArray.get(0).getAsJsonObject();
        JsonArray embeddingArray = firstEmbedding.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        LOGGER.debug("✅ Generated OpenAI-compatible embeddings: {} dimensions", embeddings.size());
        return embeddings;
    }

    /**
     * Generate embeddings using Google Gemini API
     */
    private List<Double> generateGeminiEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        JsonObject contentObj = new JsonObject();
        JsonArray partsArray = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", text);
        partsArray.add(textPart);
        contentObj.add("parts", partsArray);
        requestJson.add("content", contentObj);

        String endpoint = baseUrl + "/v1beta/models/" + embeddingModel + ":embedContent?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject embedding = responseJson.getAsJsonObject("embedding");
        JsonArray valuesArray = embedding.getAsJsonArray("values");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < valuesArray.size(); i++) {
            embeddings.add(valuesArray.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Generate embeddings using Mistral API
     */
    private List<Double> generateMistralEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        JsonArray inputArray = new JsonArray();
        inputArray.add(text);
        requestJson.add("input", inputArray);

        String endpoint = baseUrl + "/v1/embeddings";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Mistral embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray dataArray = responseJson.getAsJsonArray("data");
        JsonObject firstEmbedding = dataArray.get(0).getAsJsonObject();
        JsonArray embeddingArray = firstEmbedding.getAsJsonArray("embedding");

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < embeddingArray.size(); i++) {
            embeddings.add(embeddingArray.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Generate embeddings using Cohere API
     */
    private List<Double> generateCohereEmbeddings(String text) throws IOException, InterruptedException {
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", embeddingModel);
        JsonArray textsArray = new JsonArray();
        textsArray.add(text);
        requestJson.add("texts", textsArray);
        requestJson.addProperty("input_type", "search_document");

        String endpoint = baseUrl + "/v1/embed";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Cohere embeddings API returned status: " + response.statusCode());
        }

        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray embeddingsArray = responseJson.getAsJsonArray("embeddings");
        JsonArray firstEmbedding = embeddingsArray.get(0).getAsJsonArray();

        List<Double> embeddings = new ArrayList<>();
        for (int i = 0; i < firstEmbedding.size(); i++) {
            embeddings.add(firstEmbedding.get(i).getAsDouble());
        }

        return embeddings;
    }

    /**
     * Get the configured embedding model name
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Get the provider type
     */
    public AIProviderType getProviderType() {
        return providerType;
    }
}

