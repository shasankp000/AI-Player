package net.shasankp000.ServiceLLMClients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

/**
 * Generic OpenAI-compatible client that supports custom API base URLs.
 * This allows using alternative providers like OpenRouter that follow the OpenAI API standard.
 */
public class GenericOpenAIClient implements LLMClient {
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final HttpClient client;
    public static final Logger LOGGER = LoggerFactory.getLogger("GenericOpenAI-Client");

    public GenericOpenAIClient(String apiKey, String modelName, String baseUrl) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        // Ensure baseUrl ends with "/" but doesn't have double slashes
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.client = HttpClient.newHttpClient();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        normalized = normalized.replaceAll("/chat/completions$", "");
        normalized = normalized.replaceAll("/completions$", "");
        normalized = normalized.replaceAll("/embeddings$", "");
        return normalized + "/";
    }

    @Override
    public String sendPrompt(String systemPrompt, String userPrompt) {
        try {
            // Construct the request body for chat completions
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", this.modelName);

            JsonArray messages = new JsonArray();

            // 1. Create the system message object and add it to the array
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);

            // 2. Create the user message object and add it to the array
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", userPrompt);
            messages.add(userMessage);

            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", 1024);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(requestBody.toString()));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            // Handle HTTP error codes
            if (response.statusCode() != 200) {
                return "Error: " + response.statusCode() + " - " + response.body();
            }

            // Parse the JSON response
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

            return extractResponseText(jsonResponse, response.body());

        } catch (Exception e) {
            LOGGER.error("Error occurred while sending prompt", e);
            return "Error: " + e.getMessage();
        }
    }

    private static String extractResponseText(JsonObject jsonResponse, String rawResponseBody) {
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            LOGGER.warn("OpenAI-compatible response did not include choices: {}", rawResponseBody);
            return "Error: Provider returned no choices.";
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        if (message != null) {
            String content = readStringField(message, "content");
            if (!content.isBlank()) {
                return content;
            }

            String reasoning = readStringField(message, "reasoning");
            if (!reasoning.isBlank()) {
                return reasoning;
            }
        }

        String text = readStringField(choice, "text");
        if (!text.isBlank()) {
            return text;
        }

        String finishReason = readStringField(choice, "finish_reason");
        LOGGER.warn("OpenAI-compatible response had no text content. finish_reason={}, body={}", finishReason, rawResponseBody);
        return "Error: Provider returned an empty message content.";
    }

    private static String readStringField(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName)) {
            return "";
        }
        JsonElement element = object.get(fieldName);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    /**
     * Checks if the API is reachable and the key is valid by making a
     * quick, lightweight request to the models endpoint.
     *
     * @return true if the API returns a 200 status code, false otherwise.
     */
    @Override
    public boolean isReachable() {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "models"))
                    .GET();

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProvider() {
        return "Generic OpenAI Compatible";
    }
}
