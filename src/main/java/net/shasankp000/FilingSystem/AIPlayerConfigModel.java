package net.shasankp000.FilingSystem;

import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Sync;
import net.shasankp000.Exception.ollamaNotReachableException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Config(name = "settings", wrapperName = "AIPlayerConfig", saveOnModification = true)
public class AIPlayerConfigModel {

    public List<String> modelList;
    public String selectedLanguageModel;


    public static String selectedModel;

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");


    {
        try {
            modelList = getLanguageModels.get();
            selectedModel = modelList.get(0); // set a language model by default
        } catch (ollamaNotReachableException e) {
            LOGGER.error("{}", e.getMessage());
        }
    }


    // some getters and setters. For UI purpose.

    public String getSelectedLanguageModel() {

        return selectedModel;
    }

    public void setSelectedLanguageModel(String selectedLanguageModel) {
        selectedModel = selectedLanguageModel;
    }

}



