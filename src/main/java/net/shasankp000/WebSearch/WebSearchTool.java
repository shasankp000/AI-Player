package net.shasankp000.WebSearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;
import net.shasankp000.AIPlayer;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.ServiceLLMClients.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSearchTool {

    private static final Logger logger = LoggerFactory.getLogger("web-search-tool");
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
    private static String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();
    private static String llmMode = System.getProperty("aiplayer.llmMode", "ollama");
    private static final Path CACHE_DIR = Paths.get("web_cache");

    static {
        try {
            Files.createDirectories(CACHE_DIR);

        } catch (Exception e) {
            logger.error("❌ Could not create cache directory: {}", e.getMessage());
        }
    }

    public static String search(String query) {
        String cleanQuery = query.trim();
        String generatedQuery = createQuery(cleanQuery);
        String cacheKey = hash(generatedQuery + AISearchConfig.PREFERRED_PROVIDER);
        String cached = getCached(cacheKey);
        if (cached != null) {
            logger.info("✅ Using cached search result for: {}", cleanQuery);
            return cached;
        }

        String result;
        switch (AISearchConfig.PREFERRED_PROVIDER.toLowerCase()) {
            case "gemini" -> result = geminiSearch(cleanQuery);
            case "serper" -> result = serperSearch(cleanQuery);
            default -> result = "❌ No search provider configured.";
        }

        if (!result.startsWith("❌")) {
            saveCache(cacheKey, result);
        } else {
            logger.info("ℹ️ Not caching failed/empty result for: {}", cleanQuery);
        }

        return result;
    }

    private static String geminiSearch(String query) {
        try {
            String apiKey = AIPlayer.CONFIG.getGeminiKey();
            if (apiKey.isBlank()) return "❌ Gemini API key is missing.";

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
            String payload = """
            {
              "contents": [{
                "parts": [{
                  "text": "Please provide a factual web-style answer for this question: %s"
                }]
              }]
            }
            """.formatted(query);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode root = new ObjectMapper().readTree(res.body());
            String answer = root.at("/candidates/0/content/parts/0/text").asText();

            logger.info("🌐 Gemini result: {}", answer);
            return answer.isBlank() ? "❌ Gemini returned no answer." : answer;

        } catch (Exception e) {
            logger.error("❌ Gemini search failed: {}", e.getMessage());
            return "❌ Gemini search failed: " + e.getMessage();
        }
    }

    private static String serperSearch(String query) {
        try {
            String apiKey = AISearchConfig.SERPER_API_KEY;
            if (apiKey.isBlank()) return "❌ Serper API key is missing.";

            String url = "https://google.serper.dev/search";
            String payload = "{ \"q\": \"%s\" }".formatted(query);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-API-KEY", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode root = new ObjectMapper().readTree(res.body());
            JsonNode organic = root.path("organic");
            if (organic.isArray() && organic.size() > 0) {
                JsonNode first = organic.get(0);
                String title = first.path("title").asText();
                String snippet = first.path("snippet").asText();
                String link = first.path("link").asText();
                String result = "🔗 " + title + "\n" + snippet + "\n" + link;
                logger.info("🌐 Serper result: {}", result);
                return result;
            }

            return "❌ Serper returned no results.";

        } catch (Exception e) {
            logger.error("❌ Serper search failed: {}", e.getMessage());
            return "❌ Serper search failed: " + e.getMessage();
        }
    }

    private static String createQuery(String prompt) {
        String query_msg = "You are a first-principles reasoning search query AI agent. Your task is to a query which will be used to search the web on the topic of the prompt. The output must be a single, valid, properly formed, query. Here are a few examples of input strings you may receive:\n" +
                """
                 "How can I build an automatic farm in Minecraft?",
                 "What were the resources needed for the enchantment table?",
                 "Tell me about the mining strategy we discussed last time."
                 "Did you go somewhere recently?"
                 "What ores did you mine recently?"
                 "Please go to coordinates 10 20 30."
                 "Please be on the lookout for hostile mobs nearby."
                
                \n""" +
                "And here are examples of the format your output must follow:\n" +
                "What are the steps to build an automatic farm in Minecraft?, \n" +
                "What resources were listed for creating an enchantment table?, \n" +
                "What mining strategy was discussed on yyyy/mm/dd hh:mm:ss?, \n" +
                "Where did the bot go to recently?, \n" +
                "What ores did the bot mine recently?\n" +

                "Please remember that it is absolutely crucial that you generate queries relevant to the user prompts. DO NOT ANSWER AS AN ASSISTANT. THIS WILL CAUSE THE WEB SEARCH TOOL PROBLEMS. JUST OUTPUT THE QUERY. \n" +

                "Return only one properly drafted query for the user prompt.\n";


        List<Map<String, String>> queryConvo = new ArrayList<>();
        Map<String, String> queryMap1 = new HashMap<>();

        queryMap1.put("role", "system");
        queryMap1.put("content", query_msg);

        queryConvo.add(queryMap1);

        String response = "";

        switch (llmMode) {
            case "openai", "gemini", "claude", "grok":
                LLMClient client = LLMClientFactory.createClient(llmMode);

                if (client != null)  {
                    if (client.isReachable()) {
                        response = client.sendPrompt(query_msg, prompt);
                    }
                    else {
                        logger.error("Error! {} client is not reachable. Please check your internet connection or try again later", client.getProvider());
                    }
                }
                else {
                    logger.error("Error! {} client is null!", llmMode);
                }
                break;

            case "ollama":
                ollamaAPI.setRequestTimeoutSeconds(120);

                try {
                    List<OllamaChatMessage> messages = new java.util.ArrayList<>();
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, queryConvo.toString()));
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, prompt));

                    net.shasankp000.OllamaClient.OllamaThinkingResponse thinkingResponse =
                            net.shasankp000.OllamaClient.OllamaAPIHelper.smartChat(
                                    ollamaAPI,
                                    "http://localhost:11434",
                                    selectedLM,
                                    messages
                            );

                    response = thinkingResponse.getContent();
                    logger.info("Generated query: {}", response);

                } catch (IOException | InterruptedException e) {
                    logger.error("Caught exception while creating queries: {} ", (Object) e.getStackTrace());
                    logger.debug("Response before exception: {}", response);
                    throw new RuntimeException(e);
                }

            default:
                logger.warn("Unsupported provider detected.");
                return "Unsupported LLM provider used. Queries could not be generated.";
        }

        return response;
    }

    private static String createQuery(String prompt, LLMClient client) {
        String query_msg = "You are a first-principles reasoning search query AI agent. Your task is to a query which will be used to search the web on the topic of the prompt. The output must be a single, valid, properly formed, query. Here are a few examples of input strings you may receive:\n" +
                """
                 "How can I build an automatic farm in Minecraft?",
                 "What were the resources needed for the enchantment table?",
                 "Tell me about the mining strategy we discussed last time."
                 "Did you go somewhere recently?"
                 "What ores did you mine recently?"
                 "Please go to coordinates 10 20 30."
                 "Please be on the lookout for hostile mobs nearby."
                
                \n""" +
                "And here are examples of the format your output must follow:\n" +
                "What are the steps to build an automatic farm in Minecraft?, \n" +
                "What resources were listed for creating an enchantment table?, \n" +
                "What mining strategy was discussed on yyyy/mm/dd hh:mm:ss?, \n" +
                "Where did the bot go to recently?, \n" +
                "What ores did the bot mine recently?\n" +

                "Please remember that it is absolutely crucial that you generate queries relevant to the user prompts. DO NOT ANSWER AS AN ASSISTANT. THIS WILL CAUSE THE WEB SEARCH TOOL PROBLEMS. JUST OUTPUT THE QUERY. \n" +

                "Return only one properly drafted query for the user prompt.\n";


        List<Map<String, String>> queryConvo = new ArrayList<>();
        Map<String, String> queryMap1 = new HashMap<>();

        queryMap1.put("role", "system");
        queryMap1.put("content", query_msg);

        queryConvo.add(queryMap1);
        String response = "";

        if (client.isReachable()) {
            response = client.sendPrompt(queryConvo.toString(), prompt);
        }
        else {
            logger.warn("{} is not reachable at the moment, falling back to ollama client.", client.getProvider());

            try {
                List<OllamaChatMessage> messages = new java.util.ArrayList<>();
                messages.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, queryConvo.toString()));
                messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, prompt));

                net.shasankp000.OllamaClient.OllamaThinkingResponse thinkingResponse =
                        net.shasankp000.OllamaClient.OllamaAPIHelper.smartChat(
                                ollamaAPI,
                                "http://localhost:11434",
                                selectedLM,
                                messages
                        );

                response = thinkingResponse.getContent();
                logger.info("Generated query: {}", response);

            } catch (IOException | InterruptedException e) {
                logger.error("Caught exception while creating queries: {} ", (Object) e.getStackTrace());
                logger.debug("Response before exception: {}", response);
                throw new RuntimeException(e);
            }
        }


        return response;
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static void saveCache(String hash, String content) {
        try {
            Path file = CACHE_DIR.resolve(hash + ".txt");
            Files.writeString(file, content);
            logger.info("💾 Saved search result to cache.");
        } catch (Exception ignored) {}
    }

    private static String getCached(String hash) {
        Path file = CACHE_DIR.resolve(hash + ".txt");
        if (Files.exists(file)) {
            try {
                return Files.readString(file);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
