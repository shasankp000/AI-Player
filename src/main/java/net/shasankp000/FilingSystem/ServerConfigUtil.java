package net.shasankp000.FilingSystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class ServerConfigUtil {

    /**
     * Updates the "selectedLanguageModel" field in the config file.
     * Adjust the file path if your config is stored in a subfolder.
     */
    public static void updateSelectedLanguageModel(String newModelName) {
        // Get the config directory (this is a Path to the config folder)
        Path configDir = FabricLoader.getInstance().getConfigDir();
        // Assuming your file is named "settings.json5" and is in the root of the config directory.
        // If it's in a subfolder (e.g. "ai-player/settings.json5"), adjust accordingly.
        Path configFile = configDir.resolve("settings.json5");

        try {
            // Read the file content
            String content = Files.readString(configFile);
            // Parse the JSON content (assuming it’s JSON-compatible)
            JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
            // Update the field
            jsonObject.addProperty("selectedLanguageModel", newModelName);
            // Convert the updated object back to a JSON string (with pretty printing)
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String updatedJson = gson.toJson(jsonObject);
            // Write the updated JSON back to the file
            Files.writeString(configFile, updatedJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update config file", e);
        }
    }


}
