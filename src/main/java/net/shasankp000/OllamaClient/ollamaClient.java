package net.shasankp000.OllamaClient;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.models.chat.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shasankp000.AIPlayer;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.ChatUtils.Helper.RAG2;
import net.shasankp000.ChatUtils.NLPProcessor;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.Exception.intentMisclassification;
import net.shasankp000.FunctionCaller.FunctionCallerV2;
import net.shasankp000.GameAI.autonomous.AutonomousManager;
import net.shasankp000.Overlay.ThinkingStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final String host = "http://localhost:11434";
    public static String botName = "";
    public static boolean isInitialized = false;
    public static String initialResponse = "";
    public static final OllamaAPI ollamaAPI = new OllamaAPI(host);
    private static final Pattern THINK_BLOCK = Pattern.compile("<think>([\\s\\S]*?)</think>");
    private static final ExecutorService BOT_TASK_POOL = Executors.newCachedThreadPool();

    // ── LLMContextBridge API ──────────────────────────────────────────────────

    /**
     * Cheap availability guard used by {@link net.shasankp000.Personality.LLMContextBridge}.
     *
     * <p>Returns {@code true} when the client has been successfully initialised
     * ({@link #isInitialized} is set to {@code true} at the end of
     * {@link #initializeOllamaClient()}).  This avoids an extra network round-trip
     * on every LLM call; the startup ping in {@link #pingOllamaServer()} already
     * confirms reachability.
     *
     * <p>Falls back to a live {@code ollamaAPI.ping()} only when the client has
     * not yet been initialised (e.g. autonomous engine fires before the bot has
     * fully spawned), so callers never block indefinitely.
     *
     * @return {@code true} if Ollama is ready to accept requests.
     */
    public static boolean isAvailable() {
        if (isInitialized) return true;
        // Not yet initialised — do a quick live check rather than returning false
        // immediately, so early autonomous goals can still reach Ollama.
        try {
            return ollamaAPI.ping();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sends a single-turn prompt to Ollama and returns the text reply.
     *
     * <p>Uses the same model and {@link OllamaAPIHelper#smartChat} path as the
     * rest of the codebase so reasoning-model {@code <think>} blocks are stripped
     * automatically before the reply is returned.
     *
     * <p>This method is <strong>blocking</strong>; always call it from a
     * background thread (e.g. inside {@code CompletableFuture.supplyAsync}).
     *
     * @param prompt The full prompt text to send to the LLM.
     * @return The content portion of the LLM reply (think-blocks stripped).
     * @throws Exception if the Ollama API call fails.
     */
    public static String sendMessage(String prompt) throws Exception {
        String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();

        List<OllamaChatMessage> messages = new ArrayList<>();
        messages.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, generateSystemPrompt()));
        messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, prompt));

        OllamaThinkingResponse response = OllamaAPIHelper.smartChat(
                ollamaAPI,
                host,
                selectedLM,
                messages
        );

        // Return only the non-thinking portion so callers get clean text.
        return response.getContent();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static void runFromChat(String botName, String message, UUID playerUUID) {
        MinecraftServer server = AIPlayer.serverInstance;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);
        if (bot == null) {
            LOGGER.error("Bot {} not online.", botName);
            return;
        }
        ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

        // A human is now talking to the bot — pause autonomous loop
        AutonomousManager.getInstance().setPlayerControlled(botName, true);

        server.execute(() -> {
            try {
                routeIntent(message, botSource, playerUUID);
            } catch (Exception e) {
                LOGGER.error("Chat processing error: ", e);
                ChatUtils.sendChatMessages(botSource, "\u26a0\ufe0f I'm confused! Please report this.");
            } finally {
                // Resume autonomous loop after the interaction is handled
                AutonomousManager.getInstance().setPlayerControlled(botName, false);
            }
        });
    }

    public static void execute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        botName = EntityArgumentType.getPlayer(context, "bot").getName().getLiteralString();
        String message = StringArgumentType.getString(context, "message");

        MinecraftServer server = context.getSource().getServer();
        ServerCommandSource playerSource = context.getSource();
        ServerCommandSource botSource = Objects.requireNonNull(server.getPlayerManager().getPlayer(botName))
                .getCommandSource().withSilent().withMaxLevel(4);

        String formatter = ChatUtils.getRandomColorCode();

        server.execute(() -> {
            server.getCommandManager().executeWithPrefix(playerSource, "/say " + formatter + message);
            server.getCommandManager().executeWithPrefix(botSource, "/say Processing your message, please wait.");
        });

        // Pause autonomous loop while the command message is handled
        AutonomousManager.getInstance().setPlayerControlled(botName, true);

        server.execute(() -> {
            try {
                routeIntent(message, botSource, Objects.requireNonNull(playerSource.getPlayer()).getUuid());
            } catch (Exception e) {
                LOGGER.error("NLP error: ", e);
                ChatUtils.sendChatMessages(botSource, "\u26a0\ufe0f NLP issue. Report to developer.");
            } finally {
                AutonomousManager.getInstance().setPlayerControlled(botName, false);
            }
        });
    }

    private static void routeIntent(String message, ServerCommandSource botSource, UUID playerUUID) throws Exception {
        NLPProcessor.Intent intent = NLPProcessor.getIntention(message);

        LOGGER.info("\uD83D\uDCE8 Received intent: {}", intent);

        switch (intent) {
            case GENERAL_CONVERSATION, ASK_INFORMATION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("RAG2-Worker");
                    LOGGER.info("\uD83E\uDDF5 Started RAG2 worker thread");
                    RAG2.run(message, botSource, intent);
                    LOGGER.info("\u2705 Finished RAG2 worker thread");
                });
            }

            case REQUEST_ACTION -> {
                BOT_TASK_POOL.submit(() -> {
                    Thread.currentThread().setName("Function-Caller-Worker");
                    LOGGER.info("\uD83E\uDDF5 Started FunctionCallerV2 worker thread");
                    new FunctionCallerV2(botSource, playerUUID);
                    FunctionCallerV2.run(message);
                    LOGGER.info("\u2705 Finished FunctionCallerV2 worker thread");
                });
            }

            default -> {
                LOGGER.warn("\u26a0\ufe0f Intent unclear, retrying with LLM classification...");
                ChatUtils.sendChatMessages(botSource, "\uD83D\uDD0D Reanalyzing...");

                NLPProcessor.Intent retry = retryIntentLLM(message);

                LOGGER.info("\uD83D\uDCE8 Retry intent: {}", retry);

                if (retry == NLPProcessor.Intent.GENERAL_CONVERSATION || retry == NLPProcessor.Intent.ASK_INFORMATION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("RAG2-Retry-Worker");
                        LOGGER.info("\uD83E\uDDF5 Started RAG2 retry worker thread");
                        RAG2.run(message, botSource, retry);
                        LOGGER.info("\u2705 Finished RAG2 retry worker thread");
                    });
                } else if (retry == NLPProcessor.Intent.REQUEST_ACTION) {
                    BOT_TASK_POOL.submit(() -> {
                        Thread.currentThread().setName("Function-Caller-Retry-Worker");
                        LOGGER.info("\uD83E\uDDF5 Started FunctionCallerV2 retry worker thread");
                        new FunctionCallerV2(botSource, playerUUID);
                        FunctionCallerV2.run(message);
                        LOGGER.info("\u2705 Finished FunctionCallerV2 worker thread");
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

    /**
     * Pings Ollama server to check if it's reachable.
     * Returns false instead of crashing if server is not available.
     *
     * @return true if server is reachable, false otherwise
     */
    public static boolean pingOllamaServer() {
        try {
            boolean reachable = ollamaAPI.ping();
            if (reachable) {
                LOGGER.info("\u2713 Ollama server is alive and responding");
            } else {
                LOGGER.warn("\u26a0 Ollama server ping returned false");
            }
            return reachable;
        } catch (Exception e) {
            LOGGER.warn("\u26a0 Ollama server is not reachable: {}. AI chat features will be unavailable.", e.getMessage());
            LOGGER.info("Please ensure Ollama is installed and running on localhost:11434");
            return false;
        }
    }

    public static void initializeOllamaClient() {
        if (isInitialized) return;

        MinecraftServer server = AIPlayer.serverInstance;
        if (server == null) {
            LOGGER.error("Server instance is null.");
            return;
        }

        ollamaAPI.setRequestTimeoutSeconds(90);
        String selectedLM = AIPlayer.CONFIG.getSelectedLanguageModel();
        LOGGER.info("Connecting to Ollama using model: {}", selectedLM);

        CompletableFuture.runAsync(() -> {
            int retries = 0;
            boolean success = false;

            while (!success && retries < 3) {
                try {
                    // Build messages for the new API format
                    List<OllamaChatMessage> messages = new ArrayList<>();
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, generateSystemPrompt()));
                    messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, "Initializing chat."));

                    // Use smart chat that automatically detects reasoning models
                    OllamaThinkingResponse response = OllamaAPIHelper.smartChat(
                            ollamaAPI,
                            host,
                            selectedLM,
                            messages
                    );

                    initialResponse = response.getFullResponse();
                    LOGGER.info("Ollama Client initialized. Initial response: {}", response.getContent());

                    if (response.hasThinking()) {
                        LOGGER.info("\uD83D\uDCAD Model provided thinking: {} chars", response.getThinking().length());
                    }

                    server.execute(() ->
                            server.sendMessage(Text.of("\u00a79" + botName + " is ready!"))
                    );

                    isInitialized = true;
                    success = true;

                    // ── Start the autonomous goal engine for this bot ──────────────────
                    ServerPlayerEntity botPlayer = server.getPlayerManager().getPlayer(botName);
                    UUID botUUID = botPlayer != null ? botPlayer.getUuid() : UUID.randomUUID();
                    LOGGER.info("[autonomous] Handing off to AutonomousManager for bot '{}'", botName);
                    AutonomousManager.getInstance().startBot(botName, botUUID);
                    // ──────────────────────────────────────────────────────────────────

                } catch (HttpTimeoutException e) {
                    retries++;
                    LOGGER.error("Timeout initializing Ollama (attempt {}/3)", retries);
                } catch (Exception e) {
                    LOGGER.error("Failed initializing Ollama: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }

            if (!success) {
                LOGGER.error("Failed to initialize Ollama after 3 attempts.");
                server.sendMessage(Text.of("\u00a7c\u00a7lCould not establish uplink."));
            }
        });
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

    public static void sendInitialResponse(ServerCommandSource botSource) {
        MinecraftServer server = botSource.getServer();

        // \u2705 Schedule the WHOLE logic back to the main thread
        server.execute(() -> {
            processLLMOutput(initialResponse, botName, botSource);

            List<SQLiteDB.Memory> memories = SQLiteDB.fetchInitialResponse();
            if (memories.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        net.shasankp000.ServiceLLMClients.EmbeddingClient embeddingClient =
                                net.shasankp000.FilingSystem.EmbeddingClientFactory.createClient();
                        List<Double> embedding = embeddingClient.generateEmbedding(generateSystemPrompt());
                        SQLiteDB.storeMemory("conversation", generateSystemPrompt(), initialResponse, embedding);
                        LOGGER.info("\u2705 Saved initial response using {} embeddings.", embeddingClient.getProvider());
                    } catch (Exception e) {
                        LOGGER.error("\u274c Failed saving initial response: {}", e.getMessage(), e);
                    }
                });
            } else {
                LOGGER.info("\uD83D\uDDC3\uFE0F Initial response already in DB.");
            }
        });
    }

    public static void processLLMOutput(String fullResponse, String botName, ServerCommandSource botSource) {
        Matcher matcher = THINK_BLOCK.matcher(fullResponse);

        if (matcher.find()) {
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
                ChatUtils.sendChatMessages(botSource, botName + ": " + remainder);
            }
        } else {
            ChatUtils.sendChatMessages(botSource, botName + ": " + fullResponse);
        }
    }

}
