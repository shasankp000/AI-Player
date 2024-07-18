package net.shasankp000.OllamaClient;

// This is still a work in progress.

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;


public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static List<OllamaChatMessage> chatHistory = null;
    private static final String host = "http://localhost:11434";
    private static boolean isInitialized = false;
    public static String botName = "Steve";


    public static void getPlayerMessage() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("tellBot")
                .then(CommandManager.argument("botName", StringArgumentType.string())
                .then(CommandManager.argument("message", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(ollamaClient::execute)))));

    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        botName = StringArgumentType.getString(context, "botName");
        String message = StringArgumentType.getString(context, "message");

        LOGGER.info("Player sent a message: {}", message);

        testHttpRequest(context, botName);
        callClient(context, message);

        return 1;
    }

    public static boolean pingOllamaServer() {

        OllamaAPI ollamaAPI = new OllamaAPI(host);

        boolean isOllamaServerReachable = ollamaAPI.ping();

        System.out.println("Is Ollama server alive: " + isOllamaServerReachable);

        return isOllamaServerReachable;

    }

    public static void testHttpRequest(CommandContext<ServerCommandSource> context, String botName) {
        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity player = context.getSource().getPlayer();

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();

        if (pingOllamaServer()) {
            try {
            URL url = new URL(host);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            System.out.println(responseCode);

            server.getCommandManager().executeWithPrefix(botSource, "/say HTTP Response: " + responseCode + " " + responseMessage);
             }
            catch (Exception e) {
            server.getCommandManager().executeWithPrefix(botSource, "/say HTTP Request failed: " + e.getMessage());
            }
        }


    }


    // Method to initialize Ollama client and set the system prompt
    public static void initializeOllamaClient(String botName) {
        if (!isInitialized) {
            LOGGER.info("Initializing Ollama Client");

            String host = "http://localhost:11434/";
            OllamaAPI ollamaAPI = new OllamaAPI(host);

            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.TINYLLAMA);

            // Initial system prompt
            OllamaChatRequestModel requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, "You are an AI assistant who has been connected to Minecraft using a mod. Your job is to talk, interact with the player and the world, and behave as if you are a second player in his world. Your name is " + botName + ". If the player talks to you, you will read the message thoroughly and then decide how long your response will be. After that you will send your response. Don't say anything now, wait for the player to say something.")
                    .withMessage(OllamaChatMessageRole.USER, "Initializing chat.")
                    .build();

            try {
                chatResult = ollamaAPI.chat(requestModel);
                chatHistory = chatResult.getChatHistory();

                // Ignore the first message (response to system prompt)
                if (!chatHistory.isEmpty()) {
                    chatHistory.remove(chatHistory.size() - 1);
                }

                isInitialized = true;
                LOGGER.info("Ollama Client initialized successfully");
            } catch (OllamaBaseException | InterruptedException | IOException e) {
                LOGGER.error("Failed to initialize Ollama Client: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    public static void callClient(CommandContext<ServerCommandSource> context, String playerMessage) {
        LOGGER.info("callClient invoked with playerMessage: {}", playerMessage);

        if (pingOllamaServer()) {
            LOGGER.info("Ollama Server is reachable");

            String botName = StringArgumentType.getString(context, "botName");
            LOGGER.info("Bot name: {}", botName);

            MinecraftServer server = context.getSource().getServer();
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            if (bot == null) {
                LOGGER.error("Bot with name {} not found", botName);
                return;
            }

            ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();

            // Initialize Ollama client if not already initialized
            initializeOllamaClient(botName);

            OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
            LOGGER.info("Ollama API initialized");

            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.TINYLLAMA);

            OllamaChatRequestModel requestModel;
            if (chatHistory == null) {
                requestModel = builder.withMessage(OllamaChatMessageRole.USER, playerMessage).build();
            } else {
                requestModel = builder.withMessages(chatHistory).withMessage(OllamaChatMessageRole.USER, playerMessage).build();
            }

            try {
                chatResult = ollamaAPI.chat(requestModel);
                chatHistory = chatResult.getChatHistory();
                LOGGER.info("Chat result received: {}", chatResult.getResponse());
                sendBotMessageInChunks(server, botSource, chatResult.getResponse());
            } catch (OllamaBaseException | InterruptedException | IOException e) {
                LOGGER.error("Exception occurred: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.info("Ollama Server is unreachable!");
        }
    }

    private static void sendBotMessageInChunks(MinecraftServer server, ServerCommandSource botSource, String message) {
        int maxLength = 100; // Adjust based on desired message length
        String[] words = message.split(" ");
        StringBuilder chunk = new StringBuilder();

        for (String word : words) {
            if (chunk.length() + word.length() + 1 > maxLength) {
                server.getCommandManager().executeWithPrefix(botSource, "/say " + chunk.toString().trim());
                chunk.setLength(0);
            }
            chunk.append(word).append(" ");
        }
        if (!chunk.isEmpty()) {
            server.getCommandManager().executeWithPrefix(botSource, "/say " + chunk.toString().trim());
        }
    }


}


