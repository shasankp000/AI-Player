package net.shasankp000.HttpClient;

// This is still a work in progress.

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// http client related imports

//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import javax.net.ssl.HttpsURLConnection;
//import java.io.BufferedReader;
//import java.io.DataOutputStream;
//import java.io.IOException;
//import java.io.InputStreamReader;
//import java.net.URL;
//import java.util.HashMap;


public class httpClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static void getPlayerMessage() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("tellBot")
                .then(CommandManager.argument("message", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(httpClient::execute))));

    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");

        LOGGER.info("Player sent a message: {}", message);


        return 1;
    }

}


