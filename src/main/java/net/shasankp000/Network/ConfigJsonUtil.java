package net.shasankp000.Network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.shasankp000.AIPlayer;

import java.util.List;
import java.util.Map;

public class ConfigJsonUtil {

    public static String configToJson() {
        // Retrieve config values from the generated CONFIG
        List<String> modelList = AIPlayer.CONFIG.modelList();
        String selectedLanguageModel = AIPlayer.CONFIG.selectedLanguageModel();
        Map<String, String> botGameProfile = AIPlayer.CONFIG.BotGameProfile();

        // Build JSON using Gson's JsonObject and JsonArray
        JsonObject root = new JsonObject();

        // Add modelList as a JSON array
        JsonArray modelsArray = new JsonArray();
        for (String model : modelList) {
            modelsArray.add(model);
        }
        root.add("modelList", modelsArray);

        // Add selectedLanguageModel as a property
        root.addProperty("selectedLanguageModel", selectedLanguageModel);

        // Add BotGameProfile as a JSON object
        JsonObject profileObject = new JsonObject();
        for (Map.Entry<String, String> entry : botGameProfile.entrySet()) {
            profileObject.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("BotGameProfile", profileObject);

        // Return the JSON string (pretty printing optional)
        return root.toString();
    }
}
