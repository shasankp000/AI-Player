package net.shasankp000.OllamaClient;

// This is still a work in progress.

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;

import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.ChatUtils.Helper.RAGImplementation;
import net.shasankp000.ChatUtils.NLPProcessor;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.FunctionCaller.FunctionCallerV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.net.http.HttpTimeoutException;

import java.sql.SQLException;
import java.util.*;

import net.shasankp000.AIPlayer;


import static net.shasankp000.ChatUtils.Helper.helperMethods.*;


public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static final String host = "http://localhost:11434";
    public static String botName = "";
    public static boolean isInitialized = false;
    public static String initialResponse = "";
    public static final OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
    private static final String gameDir = FabricLoader.getInstance().getGameDir().toString();
    private static final String DB_URL = "jdbc:sqlite:" + gameDir + "/sqlite_databases/memory_agent.db";


    public static void execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        botName = EntityArgumentType.getPlayer(context, "bot").getName().getLiteralString();

        System.out.println("Bot name set to: " + botName);

        String message = StringArgumentType.getString(context, "message");
        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource playerSource = context.getSource();
        ServerCommandSource botSource = Objects.requireNonNull(server.getPlayerManager().getPlayer(botName)).getCommandSource().withSilent().withMaxLevel(4);
        String formatter = ChatUtils.chooseFormatterRandom();


        new Thread( () -> {

            server.getCommandManager().executeWithPrefix(playerSource, "/say " + formatter + message);
            LOGGER.info("Player sent a message: {}", message);

            server.getCommandManager().executeWithPrefix(botSource, "/say Processing your message, please wait.");

        } ).start();

        String dateTime = getCurrentDateAndTime();

        try {

            NLPProcessor.Intent intent1;

            intent1 = NLPProcessor.getIntention(message);

            if (intent1.equals(NLPProcessor.Intent.GENERAL_CONVERSATION)) {

                System.out.println("Execute General convo Intent: " + intent1.name());
                RAGImplementation.runRagTask(dateTime, message, botSource);
            }

            else if (intent1.equals(NLPProcessor.Intent.ASK_INFORMATION) ) {

                    System.out.println("Execute ASK_INFORMATION Intent: " + intent1.name());

                    RAGImplementation.runRagTask(dateTime, message, botSource);

            }

            else if (intent1.equals(NLPProcessor.Intent.REQUEST_ACTION)) {
                    System.out.println("Execute REQUEST_ACTION Intent: " + intent1.name());


                    FunctionCallerV2 ignored = new FunctionCallerV2(botSource);
                    FunctionCallerV2.run(message);

            }

            else {

                ChatUtils.sendChatMessages(botSource, "I am unable to understand what you just said. Could you please clarify your message?");

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


    private static String generateSystemPrompt() {

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


    public static void initializeOllamaClient() {
            if (!isInitialized) {
                System.out.println("Initializing Ollama Client");


                // if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                //     // In an integrated server, you can get the server from the client.
                //     server = net.minecraft.client.MinecraftClient.getInstance().getServer();
                // } else {
                //     // On a dedicated server, use the stored server instance.
                //     server = AIPlayer.serverInstance.getNetworkIo().getServer();

                //     System.out.println("Set Dedicated server instance to " + server);
                // }

                // use a unified approach to get the server instance.

                MinecraftServer server = AIPlayer.serverInstance;


                if (server == null) {
                    LOGGER.error("MinecraftServer is null.");
                    System.out.println("Server Instance is null!");
                    return;
                }

                System.out.println("MinecraftServer is not null. Proceeding to find player...");

                int maxRetries = 3;
                int retryCount = 0;
                boolean initialized = false;

                System.out.println("Connecting to ollama server....");

                ollamaAPI.setRequestTimeoutSeconds(90); // Set timeout to 90 seconds

                while (!initialized && retryCount < maxRetries) {
                    try {
                        String selectedLM = AIPlayer.CONFIG.selectedLanguageModel();
                        LOGGER.info("Setting language model to {}", selectedLM);


                        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(selectedLM);

                        OllamaChatRequestModel requestModel = builder
                                .withMessage(OllamaChatMessageRole.SYSTEM, generateSystemPrompt())
                                .withMessage(OllamaChatMessageRole.USER, "Initializing chat.")
                                .build();

                        LOGGER.info("Making API call to Ollama...");

                        chatResult = ollamaAPI.chat(requestModel);

                        LOGGER.info("API call to Ollama completed successfully.");
                        server.sendMessage(Text.of(" §9Sent message to "+ botName + " successfully! Please give him some time to respond."));



                        new Thread( () -> {

                            // Make the bot say the initial response
                            if (chatResult != null) {
                                System.out.println("Not null");
                                initialResponse = chatResult.getResponse();

                                if (initialResponse.equals("")) {

                                    System.out.println("Initial response not initialized");

                                }
                                else {
                                    System.out.println("Initial response initialized");

                                }

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
    }


    public static void sendInitialResponse(ServerCommandSource botSource) {

        new Thread(() -> {

            ChatUtils.sendChatMessages(botSource, initialResponse);

        }).start();


        List<RAGImplementation.InitialResponse> initialResponseList = null;
            try {
                initialResponseList = RAGImplementation.fetchInitialResponse();
            } catch (SQLException e) {
                LOGGER.error("Caught exception while fetching initial response: {}", e.getMessage());
                throw new RuntimeException(e);
            }


            if (!initialResponseList.isEmpty()) {
                    System.out.println("Initial response detected.");
                }
            else {
                System.out.println("No initial response detected.");


                try {

                    List<Double> promptEmbeddingList = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, generateSystemPrompt());
                    List<Double> responseEmbeddingList = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, initialResponse);

                    SQLiteDB.storeConversationWithEmbedding(DB_URL, generateSystemPrompt(), initialResponse, promptEmbeddingList, responseEmbeddingList);
                    System.out.println("Saved initial response to database.");
                } catch (SQLException | IOException | InterruptedException | OllamaBaseException e) {
                    LOGGER.error("Caught exception while saving initial response: {}", e.getMessage());
                    throw new RuntimeException(e);
                }

            }

    }


}


