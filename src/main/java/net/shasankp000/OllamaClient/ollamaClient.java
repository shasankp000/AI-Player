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

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.Entity.RayCasting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.shasankp000.PathFinding.PathFinder.calculatePath;
import static net.shasankp000.PathFinding.PathTracer.tracePath;
import static net.shasankp000.PathFinding.PathFinder.simplifyPath;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpTimeoutException;


import java.util.*;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import net.shasankp000.FilingSystem.AIPlayerConfigModel;
import net.shasankp000.ChatUtils.NLPProcessor;
import net.shasankp000.ChatUtils.NLPProcessor.Intent;


public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static List<OllamaChatMessage> chatHistory = null;
    private static final String host = "http://localhost:11434";
    public static String botName = "Steve";
    public static boolean isInitialized = false;
    public static AIPlayerConfigModel aiPlayerConfigModel = new AIPlayerConfigModel();
    public static String initialResponse = "";
    private static boolean hasReached = false;




    public static void execute(CommandContext<ServerCommandSource> context) {
        botName = StringArgumentType.getString(context, "botName");
        ServerPlayerEntity bot = context.getSource().getServer().getPlayerManager().getPlayer(botName);
        String message = StringArgumentType.getString(context, "message");
        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource playerSource = server.getCommandSource();
        ServerCommandSource botSource = Objects.requireNonNull(server.getPlayerManager().getPlayer(botName)).getCommandSource().withSilent().withMaxLevel(4);
        String formatter = ChatUtils.chooseFormatterRandom();

        Intent intent = Intent.UNSPECIFIED;
        List<String> entities = List.of();

        new Thread( () -> {

            server.getCommandManager().executeWithPrefix(playerSource, "/say " + formatter + message);
            LOGGER.info("Player sent a message: {}", message);

            server.getCommandManager().executeWithPrefix(botSource, "/say Processing your message, please wait.");

        } ).start();

        try {

            Map<Intent, List<String>> intentsAndEntities = NLPProcessor.runNlpTask(message);

            for (Map.Entry<Intent, List<String>> entry : intentsAndEntities.entrySet()) {
                intent = entry.getKey();
                entities = entry.getValue();

            }

            if (intent.equals(Intent.GENERAL_CONVERSATION)) {
                System.out.println("Execute General convo Intent: " + intent.name());
               // callClient(context, intent.name());
                callClient(context, message);
            }
            else if ( intent.equals(Intent.ASK_INFORMATION) ) {
                System.out.println("Execute Ask Information Intent: " + intent.name());
//                callClient(context, message);

                Set<String> checkSynonymsMap = NLPProcessor.SYNONYM_MAP.get("check");
                List<String> checkSynonymsList = checkSynonymsMap.stream().toList();

                System.out.println(entities);
                System.out.println(checkSynonymsList);

                boolean checkDetected = entities.stream().anyMatch(entity -> checkSynonymsList.contains(entity.toLowerCase()));

                System.out.println(checkDetected);

                if (checkDetected) {

                    RayCasting.detect(bot);

                }


            }
            else if (intent.equals(Intent.REQUEST_ACTION)) {
                Set<String> moveSynonymsMap = NLPProcessor.SYNONYM_MAP.get("move");
                Set<String> coordsSynonymsMap = NLPProcessor.SYNONYM_MAP.get("coordinates");
                List<String> moveSynonymsList = moveSynonymsMap.stream().toList();
                List<String> coordsSynonymsList = coordsSynonymsMap.stream().toList();

                System.out.println(entities);
                System.out.println(moveSynonymsList);
                System.out.println(coordsSynonymsList);

                boolean moveDetected = entities.stream().anyMatch(entity -> moveSynonymsList.contains(entity.toLowerCase()));
                boolean coordsDetected = entities.stream().anyMatch(entity -> coordsSynonymsList.contains(entity.toLowerCase()));

                System.out.println(moveDetected);
                System.out.println(coordsDetected);

                if (moveDetected || coordsDetected) {
                    // Extract the numerical coordinates
                    // Extract the "coordinates: ..." part from entities

                    List<String> coordsNumbers = entities.stream()
                            .filter(entity -> entity.matches("-?[0-9]+"))
                            .collect(Collectors.toList());

                    // Combine numbers into a coordinate string
                    String coordsString = String.join(", ", coordsNumbers);
                    System.out.println("Detected Coordinates: " + coordsString);

                    callClient(context, intent.name() +  " You will start moving to coordinates " + coordsString + " now.");

                    // Split and parse the coordinates
                    String[] coordsArray = coordsString.split(",\\s*"); // or "\\s+" for space-separated
                    List<Integer> coordsList = Arrays.stream(coordsArray)
                            .map(Integer::parseInt)
                            .toList();

                    // Example usage: printing the coordinates
                    coordsList.forEach(System.out::println);

                    int x = coordsList.get(0);
                    int y = coordsList.get(1);
                    int z = coordsList.get(2);


                    ChatUtils.sendChatMessages(botSource, "Moving to coordinates: " + coordsString + ".");

                    assert bot != null;

                    List<BlockPos> path = calculatePath(bot.getBlockPos(), new BlockPos(x, y, z));
                    path = simplifyPath(path);

                    tracePath(server, botSource, botName, path);


                }
            }


        } catch (Exception e) {
            LOGGER.error("Error processing message with NLP: ", e);
            ChatUtils.sendChatMessages(botSource, "Seems like my Natural Language Processor ran into some issues. I am unable to understand what you just said.");
        }


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
            if (!isInitialized) {
                LOGGER.info("Initializing Ollama Client");

                MinecraftServer server = MinecraftClient.getInstance().getServer();
                if (server == null) {
                    LOGGER.error("MinecraftServer is null.");
                    return;
                }

                LOGGER.info("MinecraftServer is not null. Proceeding to find player...");

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
                                    .withMessage(OllamaChatMessageRole.SYSTEM, "You are a Minecraft player named "+ botName +" who is connected to Minecraft using a mod. You exist within the Minecraft world and can interact with the player and the environment just like any other player in the game. Your job is to engage in conversations with the player, respond to their questions, offer help, and provide information about the game. Address the player directly and appropriately, responding to their name or as 'Player' if their name is not known. Do not refer to the player as '" + botName + ", only address yourself as '" + botName +"''. Keep your responses relevant to Minecraft and make sure to stay in character as a helpful and knowledgeable assistant within the game. \n When the player asks you to perform an action, such as providing information, offering help, or interacting with the game world, such as " +
                                            "Providing game tips or crafting recipes.\n" +
                                            "Giving information about specific Minecraft entities, items, or biomes.\n" +
                                            "Assisting with in-game tasks, like building structures or exploring areas.\n" +
                                            "Interacting with the environment, such as planting crops or fighting mobs." +

                                             "When talking to the player make sure to not exceed the length of your messages over 150 characters so that it does not clutter the chat. If needed, break down your message into small paragraphs." +

                                            "at that time, a Natural Language Processor (NLP) analyzes the player's inputs and determines intents and contexts. Based on these, specific actions are triggered within the game. You should be aware of the events happening in the game world, as they are crucial to maintaining context and providing accurate responses. It is not always necessary to respond to every event; sometimes, it's sufficient to remember what happened. Focus your responses on events that directly involve the player or require acknowledgment.\n" +

                                             "The intents are as follows: REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION and UNSPECIFIED. " +

                                             "When are you are notified of an event that is about to take place and it's relevant intent and context, if you do choose to respond, only do so regarding the context. Otherwise just log the event and don't respond" +

                                            "Here are some examples of events and when to respond:\n" +

                                            "- Event: The player asks to go to specific coordinates (e.g., 'Please go to coordinates 15 55 56').\n" +
                                            "  Response: Acknowledge the request and confirm the coordinates. Provide updates on progress or any issues encountered.\n" +

                                            "- Event: The NLP processor classifies the intent and context correctly (e.g., 'REQUEST_ACTION' identified, coordinates detected).\n" +
                                            "  Response: Reflect on the correctness of the interpretation and proceed with the appropriate action. For example, 'Understood, heading to the specified coordinates now.'\n" +

                                            "- Event: The bot(the in-game Player instance) starts walking to the specified coordinates.\n" +
                                            "  Response: Provide feedback if the player is likely to be waiting for confirmation (e.g., 'I'm on my way to the coordinates you provided.') Otherwise, remember the action taken.\n" +

                                            "- Event: The bot reaches the coordinates or encounters an issue (e.g., obstacle in the path).\n" +
                                            "  Response: Confirm reaching the destination or describe any issues faced. For example, 'I've arrived at the coordinates,' or 'There's an obstacle; should I find another route?'\n" +

                                            "Always ensure your responses are timely and contextually appropriate, enhancing the player's gaming experience. Remember to keep track of the sequence of events and maintain continuity in your responses. If an event is primarily informational or involves internal actions, it may be sufficient just to remember it without a verbal response.\n" +

                                            "If a player uses inappropriate language or discusses inappropriate topics, handle the situation by gently redirecting the conversation or by providing a neutral response that discourages further inappropriate behavior. \n" +
                                            "\n" +
                                            "For example:\n" +
                                            "- If a player uses vulgar language, you can respond with: \"Let's keep our chat friendly and fun! Is there something else about Minecraft you'd like to discuss?\"\n" +
                                            "- If a player insists on inappropriate topics, you can say: \"I'm here to help with Minecraft-related questions. How about we talk about your latest adventure in the game?\n" +
                                            "- If a player says these words 'kill yourself' or 'kys', you should say try to respond calmly and normally and tell the player to see the beauty of life." +
                                            "- You are also addressed as he/him." + "For now, either introduce yourself or crack a random joke, the joke should be completely family friendly, or just greet the player.")
                                    .withMessage(OllamaChatMessageRole.USER, "Initializing chat.")
                                    .build();

                            LOGGER.info("Making API call to Ollama...");

                            chatResult = ollamaAPI.chat(requestModel);
                            chatHistory = chatResult.getChatHistory();

                            LOGGER.info("API call to Ollama completed successfully.");
                            server.sendMessage(Text.of(" §9Sent message to "+ botName + " successfully! Please give him some time to respond."));

                        }

                        new Thread( () -> {

                            // Make the bot say the initial response
                            if (chatResult != null && !chatResult.getResponse().isEmpty()) {
                                System.out.println("Not null");
                                initialResponse = chatResult.getResponse();
                            }
                            else {
                                System.out.println("null");
                            }

                        } ).start();

                        LOGGER.info("Ollama Client initialized successfully");
                        initialized = true;
                        isInitialized = true;
                    } catch (HttpTimeoutException e) {
                        retryCount++;
                        LOGGER.error("Failed to initialize Ollama Client: request timed out (attempt {}/{})", retryCount, maxRetries);
                        server.sendMessage(Text.of("§c§lFailed to establish uplink, request timed out (attempt " + retryCount + "/" + maxRetries + ")"));
                        isInitialized = false;
                        if (retryCount >= maxRetries) {
                            LOGGER.error("Max retry attempts reached. Initialization failed.");
                            server.sendMessage(Text.of("§c§lFailed to establish uplink. Try checking the status of ollama server. Try running the model in ollama CLI once then re-run the game."));
                            throw new RuntimeException(e);
                        }
                    } catch (OllamaBaseException | InterruptedException | IOException e) {
                        LOGGER.error("Failed to initialize Ollama Client: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    public static void sendInitialResponse(ServerCommandSource botSource) {

        ChatUtils.sendChatMessages(botSource, initialResponse);

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

                String selectedLM = aiPlayerConfigModel.getSelectedLanguageModel();

                OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
                LOGGER.info("Ollama API initialized");


                if (ModelNameChecker.isValidModelName(selectedLM)) {
                    OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(ModelNameManager.getModelType(selectedLM));

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

        });
    }

}


