package net.shasankp000.Commands;


import carpet.CarpetSettings;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.OllamaClient.ollamaClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.shasankp000.PathFinding.PathFinder.*;


public class spawnFakePlayer {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static class BotMovementTask implements Runnable {
        private final MinecraftServer server;
        private final ServerCommandSource botSource;
        private final String botName;

        public BotMovementTask(MinecraftServer server, ServerCommandSource botSource, String botName) {
            this.server = server;
            this.botSource = botSource;
            this.botName = botName;
        }

        @Override
        public void run() {
            stopMoving(server, botSource, botName);
            LOGGER.info("{} has stopped walking!", botName);
        }
    }





    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static void register()  {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("spawnBot")
                .then(CommandManager.argument("botName", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(context -> {

                            MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
                            BlockPos spawnPos = getBlockPos(context);

                            RegistryKey<World> dimType = context.getSource().getWorld().getRegistryKey();

                            Vec2f facing = context.getSource().getRotation();

                            Vec3d pos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

                            CarpetSettings.allowSpawningOfflinePlayers = true;

                            GameMode mode = GameMode.SURVIVAL;

                            String bot_name = StringArgumentType.getString(context, "botName");

                            EntityPlayerMPFake.createFake(
                                    bot_name,
                                    server,
                                    pos,
                                    facing.y,
                                    facing.x,
                                    dimType,
                                    mode,
                                    false
                            );

                            LOGGER.info("Spawned new bot {}!", bot_name);

                            return 1;


                        } ))));


    }

    public static void additionalBotCommands() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("teleportForward")
                .then(CommandManager.argument("botName", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(context -> {

                            MinecraftServer server = context.getSource().getServer();

                            ServerPlayerEntity bot = server.getPlayerManager().getPlayer("Steve");


                            if (bot == null) {

                                context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
                                server.sendMessage(Text.literal("Error! Bot not found!"));
                                LOGGER.error("The requested bot could not be found on the server!");

                            }

                            else {

                                BlockPos currentPosition = bot.getBlockPos();
                                BlockPos newPosition = currentPosition.add(1, 0, 0); // Move one block forward
                                bot.teleport(newPosition.getX(), newPosition.getY(), newPosition.getZ());

                                LOGGER.info("Teleported Steve 1 positive block ahead");

                            }

                            return 1;



                        }))));


        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("botJump")
                .then(CommandManager.argument("botName", StringArgumentType.greedyString()) // gets all strings including whitespaces instead of a single string.
                        .executes(context -> {

                            MinecraftServer server = context.getSource().getServer();

                            String bot_name = StringArgumentType.getString(context, "botName");

                            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(bot_name);


                            if (bot == null) {

                                context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
                                server.sendMessage(Text.literal("Error! Bot not found!"));
                                LOGGER.error("The requested bot could not be found on the server!");

                            }

                            else {

                                bot.jump();


                                LOGGER.info("{} jumped!", bot_name);


                            }

                            return 1;


                        } ))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("botWalk")
                .then(CommandManager.argument("botName", StringArgumentType.string()) // gets all strings including whitespaces instead of a single string.
                        .then(CommandManager.argument("till", IntegerArgumentType.integer())
                        .executes(context ->  {

                            MinecraftServer server = context.getSource().getServer();

                            String botName = StringArgumentType.getString(context, "botName");

                            int travelTime = IntegerArgumentType.getInteger(context, "till");

                            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);


                            if (bot == null) {

                                context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
                                server.sendMessage(Text.literal("Error! Bot not found!"));
                                LOGGER.error("The requested bot could not be found on the server!");

                            }

                            else {

                                ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);
                                moveForward(server, botSource, botName);

                                scheduler.schedule(new BotMovementTask(server, botSource, botName), travelTime, TimeUnit.SECONDS);


                            }

                            return 1;
                        })))));



        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("botGo")
                .then(CommandManager.argument("botName", StringArgumentType.string()) // gets all strings including whitespaces instead of a single string.
                        .then(CommandManager.argument("x-axis", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y-axis", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("z-axis", IntegerArgumentType.integer())
                                                .executes( context -> {

                                                    MinecraftServer server = context.getSource().getServer();

                                                    String botName = StringArgumentType.getString(context, "botName");

                                                    int x_distance = IntegerArgumentType.getInteger(context, "x-axis");

                                                    int y_distance = IntegerArgumentType.getInteger(context, "y-axis");

                                                    int z_distance = IntegerArgumentType.getInteger(context, "z-axis");

                                                    ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

                                                    if (bot == null) {

                                                        context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
                                                        server.sendMessage(Text.literal("Error! Bot not found!"));
                                                        LOGGER.error("The requested bot could not be found on the server!");

                                                    }

                                                    else {
                                                        ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

                                                        server.sendMessage(Text.literal("Finding the shortest path to the target, please wait patiently if the game seems hung"));
                                                        // Calculate path
                                                        List<BlockPos> path = calculatePath(bot.getBlockPos(), new BlockPos(x_distance, y_distance , z_distance));

                                                        path = simplifyPath(path);

                                                        char axis = identifyPrimaryAxis(path);

                                                        LOGGER.info("Primary axis: {}", axis);

                                                        LOGGER.info("{}", path);

                                                    }


                                                    return 1;
                                                })))))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("testChatMessage")
                .then(CommandManager.argument("botName", StringArgumentType.string())// gets all strings including whitespaces instead of a single string.
                                .executes( context -> {

                                    String response = "I am doing great! It feels good to be able to chat with you again after a long time. So, how have you been doing? Are you enjoying the game world and having fun playing Minecraft with me? Let's continue chatting about whatever topic comes to mind! I love hearing from you guys and seeing your creations in the game. Don't hesitate to share anything with me, whether it's an idea, a problem, or simply something that makes you laugh. Cheers!";

                                    String botName = StringArgumentType.getString(context,"botName");

                                    MinecraftServer server = context.getSource().getServer();

                                    ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

                                    if (bot != null) {

                                        ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();
                                        ChatUtils.sendChatMessages(botSource, response);

                                    }
                                    else {
                                        context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
                                        server.sendMessage(Text.literal("Error! Bot not found!"));
                                        LOGGER.error("The requested bot could not be found on the server!");

                                    }

                                            return 1;
                                        }

                                ))));

    }





    public static void saySomething(MinecraftServer server, ServerCommandSource source, String message) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/say " + message );

        }

    }

    private static void moveForward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");

        }

    }

    private static void moveBackward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move backward");

        }


    }

    private static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " stop");

        }


    }

    private static void moveLeft(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move left");

        }

    }

    private static void moveRight(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move right");

        }

    }


    private static @NotNull BlockPos getBlockPos(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer(); // gets the player who executed the command


        // Set spawn location for the second player
        assert player != null;
        return new BlockPos((int) player.getX() + 5, (int) player.getY(), (int) player.getZ());
    }

}
