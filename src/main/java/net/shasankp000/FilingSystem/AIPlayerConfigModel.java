package net.shasankp000.FilingSystem;

import io.wispforest.owo.config.annotation.Config;
import net.shasankp000.Exception.ollamaNotReachableException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Config(name = "settings", wrapperName = "AIPlayerConfig", saveOnModification = true)
public class AIPlayerConfigModel {

    public List<String> modelList;
    public String selectedLanguageModel;


    public static String selectedModel;

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public Map<String, String> BotGameProfile = new HashMap<>();


    {
        try {
            modelList = getLanguageModels.get();
            selectedModel = modelList.get(0);
            selectedLanguageModel = selectedModel; // set a language model by default
        } catch (ollamaNotReachableException e) {
            LOGGER.error("{}", e.getMessage());
        }
    }


    // some getters and setters. For UI and config purpose.

    public String getSelectedLanguageModel() {

        return selectedLanguageModel;
    }

    public void setSelectedLanguageModel(String selectedLanguageModel) {
        selectedModel = selectedLanguageModel;
    }


    public Map<String, String> getBotGameProfile() {

        return BotGameProfile;
    }

    public void setBotGameProfile(HashMap<String, String> botGameProfile) {
        BotGameProfile = botGameProfile;
    }

}



