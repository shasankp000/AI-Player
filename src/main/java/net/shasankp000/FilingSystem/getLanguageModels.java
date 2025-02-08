package net.shasankp000.FilingSystem;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.Model;
import net.shasankp000.Exception.ollamaNotReachableException;
import net.shasankp000.OllamaClient.ollamaClient;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class getLanguageModels {

    public static List<String> get() throws ollamaNotReachableException {

        List<Model> models;
        Set<String> modelSet = new HashSet<>(); // using a HashSet to avoid duplicates.

        if (ollamaClient.pingOllamaServer()) {

            String host = "http://localhost:11434/";

            OllamaAPI ollamaAPI = new OllamaAPI(host);

            try {
                models = ollamaAPI.listModels();
            } catch (OllamaBaseException | IOException | InterruptedException | URISyntaxException e) {
                throw new RuntimeException(e);
            }


            for (Model model: models) {

                modelSet.add(model.getModel());

            }
        }

        else {

            throw new ollamaNotReachableException("Ollama Server is not reachable!");

        }

        return new ArrayList<>(modelSet); // Convert the set back to a list

    }

}
