package net.shasankp000.FilingSystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.shasankp000.Exception.ollamaNotReachableException;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.Model;
import net.shasankp000.OllamaClient.ollamaClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class getLanguageModels {

    public static List<String> get() throws ollamaNotReachableException {
        Set<String> modelSet = new HashSet<>();

        // If running on the client (or integrated server), use the ollamaClient-based logic.
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            if (ollamaClient.pingOllamaServer()) {
                String host = "http://localhost:11434/";
                OllamaAPI ollamaAPI = new OllamaAPI(host);
                List<Model> models;
                try {
                    models = ollamaAPI.listModels();
                } catch (OllamaBaseException | IOException | InterruptedException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                for (Model model : models) {
                    modelSet.add(model.getModel());
                }
            } else {
                throw new ollamaNotReachableException("Ollama Server is not reachable!");
            }
        } else {
            // On a dedicated server, manually query the Ollama server and parse the JSON using Gson.
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:11434/api/tags"))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    // Parse the response as JSON
                    JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                    // Extract the "models" array
                    JsonArray modelsArray = jsonObject.getAsJsonArray("models");
                    for (JsonElement element : modelsArray) {
                        JsonObject modelObject = element.getAsJsonObject();
                        // Only add the "name" field from each model object
                        String modelName = modelObject.get("name").getAsString();
                        modelSet.add(modelName);
                    }
                } else {
                    throw new ollamaNotReachableException("Ollama Server returned status code: " + response.statusCode());
                }
            } catch (URISyntaxException | IOException | InterruptedException e) {
                throw new ollamaNotReachableException("Error pinging Ollama Server: " + e.getMessage());
            }
        }
        return new ArrayList<>(modelSet);
    }

}
