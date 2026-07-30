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
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Model fetcher for generic OpenAI-compatible APIs.
 * This fetcher can work with any provider that follows the OpenAI models API standard.
 */
public class GenericOpenAIModelFetcher implements ModelFetcher {

    private final HttpClient client;
    private final String baseUrl;
    private static final Logger LOGGER = LoggerFactory.getLogger("GenericOpenAIModelFetcher");

    public GenericOpenAIModelFetcher(String baseUrl) {
        this.client = HttpClient.newHttpClient();
        // Ensure baseUrl ends with "/" but doesn't have double slashes
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        String trimmedUrl = baseUrl.trim();
        this.baseUrl = trimmedUrl.endsWith("/") ? trimmedUrl : trimmedUrl + "/";
    }

    @Override
    public List<String> fetchModels(String apiKey) {
        List<String> modelList = new ArrayList<>();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return modelList; // Return empty list if no API key is provided
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {

                LOGGER.info("Request url: {}", request.uri());
                LOGGER.info("Response code: {}", response.statusCode());

                JsonElement jsonElement = JsonParser.parseString(response.body());
                JsonArray models;

                if (jsonElement.isJsonArray()) {
                    models = jsonElement.getAsJsonArray();
                } else {
                    models = jsonElement.getAsJsonObject().getAsJsonArray("data");
                }

                for (JsonElement modelElement : models) {
                    JsonObject modelObject = modelElement.getAsJsonObject();
                    String modelId = modelObject.get("id").getAsString();
                    
                    // Add all models - let users choose what they want
                    // Generic providers might have different naming conventions
                    modelList.add(modelId);
                }

                LOGGER.info("Fetched {} models from Generic OpenAI API", modelList.size());
                LOGGER.info("Models: \n {}", modelList);

            }

            else {
                LOGGER.error("Failed to fetch models. HTTP Status: {}. Response: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            LOGGER.error("Error fetching models from Generic OpenAI API: {}", e.getMessage());
        }

        return modelList;
    }
}
