package net.shasankp000.OllamaClient;

// This is still a work in progress.

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import net.shasankp000.ChatUtils.ChatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpTimeoutException;


import java.util.List;

import java.util.concurrent.CompletableFuture;
import net.shasankp000.FilingSystem.AIPlayerConfigModel;

public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static List<OllamaChatMessage> chatHistory = null;
    private static final String host = "http://localhost:11434";
    public static String botName = "Steve";
    public static boolean isInitialized;
    public static AIPlayerConfigModel aiPlayerConfigModel = new AIPlayerConfigModel();


    public static void execute(CommandContext<ServerCommandSource> context) {
        botName = StringArgumentType.getString(context, "botName");
        String message = StringArgumentType.getString(context, "message");
        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource playerSource = server.getCommandSource();
        String formatter = ChatUtils.chooseFormatterRandom();

        server.getCommandManager().executeWithPrefix(playerSource, "/say "+ formatter + message);
        LOGGER.info("Player sent a message: {}", message);


        callClient(context, message);

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
        assert bot != null;
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

    public static CompletableFuture<Void> initializeOllamaClient() {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info("Initializing Ollama Client");

            MinecraftServer server = MinecraftClient.getInstance().getServer();
            if (server == null) {
                LOGGER.error("MinecraftServer is null.");
                return;
            }

            LOGGER.info("MinecraftServer is not null. Proceeding to find player...");

            // Wait until any player is available
            ServerPlayerEntity player = null;
            int maxWaitTime = 10000; // Max wait time of 10 seconds
            int waitInterval = 100; // Check every 100 milliseconds
            int waitedTime = 0;

            while (player == null && waitedTime < maxWaitTime) {
                player = findAnyPlayer(server);
                if (player == null) {
                    try {
                        LOGGER.info("Player not found yet, waiting...");
                        Thread.sleep(waitInterval);
                    } catch (InterruptedException e) {
                        LOGGER.error("Waiting for player interrupted: {}", e.getMessage());
                        return;
                    }
                    waitedTime += waitInterval;
                }
            }

            if (player == null) {
                LOGGER.error("No player found after waiting for {} seconds.", maxWaitTime / 1000);
                return;
            }

            LOGGER.info("Player found: {}", player.getName().getString());

            int maxRetries = 3;
            int retryCount = 0;
            boolean initialized = false;

            LOGGER.info("Connecting to ollama server....");

            String host = "http://localhost:11434/";
            OllamaAPI ollamaAPI = new OllamaAPI(host);
            ollamaAPI.setVerbose(true);
            ollamaAPI.setRequestTimeoutSeconds(90); // Set timeout to 90 seconds

            while (!initialized && retryCount < maxRetries) {
                try {
                    String selectedLM = aiPlayerConfigModel.getSelectedLanguageModel();
                    LOGGER.info("Setting language model to {}", selectedLM);

                    if (ModelNameChecker.isValidModelName(selectedLM)) {
                        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(ModelNameManager.getModelType(selectedLM));

                        OllamaChatRequestModel requestModel = builder
                                .withMessage(OllamaChatMessageRole.SYSTEM, "You are an AI assistant named "+ botName +" who is connected to Minecraft using a mod. You exist within the Minecraft world and can interact with the player and the environment just like any other character in the game. Your job is to engage in conversations with the player, respond to their questions, offer help, and provide information about the game. Address the player directly and appropriately, responding to their name or as 'Player' if their name is not known. Do not refer to yourself or the player as '" + botName + "'. Keep your responses relevant to Minecraft and make sure to stay in character as a helpful and knowledgeable assistant within the game. \n When the player asks you to perform an action, such as providing information, offering help, or interacting with the game world, you should recognize these requests and trigger the appropriate function calls." + "Here are some examples of actions you might be asked to perform:\n" +
                                        "\n" +
                                        "Providing game tips or crafting recipes.\n" +
                                        "Giving information about specific Minecraft entities, items, or biomes.\n" +
                                        "Assisting with in-game tasks, like building structures or exploring areas.\n" +
                                        "Interacting with the environment, such as planting crops or fighting mobs." + "\n Always ensure your responses are timely and contextually appropriate, enhancing the player's gaming experience. " +
                                        "If a player uses inappropriate language or discusses inappropriate topics, handle the situation by gently redirecting the conversation or by providing a neutral response that discourages further inappropriate behavior. \n" +
                                        "\n" +
                                        "For example:\n" +
                                        "- If a player uses vulgar language, you can respond with: \"Let's keep our chat friendly and fun! Is there something else about Minecraft you'd like to discuss?\"\n" +
                                        "- If a player insists on inappropriate topics, you can say: \"I'm here to help with Minecraft-related questions. How about we talk about your latest adventure in the game?\n" +
                                        "- If a player says these words 'kill yourself' or 'kys', you should say try to respond calmly and normally and tell the player to see the beauty of life." +
                                        "- You are also addressed as he/him.")
                                .withMessage(OllamaChatMessageRole.USER, "Initializing chat.")
                                .build();

                        LOGGER.info("Making API call to Ollama...");

                        chatResult = ollamaAPI.chat(requestModel);
                        chatHistory = chatResult.getChatHistory();

                        LOGGER.info("API call to Ollama completed successfully.");
                        ServerCommandSource playerSource = player.getCommandSource();
                        server.getCommandManager().executeWithPrefix(playerSource, "/say §9Sent message to "+ botName + " successfully! Please give him some time to respond.");

                    }

                    LOGGER.info("Ollama Client initialized successfully");
                    initialized = true;
                    isInitialized = true;
                } catch (HttpTimeoutException e) {
                    retryCount++;
                    LOGGER.error("Failed to initialize Ollama Client: request timed out (attempt {}/{})", retryCount, maxRetries);
                    ServerCommandSource playerSource = player.getCommandSource();
                    server.getCommandManager().executeWithPrefix(playerSource, "/say §c§lFailed to establish uplink, request timed out (attempt " + retryCount + "/" + maxRetries + ")");
                    isInitialized = false;
                    if (retryCount >= maxRetries) {
                        LOGGER.error("Max retry attempts reached. Initialization failed.");
                        server.getCommandManager().executeWithPrefix(playerSource, "/say §c§lFailed to establish uplink. Try checking the status of ollama server. Try running the model in ollama CLI once then re-run the game.");
                        throw new RuntimeException(e);
                    }
                } catch (OllamaBaseException | InterruptedException | IOException e) {
                    LOGGER.error("Failed to initialize Ollama Client: {}", e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        });
    }

    // Helper function to find any player on the server
    private static ServerPlayerEntity findAnyPlayer(MinecraftServer server) {
        LOGGER.info("Finding any player...");
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null) {
                LOGGER.info("Player found: {}", player.getName().getString());
                return player;
            }
        }
        LOGGER.info("No players found.");
        return null;
    }




    public static void callClient(CommandContext<ServerCommandSource> context, String playerMessage) {
        LOGGER.info("callClient invoked with playerMessage: {}", playerMessage);

        initializeOllamaClient().thenRun(() -> {

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
                    ChatUtils.sendChatMessages(botSource, chatResult.getResponse());
                } catch (OllamaBaseException | InterruptedException | IOException e) {
                    LOGGER.error("Exception occurred: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } else {
                LOGGER.info("Ollama Server is unreachable!");
            }

        });
    }




}


