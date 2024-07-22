package net.shasankp000.OllamaClient;

import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;

import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelNameChecker {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static boolean isValidModelName(String modelName) {
        Field[] fields = OllamaModelType.class.getFields();

        for (Field field : fields) {
            try {
                if (field.get(null).equals(modelName)) {
                    return true;
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Model doest not exist: {}", e.getMessage());
            }
        }
        return false;
    }
}
