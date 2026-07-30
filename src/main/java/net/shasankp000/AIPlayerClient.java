package net.shasankp000;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.shasankp000.ChatUtils.ChatContextManager;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.ChatUtils.ClarificationState;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.FilingSystem.ManualConfig;
import net.shasankp000.LauncherDetection.LauncherEnvironment;
import net.shasankp000.Network.OpenConfigPayload;
import net.shasankp000.OllamaClient.ollamaClient;
import net.shasankp000.ServiceLLMClients.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class AIPlayerClient implements ClientModInitializer {
    private static final Gson GSON = new Gson();
    private static final Path BOT_PROFILE_PATH = Paths.get(LauncherEnvironment.getStorageDirectory("config") + File.separator + "settings.json5");
    private static JsonObject botProfiles;
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player-client");
    public static final ManualConfig CONFIG = ManualConfig.load();


    static {
        try {
            botProfiles = GSON.fromJson(Files.newBufferedReader(BOT_PROFILE_PATH), JsonObject.class);
        } catch (IOException e) {
            System.out.println("Bot profiles not found yet — continuing with no bots registered.");
            botProfiles = null;
        }
    }


    private static String getBotNameIfMentioned(String message) {
        String[] words = message.split("\\s+");
        JsonObject profiles = botProfiles.getAsJsonObject("botGameProfile");
        for (String word : words) {
            String cleaned = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            for (String botName : profiles.keySet()) {
                if (botName.equalsIgnoreCase(cleaned)) {
                    return botName;
                }
            }
        }
        return null;
    }



    private static boolean isMessageFromBot(String rawMessage) {
        if (botProfiles == null) return false;

        JsonObject profiles = botProfiles.getAsJsonObject("botGameProfile");
        if (profiles == null) return false;

        for (String botName : profiles.keySet()) {
            if (rawMessage.startsWith("[" + botName + "]")) {
                return true;
            }
        }
        return false;
    }


    private static boolean isMessageFromServerContainsBotName(String rawMessage) {
        if (botProfiles == null) return false;

        if (rawMessage.startsWith("[Server]")) {
            JsonObject profiles = botProfiles.getAsJsonObject("botGameProfile");
            if (profiles == null) return false;

            for (String botName : profiles.keySet()) {
                if (rawMessage.contains(botName)) {
                    return true;
                }
            }
        }
        return false;
    }




    @Override
    public void onInitializeClient() {


        LOGGER.debug("Running on environment type: {}", FabricLoader.getInstance().getEnvironmentType());

        String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

        LOGGER.debug("Using provider: {}", llmProvider);


        ClientPlayNetworking.registerGlobalReceiver(OpenConfigPayload.ID, (client, handler) -> {
            net.minecraft.client.gui.screen.Screen currentScreen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                    new net.shasankp000.GraphicalUserInterface.ConfigManager(net.minecraft.text.Text.empty(), currentScreen)
            );
        });


        // Register real-time chat scanner with loop protection
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            String rawMessage = message.getString();
            UUID playerUUID = sender != null ? sender.getId() : null;



            System.out.println("Raw message: " + rawMessage);

            // Loop protection layer 1: Ignore commands
            if (rawMessage.startsWith("/")) {
                System.out.println("Command detected, skipping....");
                return;
            }

            // Loop protection layer 2: Ignore if message has bot prefix
            if (isMessageFromBot(rawMessage)) {
                System.out.println("Bot's own message detected by prefix, skipping...");
                return;
            }

            // Loop protection layer 3: Ignore server system messages that mention a bot name
            if (isMessageFromServerContainsBotName(rawMessage)) {
                System.out.println("Server system message mentioning bot detected, skipping...");
                return;
            }


            if (playerUUID != null && ChatContextManager.isAwaitingClarification(playerUUID)) {
                ClarificationState clarification = ChatContextManager.getPendingClarification(playerUUID);

                String combinedContext = """
                    Original: %s
                    Clarification asked: %s
                    Player answer: %s
                  """.formatted(
                        clarification.originalMessage,
                        clarification.clarifyingQuestion,
                        rawMessage
                );

                String botName = clarification.botName;
                if (botName == null || botName.isEmpty()) {
                    botName = getBotNameIfMentioned(clarification.clarifyingQuestion);
                }
                if (botName == null || botName.isEmpty()) {
                    botName = getBotNameIfMentioned(rawMessage);
                }
                System.out.println("[Debug] Using botName: " + botName);

                if (botName == null) {
                    System.out.println("No bot name resolved for clarification! Skipping NLP call to prevent crash.");
                } else {
                    final UUID finalPlayerUUID = playerUUID;
                    final String finalBotName  = botName;

                    switch (llmProvider) {
                        case "openai", "gpt", "google", "gemini", "anthropic", "claude", "xAI", "xai", "grok", "custom": {
                            LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                            if (llmClient != null) {
                                LLMServiceHandler.runFromChat(combinedContext, finalBotName, finalPlayerUUID, llmClient);
                            } else {
                                LOGGER.error("LLM client is null for provider: {}", llmProvider);
                                client.getToastManager().add(
                                        SystemToast.create(client, SystemToast.Type.CHUNK_LOAD_FAILURE,
                                                Text.of("LLM Client factory error."),
                                                Text.of("Error! Returned client is null! Cannot proceed!"))
                                );
                            }
                            break;
                        }
                        case "player2": {
                            LLMClient llmClient = LLMClientFactory.createClient(
                                    "player2",
                                    finalPlayerUUID,
                                    msg -> ChatUtils.sendSystemMessage(null, msg)
                            );
                            if (llmClient != null) {
                                LLMServiceHandler.runFromChat(combinedContext, finalBotName, finalPlayerUUID, llmClient);
                            } else {
                                LOGGER.error("Player2 client creation failed.");
                            }
                            break;
                        }
                        case "ollama":
                            ollamaClient.runFromChat(finalBotName, combinedContext, finalPlayerUUID);
                            break;
                        default:
                            LOGGER.warn("Unsupported provider detected. Defaulting to Ollama client");
                            client.getToastManager().add(
                                    SystemToast.create(client, SystemToast.Type.NARRATOR_TOGGLE,
                                            Text.of("Invalid LLM Client."),
                                            Text.of("Unsupported provider detected. Defaulting to Ollama client"))
                            );
                            ollamaClient.runFromChat(finalBotName, combinedContext, finalPlayerUUID);
                            break;
                    }
                }
                ChatContextManager.clearPendingClarification(playerUUID);
            }



        });

        // Listen to player send messages event
        ClientSendMessageEvents.CHAT.register((message) -> {
            MinecraftClient client = MinecraftClient.getInstance();

            System.out.println("Outgoing message: " + message);

            UUID playerUUID = net.minecraft.client.MinecraftClient.getInstance().player.getUuid();

            if (message.startsWith("/")) {
                System.out.println("Outgoing command detected, skipping NLP...");
                return;
            }

            if (ChatContextManager.isAwaitingClarification(playerUUID)) {
                System.out.println("Awaiting clarification — skipping outgoing NLP trigger.");
                return;
            }

            if (isMessageFromServerContainsBotName(message)) {
                System.out.println("Outgoing message spoofing server with bot name detected — skipping NLP...");
                return;
            }

            if (isMessageFromBot(message)) {
                System.out.println("Bot's own message detected by prefix, skipping...");
                return;
            }

            String botName = getBotNameIfMentioned(message);
            System.out.println("Mentioned bot name: " + botName);
            if (botName != null) {
                final UUID finalPlayerUUID = playerUUID;
                final String finalBotName  = botName;

                switch (llmProvider) {
                    case "openai", "gpt", "google", "gemini", "anthropic", "claude", "xAI", "xai", "grok": {
                        LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                        if (llmClient != null) {
                            LLMServiceHandler.runFromChat(message, finalBotName, finalPlayerUUID, llmClient);
                        } else {
                            LOGGER.error("Error! Returned client is null! Cannot proceed!");
                            client.getToastManager().add(
                                    SystemToast.create(client, SystemToast.Type.CHUNK_LOAD_FAILURE,
                                            Text.of("LLM Client factory error."),
                                            Text.of("Error! Returned client is null! Cannot proceed!"))
                            );
                        }
                        break;
                    }
                    case "player2": {
                        LLMClient llmClient = LLMClientFactory.createClient(
                                "player2",
                                finalPlayerUUID,
                                msg -> ChatUtils.sendSystemMessage(null, msg)
                        );
                        if (llmClient != null) {
                            LLMServiceHandler.runFromChat(message, finalBotName, finalPlayerUUID, llmClient);
                        } else {
                            LOGGER.error("Player2 client creation failed.");
                        }
                        break;
                    }
                    case "ollama":
                        ollamaClient.runFromChat(finalBotName, message, finalPlayerUUID);
                        break;
                    default:
                        LOGGER.warn("Unsupported provider detected. Defaulting to Ollama client");
                        client.getToastManager().add(
                                SystemToast.create(client, SystemToast.Type.NARRATOR_TOGGLE,
                                        Text.of("Invalid LLM Client."),
                                        Text.of("Unsupported provider detected. Defaulting to Ollama client"))
                        );
                        ollamaClient.runFromChat(finalBotName, message, finalPlayerUUID);
                        break;
                }
            }
        });




    }
}
