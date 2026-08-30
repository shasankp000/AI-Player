package net.shasankp000.ServiceLLMClients;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic OpenAI-compatible client that supports custom API base URLs.
 * Supports both Chat Completions and the newer Responses API.
 */
public class GenericOpenAIClient implements LLMClient {
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final Map<String, ApiEndpoint> API_PREFERENCES = new ConcurrentHashMap<>();

    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final String preferenceKey;
    private final HttpClient client;
    private volatile ApiEndpoint preferredEndpoint;

    public static final Logger LOGGER = LoggerFactory.getLogger("GenericOpenAI-Client");

    public GenericOpenAIClient(String apiKey, String modelName, String baseUrl) {
        this.apiKey = apiKey;
        this.modelName = modelName;

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }

        ApiEndpoint configuredEndpoint = detectConfiguredEndpoint(baseUrl);
        this.baseUrl = normalizeBaseUrl(baseUrl);
        String configuredMode = configuredEndpoint != null ? configuredEndpoint.name() : "AUTO";
        String credentialScope = apiKey != null ? Integer.toHexString(apiKey.hashCode()) : "anonymous";
        this.preferenceKey = this.baseUrl + "|" + this.modelName + "|" + configuredMode + "|" + credentialScope;
        this.preferredEndpoint = API_PREFERENCES.getOrDefault(
                this.preferenceKey,
                configuredEndpoint != null ? configuredEndpoint : ApiEndpoint.CHAT_COMPLETIONS);
        this.client = HttpClient.newHttpClient();
    }

    private static ApiEndpoint detectConfiguredEndpoint(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/responses")) {
            return ApiEndpoint.RESPONSES;
        }
        if (normalized.endsWith("/chat/completions") || normalized.endsWith("/completions")) {
            return ApiEndpoint.CHAT_COMPLETIONS;
        }
        return null;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        normalized = normalized.replaceAll("/chat/completions$", "");
        normalized = normalized.replaceAll("/completions$", "");
        normalized = normalized.replaceAll("/responses$", "");
        normalized = normalized.replaceAll("/embeddings$", "");
        return normalized + "/";
    }

    @Override
    public String sendPrompt(String systemPrompt, String userPrompt) {
        ApiEndpoint primaryEndpoint = preferredEndpoint;
        try {
            ApiResponse primaryResponse = sendRequest(primaryEndpoint, systemPrompt, userPrompt);
            if (primaryResponse.isSuccessful()) {
                return extractResponseText(primaryEndpoint, primaryResponse.body());
            }

            if (!shouldTryAlternateEndpoint(primaryResponse.statusCode())) {
                return formatHttpError(primaryResponse);
            }

            ApiEndpoint alternateEndpoint = primaryEndpoint.alternate();
            LOGGER.info("{} returned HTTP {} for model {}; trying {}",
                    primaryEndpoint.displayName,
                    primaryResponse.statusCode(),
                    modelName,
                    alternateEndpoint.displayName);

            ApiResponse alternateResponse = sendRequest(alternateEndpoint, systemPrompt, userPrompt);
            if (alternateResponse.isSuccessful()) {
                preferredEndpoint = alternateEndpoint;
                if (shouldRememberAlternateEndpoint(primaryResponse)) {
                    API_PREFERENCES.put(preferenceKey, alternateEndpoint);
                }
                return extractResponseText(alternateEndpoint, alternateResponse.body());
            }

            return formatCombinedHttpError(primaryResponse, alternateResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while sending prompt", e);
            return "Error: Request interrupted";
        } catch (Exception e) {
            LOGGER.error("Error occurred while sending prompt", e);
            return "Error: " + e.getMessage();
        }
    }

    private ApiResponse sendRequest(ApiEndpoint endpoint, String systemPrompt, String userPrompt)
            throws IOException, InterruptedException {
        JsonObject requestBody = endpoint == ApiEndpoint.RESPONSES
                ? createResponsesRequest(systemPrompt, userPrompt)
                : createChatCompletionsRequest(systemPrompt, userPrompt);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint.path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(endpoint, response.statusCode(), response.body());
    }

    private JsonObject createChatCompletionsRequest(String systemPrompt, String userPrompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);

        JsonArray messages = new JsonArray();
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", userPrompt));

        requestBody.add("messages", messages);
        requestBody.addProperty("max_tokens", MAX_OUTPUT_TOKENS);
        requestBody.addProperty("stream", false);
        return requestBody;
    }

    private JsonObject createResponsesRequest(String systemPrompt, String userPrompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);

        // Although OpenAI accepts a scalar input string, some compatible
        // gateways (including LiteLLM ChatGPT routes) require a message list.
        JsonArray input = new JsonArray();
        input.add(createMessage("system", systemPrompt));
        input.add(createMessage("user", userPrompt));

        requestBody.add("input", input);
        requestBody.addProperty("max_output_tokens", MAX_OUTPUT_TOKENS);
        requestBody.addProperty("store", false);
        requestBody.addProperty("stream", false);
        return requestBody;
    }

    private static JsonObject createMessage(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static boolean shouldTryAlternateEndpoint(int statusCode) {
        return switch (statusCode) {
            case 400, 404, 405, 422, 501 -> true;
            default -> false;
        };
    }

    private static boolean shouldRememberAlternateEndpoint(ApiResponse failedResponse) {
        if (failedResponse.statusCode() == 404
                || failedResponse.statusCode() == 405
                || failedResponse.statusCode() == 501) {
            return true;
        }

        String body = failedResponse.body().toLowerCase(Locale.ROOT);
        return body.contains("responses api")
                || body.contains("/responses")
                || body.contains("chat completions")
                || body.contains("chat/completions")
                || body.contains("unsupported endpoint")
                || body.contains("endpoint is not supported");
    }

    private static String extractResponseText(ApiEndpoint endpoint, String rawResponseBody) {
        try {
            String trimmedBody = rawResponseBody.trim();
            if (trimmedBody.startsWith("data:") || trimmedBody.startsWith("event:")) {
                return extractEventStreamText(endpoint, rawResponseBody);
            }

            JsonElement parsed = JsonParser.parseString(rawResponseBody);
            if (!parsed.isJsonObject()) {
                return "Error: Provider returned an invalid JSON response.";
            }

            JsonObject jsonResponse = parsed.getAsJsonObject();
            if (endpoint == ApiEndpoint.RESPONSES) {
                return extractResponsesText(jsonResponse, rawResponseBody);
            }
            return extractChatCompletionsText(jsonResponse, rawResponseBody);
        } catch (Exception e) {
            LOGGER.warn("Could not parse OpenAI-compatible response: {}", rawResponseBody, e);
            return "Error: Could not parse provider response.";
        }
    }

    private static String extractEventStreamText(ApiEndpoint endpoint, String rawResponseBody) {
        StringBuilder deltas = new StringBuilder();
        String completedText = "";

        for (String line : rawResponseBody.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("data:")) {
                continue;
            }

            String data = trimmedLine.substring("data:".length()).trim();
            if (data.isEmpty() || data.equals("[DONE]")) {
                continue;
            }

            try {
                JsonElement parsedEvent = JsonParser.parseString(data);
                if (!parsedEvent.isJsonObject()) {
                    continue;
                }

                JsonObject event = parsedEvent.getAsJsonObject();
                String eventType = readStringField(event, "type");
                if (eventType.endsWith(".delta")) {
                    appendText(deltas, readStringField(event, "delta"));
                    continue;
                }
                if (eventType.endsWith(".done")) {
                    String doneText = readStringField(event, "text");
                    if (!doneText.isEmpty()) {
                        completedText = doneText;
                    }
                    continue;
                }

                JsonArray choices = getArray(event, "choices");
                if (choices != null) {
                    for (JsonElement choiceElement : choices) {
                        if (!choiceElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject choice = choiceElement.getAsJsonObject();
                        JsonObject delta = getObject(choice, "delta");
                        appendText(deltas, readContentField(delta, "content"));
                    }
                    continue;
                }

                JsonObject eventResponse = getObject(event, "response");
                if (eventResponse != null) {
                    // Lifecycle events such as response.created and
                    // response.in_progress do not contain generated text.
                    if (eventType.equals("response.completed")) {
                        String extracted = extractResponsesText(eventResponse, data);
                        if (!extracted.startsWith("Error:")) {
                            completedText = extracted;
                        }
                    }
                    continue;
                }

                // Other typed lifecycle events (output_item.added,
                // content_part.added, etc.) carry metadata rather than text.
                if (!eventType.isEmpty()) {
                    continue;
                }

                String extracted = endpoint == ApiEndpoint.RESPONSES
                        ? extractResponsesText(event, data)
                        : extractChatCompletionsText(event, data);
                if (!extracted.startsWith("Error:")) {
                    completedText = extracted;
                }
            } catch (Exception e) {
                LOGGER.debug("Ignoring malformed event-stream data: {}", data, e);
            }
        }

        if (!deltas.isEmpty()) {
            return deltas.toString();
        }
        if (!completedText.isEmpty()) {
            return completedText;
        }

        LOGGER.warn("Provider returned an event stream without text output");
        return "Error: Provider returned an empty event stream.";
    }

    private static String extractResponsesText(JsonObject jsonResponse, String rawResponseBody) {
        String topLevelOutputText = readStringField(jsonResponse, "output_text");
        if (!topLevelOutputText.isBlank()) {
            return topLevelOutputText;
        }

        StringBuilder text = new StringBuilder();
        JsonArray output = getArray(jsonResponse, "output");
        if (output != null) {
            for (JsonElement outputElement : output) {
                if (!outputElement.isJsonObject()) {
                    continue;
                }

                JsonObject outputItem = outputElement.getAsJsonObject();
                appendText(text, readStringField(outputItem, "text"));

                JsonArray content = getArray(outputItem, "content");
                if (content == null) {
                    continue;
                }

                for (JsonElement contentElement : content) {
                    if (!contentElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject contentItem = contentElement.getAsJsonObject();
                    appendText(text, readStringField(contentItem, "text"));
                    appendText(text, readStringField(contentItem, "refusal"));
                }
            }
        }

        if (!text.isEmpty()) {
            return text.toString();
        }

        // Some compatible gateways return Chat Completions-shaped data from
        // their Responses endpoint. Accept it rather than rejecting useful text.
        if (jsonResponse.has("choices")) {
            return extractChatCompletionsText(jsonResponse, rawResponseBody);
        }

        JsonObject error = getObject(jsonResponse, "error");
        String errorMessage = readStringField(error, "message");
        if (!errorMessage.isBlank()) {
            return "Error: " + errorMessage;
        }

        JsonObject incompleteDetails = getObject(jsonResponse, "incomplete_details");
        String incompleteReason = readStringField(incompleteDetails, "reason");
        if (!incompleteReason.isBlank()) {
            return "Error: Provider returned an incomplete response (" + incompleteReason + ").";
        }

        LOGGER.warn("Responses API result did not include output text: {}", rawResponseBody);
        return "Error: Provider returned no output text.";
    }

    private static String extractChatCompletionsText(JsonObject jsonResponse, String rawResponseBody) {
        JsonArray choices = getArray(jsonResponse, "choices");
        if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) {
            LOGGER.warn("OpenAI-compatible response did not include choices: {}", rawResponseBody);
            return "Error: Provider returned no choices.";
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = getObject(choice, "message");
        if (message != null) {
            String content = readContentField(message, "content");
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

    private static String readContentField(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName)) {
            return "";
        }

        JsonElement content = object.get(fieldName);
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (!content.isJsonArray()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray()) {
            if (part.isJsonPrimitive()) {
                appendText(text, part.getAsString());
            } else if (part.isJsonObject()) {
                appendText(text, readStringField(part.getAsJsonObject(), "text"));
            }
        }
        return text.toString();
    }

    private static void appendText(StringBuilder destination, String value) {
        if (value != null && !value.isEmpty()) {
            destination.append(value);
        }
    }

    private static JsonArray getArray(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName) || !object.get(fieldName).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(fieldName);
    }

    private static JsonObject getObject(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName) || !object.get(fieldName).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(fieldName);
    }

    private static String readStringField(JsonObject object, String fieldName) {
        if (object == null || !object.has(fieldName)) {
            return "";
        }
        JsonElement element = object.get(fieldName);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    private static String formatHttpError(ApiResponse response) {
        return "Error: " + response.endpoint().displayName + " returned HTTP "
                + response.statusCode() + " - " + response.body();
    }

    private static String formatCombinedHttpError(ApiResponse primary, ApiResponse alternate) {
        return formatHttpError(primary) + "; " + alternate.endpoint().displayName
                + " also returned HTTP " + alternate.statusCode() + " - " + alternate.body();
    }

    /**
     * Checks if the API is reachable and the key is valid by making a
     * quick, lightweight request to the models endpoint.
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

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProvider() {
        return "Generic OpenAI Compatible";
    }

    private enum ApiEndpoint {
        CHAT_COMPLETIONS("chat/completions", "Chat Completions"),
        RESPONSES("responses", "Responses API");

        private final String path;
        private final String displayName;

        ApiEndpoint(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
        }

        private ApiEndpoint alternate() {
            return this == CHAT_COMPLETIONS ? RESPONSES : CHAT_COMPLETIONS;
        }
    }

    private record ApiResponse(ApiEndpoint endpoint, int statusCode, String body) {
        private boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
