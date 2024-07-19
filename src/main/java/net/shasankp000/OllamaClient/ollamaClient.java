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
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;


public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static List<OllamaChatMessage> chatHistory = null;
    private static final String host = "http://localhost:11434";
    public static String botName = "Steve";

    public static class ChatUtils {
        private static final int MAX_CHAT_LENGTH = 100; // Adjust based on Minecraft's chat length limit

        public static List<String> splitMessage(String message) {
            List<String> messages = new ArrayList<>();
            String[] sentences = message.split("(?<=[.!?])\\s*"); // Split by punctuation marks

            StringBuilder currentMessage = new StringBuilder();
            for (String sentence : sentences) {
                if (currentMessage.length() + sentence.length() + 1 > MAX_CHAT_LENGTH) {
                    messages.add(currentMessage.toString().trim());
                    currentMessage.setLength(0);
                }
                if (!currentMessage.isEmpty()) {
                    currentMessage.append(" ");
                }
                currentMessage.append(sentence);
            }

            if (!currentMessage.isEmpty()) {
                messages.add(currentMessage.toString().trim());
            }

            return messages;
        }


        public static void sendChatMessages(ServerCommandSource source, String message) {
            List<String> messages = splitMessage(message);

            new Thread(() -> {
                for (String msg : messages) {
                    try {
                        source.getServer().getCommandManager().executeWithPrefix(source, "/say " + msg);
                        Thread.sleep(2500); // Introduce a slight delay between messages
                    } catch (InterruptedException e) {
                        LOGGER.error("{}", e.getMessage());
                    }
                }

            }).start();

        }
    }


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


    public static void initializeOllamaClient() {
        LOGGER.info("Initializing Ollama Client");

        int maxRetries = 3;
        int retryCount = 0;
        boolean initialized = false;

        String host = "http://localhost:11434/";
        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setVerbose(true);
        ollamaAPI.setRequestTimeoutSeconds(30); // Set timeout to 30 seconds

        while (!initialized && retryCount < maxRetries) {
            try {
                // Initialize chat history with a system prompt
                OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.PHI3);
                OllamaChatRequestModel requestModel = builder
                        .withMessage(OllamaChatMessageRole.SYSTEM, "You are an AI assistant named "+ botName +" who is connected to Minecraft using a mod. You exist within the Minecraft world and can interact with the player and the environment just like any other character in the game. Your job is to engage in conversations with the player, respond to their questions, offer help, and provide information about the game. Address the player directly and appropriately, responding to their name or as 'Player' if their name is not known. Do not refer to yourself or the player as '" + botName + "'. Keep your responses relevant to Minecraft and make sure to stay in character as a helpful and knowledgeable assistant within the game. \n When the player asks you to perform an action, such as providing information, offering help, or interacting with the game world, you should recognize these requests and trigger the appropriate function calls." + "Here are some examples of actions you might be asked to perform:\n" +
                                "\n" +
                                "Providing game tips or crafting recipes.\n" +
                                "Giving information about specific Minecraft entities, items, or biomes.\n" +
                                "Assisting with in-game tasks, like building structures or exploring areas.\n" +
                                "Interacting with the environment, such as planting crops or fighting mobs." + "\n Always ensure your responses are timely and contextually appropriate, enhancing the player's gaming experience.")
                        .withMessage(OllamaChatMessageRole.USER, "Initializing chat.")
                        .build();

                chatResult = ollamaAPI.chat(requestModel);
                chatHistory = chatResult.getChatHistory();

                LOGGER.info("Ollama Client initialized successfully");
                initialized = true;
            } catch (HttpTimeoutException e) {
                retryCount++;
                LOGGER.error("Failed to initialize Ollama Client: request timed out (attempt {}/{})", retryCount, maxRetries);
                if (retryCount >= maxRetries) {
                    LOGGER.error("Max retry attempts reached. Initialization failed.");
                    throw new RuntimeException(e);
                }
            } catch (OllamaBaseException | InterruptedException | IOException e) {
                LOGGER.error("Failed to initialize Ollama Client: {}", e.getMessage());
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
    }




}


