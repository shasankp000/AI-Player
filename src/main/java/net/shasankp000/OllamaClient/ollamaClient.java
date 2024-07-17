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
import net.shasankp000.Commands.spawnFakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;


public class ollamaClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static OllamaChatResult chatResult;
    private static List<OllamaChatMessage> chatHistory;
    private static final String host = "http://localhost:11434";


    public static void getPlayerMessage() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("tellBot")
                .then(CommandManager.argument("botName", StringArgumentType.string())
                .then(CommandManager.argument("message", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(ollamaClient::execute)))));

    }

    private static int execute(CommandContext<ServerCommandSource> context) {
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

    public static void callClient(CommandContext<ServerCommandSource> context, String playerMessage) {

        if (pingOllamaServer()) {

            LOGGER.info("Ollama Server is reachable");

            String botName = StringArgumentType.getString(context, "botName");

            MinecraftServer server = context.getSource().getServer();

            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);


            OllamaAPI ollamaAPI = new OllamaAPI(host);

            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.TINYLLAMA);

            // First message.

            OllamaChatRequestModel requestModel = builder.withMessage(OllamaChatMessageRole.SYSTEM, "You are an AI assistant who has been connected to minecraft using a mod. Your job is to talk, interact with the player and the world and behave as if you are a second player on his world. Your name is {}", botName)
                    .withMessage(OllamaChatMessageRole.USER, playerMessage)
                    .build();

            try {

                if (chatHistory == null) {
                    chatResult = ollamaAPI.chat(requestModel);
                    chatHistory = chatResult.getChatHistory();

                    System.out.println(chatResult.getResponse());
                    server.getCommandManager().executeWithPrefix(botSource, "/say " + chatResult.getResponse());
                }
                else {
                    requestModel = builder.withMessages(chatResult.getChatHistory()).withMessage(OllamaChatMessageRole.USER, playerMessage).build();
                    chatResult = ollamaAPI.chat(requestModel);

                    System.out.println(chatResult.getResponse());
                    server.getCommandManager().executeWithPrefix(botSource, "/say " + chatResult.getResponse());
                }



            } catch (OllamaBaseException | InterruptedException | IOException e) {
                LOGGER.error("{}", e.getMessage());
                throw new RuntimeException(e);
            }


        }

        else {

            LOGGER.info("Ollama Server is unreachable!");
        }



    }

}


