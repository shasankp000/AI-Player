package net.shasankp000.ServiceLLMClients;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.ChatUtils.Helper.RAG2;
import net.shasankp000.ChatUtils.NLPProcessor;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.Exception.intentMisclassification;
import net.shasankp000.FunctionCaller.FunctionCallerV2;
import net.shasankp000.Overlay.ThinkingStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMServiceHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("LLMServiceHandler");
    private static final ExecutorService BOT_TASK_POOL = Executors.newCachedThreadPool();
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>([\\s\\S]*?)</think>");
    public static String initialResponse = "";
    private static final String host = "http://localhost:11434";
    public static final OllamaAPI ollamaAPI = new OllamaAPI(host);
    public static boolean isInitialized = false;

    private static String generateSystemPrompt(String botName) {

        return
                "You are a Minecraft player named " + botName + " who is connected to Minecraft using a mod. You exist within the Minecraft world and can interact with the player and the environment just like any other player in the game. Your job is to engage in conversations with the player, respond to their questions, offer help, and provide information about the game. Address the player directly and appropriately, responding to their name or as 'Player' if their name is not known. Do not refer to the player as " + botName + ", only address yourself as " + botName + " Keep your responses relevant to Minecraft and make sure to stay in character as a helpful and knowledgeable assistant within the game."
                        +
                        """
                        
                        When the player asks you to perform an action, such as providing information, offering help, or interacting with the game world, such as:
                        
                        Providing game tips or crafting recipes.
                        Giving information about specific Minecraft entities, items, or biomes.
                        Assisting with in-game tasks, like building structures or exploring areas.
                        Interacting with the environment, such as planting crops or fighting mobs.
                       
                        Always ensure your responses are timely and contextually appropriate, enhancing the player's gaming experience. Remember to keep track of the sequence of events and maintain continuity in your responses. If an event is primarily informational or involves internal actions, it may be sufficient just to remember it without a verbal response.
                        
                        If a player uses inappropriate language or discusses inappropriate topics, handle the situation by gently redirecting the conversation or by providing a neutral response that discourages further inappropriate behavior.
                        
                        For example:
                        
                        If a player uses vulgar language, you can respond with: "Let's keep our chat friendly and fun! Is there something else about Minecraft you'd like to discuss?"
                        If a player insists on inappropriate topics, you can say: "I'm here to help with Minecraft-related questions. How about we talk about your latest adventure in the game?"
                        If a player says these words "kill yourself" or "kys", you should respond calmly and normally and tell the player to see the beauty of life.
                        
                        
                        Your pronouns, are by default, to be addressed as the pronouns based on your name's gender (female/male). However if the player decides to address you with different pronouns, you must not object. For now, either introduce yourself or crack a random joke; the joke should be completely family-friendly, or just greet the player.
                        
                        The name Steve has the pronouns: he/him
                        The name Alex has the pronouns: she/her
                        
                        If the player asks you as to why you were put here in the first place: Remember that it was the developer's idea to solve the ever existing problem of loneliness in minecraft as much as possible by making this mod.
                        
                        For now introduce yourself with your name.
                        """;

    }

    public static void processLLMOutput(String fullResponse, String botName, ServerCommandSource botSource) {
        LOGGER.info("processLLMOutput called with response: '{}', botName: '{}'", fullResponse, botName);

        if (fullResponse == null || fullResponse.trim().isEmpty()) {
            LOGGER.warn("fullResponse is null or empty");
            return;
        }

        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
            LOGGER.info("Think block found");
            String thinking = matcher.group(1).trim();
            String remainder = fullResponse.replace(matcher.group(0), "").trim();

            ThinkingStateManager.start(botName);
            ChatUtils.sendChatMessages(botSource, botName + " is thinking...");

            for (String line : thinking.split("\\n")) {
                ThinkingStateManager.appendThoughtLine(line);
            }

            ThinkingStateManager.end();
            ChatUtils.sendChatMessages(botSource, botName + " is done thinking!");

            if (!remainder.isEmpty()) {
                LOGGER.info("Sending remainder: '{}'", remainder);
                ChatUtils.sendChatMessages(botSource, botName + ": " + remainder);
            } else {
                LOGGER.info("Remainder is empty");
            }
        } else {
            LOGGER.info("No think block found, sending full response: '{}'", fullResponse);
            ChatUtils.sendChatMessages(botSource, fullResponse);
        }
    }


    public static void sendInitialResponse(ServerCommandSource botSource, LLMClient client) {
        MinecraftServer server = botSource.getServer();
        String botName = botSource.getPlayer().getName().getString();

        CompletableFuture<String> initFuture = CompletableFuture.supplyAsync(() -> {
            try {
                if (client.isReachable()) {
                    isInitialized = true;
                    LOGGER.info("{} client initialized.", client.getProvider());
                    ChatUtils.sendChatMessages(botSource, "Established connection to " + client.getProvider() + "'s servers. Using " + AIPlayer.CONFIG.getSelectedLanguageModel());

                    // Fetch and return the initial response
                    String response = client.sendPrompt(generateSystemPrompt(botName), "Initializing chat");
                    LOGGER.info("Initial response received: '{}'", response);
                    LOGGER.info("Response length: {}", response != null ? response.length() : "null");
                    initialResponse = response;
                    return response;
                } else {
                    LOGGER.error("Error! Could not reach {} client. Please try again!", client.getProvider());
                    ChatUtils.sendChatMessages(botSource, "Error! Could not reach " + client.getProvider() + "'s servers. Please check your internet connection or try again after sometime!");
                    return null;
                }
            } catch (Exception e) {
                LOGGER.error("Exception in initFuture: {}", e.getMessage(), e);
                return null;
            }
        });

        initFuture.thenAccept(response -> {
            try {
                LOGGER.info("thenAccept called with response: '{}'", response);
                if (response != null && !response.trim().isEmpty()) {
                    // Process the response on the main thread

                    LOGGER.info("Scheduling processLLMOutput on main thread for bot: {}", botName);
                    server.execute(() -> {
                        try {
                            LOGGER.info("About to call processLLMOutput with: '{}'", response);
                            processLLMOutput(response, botName, botSource);
                            LOGGER.info("processLLMOutput completed");
                        } catch (Exception e) {
                            LOGGER.error("Exception in processLLMOutput: {}", e.getMessage(), e);
                        }
                    });

                    // Handle database operations
                    CompletableFuture.runAsync(() -> {
                        // ... your database code
                    });
                } else {
                    LOGGER.warn("Response is null or empty, not processing");
                }
            } catch (Exception e) {
                LOGGER.error("Exception in thenAccept: {}", e.getMessage(), e);
            }
        }).exceptionally(throwable -> {
            LOGGER.error("CompletableFuture failed: {}", throwable.getMessage(), throwable);
            return null;
        });
    }

    /**
     * Entry point for running the bot's logic from a chat message.
     * This method triggers the intent routing and is called by the main game thread.
     *
     * @param message The chat message from the player.
     * @param botName The name of the bot.
     * @param playerUUID The UUID of the player.
     */
    public static void runFromChat(String message, String botName, UUID playerUUID, LLMClient client) {
        MinecraftServer server = AIPlayer.serverInstance;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            LOGGER.error("Bot {} not online.", botName);
            return;
        }
        ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

        server.execute(() -> {
            Thread.currentThread().setName("LLM-Chat-Worker");
            try {
                routeIntent(message, botSource, playerUUID, client);
            } catch (Exception e) {
                LOGGER.error("Chat processing error: ", e);
                ChatUtils.sendChatMessages(botSource, "⚠️ I'm confused! Please report this.");
            }
        });
    }

    /**
     * Routes the user's intent to the appropriate function (RAG, FunctionCaller, etc.).
     *
     * @param message The user's message.
     * @param botSource The bot's command source.
     * @param playerUUID The player's UUID.
     * @throws Exception if an error occurs during intent routing.
     */
    private static void routeIntent(String message, ServerCommandSource botSource, UUID playerUUID, LLMClient client) throws Exception {
        NLPProcessor.Intent intent = NLPProcessor.getIntention(message);

        LOGGER.info("📨 Received intent: {}", intent);


        switch (intent) {
            case GENERAL_CONVERSATION, ASK_INFORMATION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("LLM-RAG2-Worker");
                    LOGGER.info("🧵 Started RAG2 worker thread");
                    RAG2.run(message, botSource, intent, client);
                    LOGGER.info("✅ Finished RAG2 worker thread");
                });
            }

            case REQUEST_ACTION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("LLM-Function-Caller-Worker");
                    LOGGER.info("🧵 Started FunctionCallerV2 worker thread");
                    new FunctionCallerV2(botSource, playerUUID);
                    FunctionCallerV2.run(message, client);
                    LOGGER.info("✅ Finished FunctionCallerV2 worker thread");
                });
            }

            default -> {
                LOGGER.warn("⚠️ Intent unclear, retrying with LLM classification...");
                ChatUtils.sendChatMessages(botSource, "🔍 Reanalyzing...");

                NLPProcessor.Intent retry = retryIntentLLM(message);

                LOGGER.info("📨 Retry intent: {}", retry);

                if (retry == NLPProcessor.Intent.GENERAL_CONVERSATION || retry == NLPProcessor.Intent.ASK_INFORMATION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("LLM-RAG2-Retry-Worker");
                        LOGGER.info("🧵 Started RAG2 retry worker thread");
                        RAG2.run(message, botSource, retry, client);
                        LOGGER.info("✅ Finished RAG2 retry worker thread");
                    });
                } else if (retry == NLPProcessor.Intent.REQUEST_ACTION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("LLM-Function-Caller-Retry-Worker");
                        LOGGER.info("🧵 Started FunctionCallerV2 retry worker thread");
                        new FunctionCallerV2(botSource, playerUUID);
                        FunctionCallerV2.run(message);
                        LOGGER.info("✅ Finished FunctionCallerV2 retry worker thread");
                    });
                } else {
                    throw new intentMisclassification("LLM failed to classify intent.");
                }
            }
        }
    }


    private static NLPProcessor.Intent retryIntentLLM(String message) {
        return NLPProcessor.getIntentionFromLLM(message);
    }
}
