package net.shasankp000.ChatUtils.Helper;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtils {

    public static String cleanJsonString(String jsonString) {
        // Remove ```json and ``` markers
        jsonString = jsonString.replaceAll("```json", "").replaceAll("```", "").trim();

        // Remove non-printable characters
        jsonString = jsonString.replaceAll("[^\\x20-\\x7E]", "").replaceAll("\\\\n", "").replaceAll("\\s+", " ");

        // Attempt to correct common JSON structure errors
        jsonString = correctParameterNames(jsonString);

        // Ensure proper JSON format
        jsonString = jsonString.replaceAll("\\s*:\\s*", ":").replaceAll("\\s*,\\s*", ",");
        jsonString = jsonString.replaceAll("}\\s*]", "}]");

        // If the JSON still seems malformed, attempt to manually correct it
        if (!isValidJson(jsonString)) {
            jsonString = attemptManualCorrection(jsonString);
        }

        return jsonString;
    }

    private static String correctParameterNames(String jsonString) {
        // Fix parameter names in a malformed JSON string
        jsonString = jsonString.replaceAll("\"name\":", "\"parameterName\":");
        jsonString = jsonString.replaceAll("\"value\":", "\"parameterValue\":");

        // Fix other potential issues
        Pattern pattern = Pattern.compile("\"parameterName\\d+\":\"([a-zA-Z]+)\",\\s*\"parameterValue\":\"([^\"]+)\"");
        StringBuffer sb = getStringBuffer(jsonString, pattern);

        return sb.toString();
    }

    private static StringBuffer getStringBuffer(String jsonString, Pattern pattern) {
        Matcher matcher = pattern.matcher(jsonString);
        StringBuffer sb = new StringBuffer();

        int counter = 0;
        while (matcher.find()) {
            matcher.appendReplacement(sb, "\"parameterName\":\"" + matcher.group(1) + "\",\"parameterValue\":\"" + matcher.group(2) + "\"");
            counter++;
        }
        matcher.appendTail(sb);

        // Ensure the parameter array is correctly closed
        if (counter > 0 && !jsonString.endsWith("}]")) {
            sb.append("}]");
        }
        return sb;
    }

    private static boolean isValidJson(String jsonString) {
        try {
            JsonReader reader = new JsonReader(new StringReader(jsonString));
            reader.setLenient(true);
            JsonParser.parseReader(reader).getAsJsonObject();
            return true;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return false;
        }
    }

    private static String attemptManualCorrection(String jsonString) {
        // Attempt to manually correct known issues with the JSON string
        jsonString = jsonString.replaceAll("\"parameterName\\d+\":", "\"parameterName\":");
        jsonString = jsonString.replaceAll("\"parameterValue([a-zA-Z]+)\":", "\"parameterValue\":");

        // Fix trailing commas and other common mistakes
        jsonString = jsonString.replaceAll(",\\s*}", "}");
        jsonString = jsonString.replaceAll(",\\s*]", "]");

        return jsonString;
    }

}
