package net.shasankp000.Commands;



import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
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
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.Entity.RayCasting;
import net.shasankp000.Entity.createFakePlayer;
import net.shasankp000.OllamaClient.ollamaClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.shasankp000.OllamaClient.ollamaClient.*;
import static net.shasankp000.PathFinding.PathFinder.*;
import static net.minecraft.server.command.CommandManager.literal;
import static net.shasankp000.PathFinding.PathTracer.tracePath;

public class spawnFakePlayer {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public record BotMovementTask(MinecraftServer server, ServerCommandSource botSource,
                                   String botName) implements Runnable {

        @Override
            public void run() {

                stopMoving(server, botSource, botName);

                LOGGER.info("{} has stopped walking!", botName);

            }
        }

    public record BotStopTask(MinecraftServer server, ServerCommandSource botSource,
                                  String botName) implements Runnable {

        @Override
        public void run() {

            stopMoving(server, botSource, botName);
            LOGGER.info("{} has stopped walking!", botName);


        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("bot")
                        .then(literal("spawn")
                                .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                        .executes(context -> { spawnBot(context); return 1; })
                                )
                        )
                        .then(literal("walk")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("till", IntegerArgumentType.integer())
                                                .executes(context -> { botWalk(context); return 1; })
                                        )
                                )
                        )
                        .then(literal("jump")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { botJump(context); return 1; })
                                )
                        )
                        .then(literal("teleport_forward")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { teleportForward(context); return 1; })
                                )
                        )
                        .then(literal("test_chat_message")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { testChatMessage(context); return 1; })
                                )
                        )
                        .then(literal("go_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .executes(context -> { botGo(context); return 1; })
                                        )
                                )
                        )
                        .then(literal("send_message_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> { ollamaClient.execute(context); return 1; })
                                        )
                                )
                        )
                        .then(literal("detect_entities")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                                .executes(context -> {
                                                    String botName = StringArgumentType.getString(context,"bot_name");
                                                    ServerPlayerEntity bot = context.getSource().getServer().getPlayerManager().getPlayer(botName);
                                                    if (bot != null) {
                                                        RayCasting.detect(bot);
                                                    }
                                                    return 1;
                                                })
                                )
                        )
        ));
    }


    private static void spawnBot(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
        BlockPos spawnPos = getBlockPos(context);

        RegistryKey<World> dimType = context.getSource().getWorld().getRegistryKey();

        Vec2f facing = context.getSource().getRotation();

        Vec3d pos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

        GameMode mode = GameMode.SURVIVAL;

        String botName = StringArgumentType.getString(context, "bot_name");


        createFakePlayer.createFake(
                botName,
                server,
                pos,
                facing.y,
                facing.x,
                dimType,
                mode,
                false
        );



        LOGGER.info("Spawned new bot {}!", botName);

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

        if (bot!=null) {
            bot.changeGameMode(mode);

            ollamaClient.botName = botName; // set the bot's name.

            ServerCommandSource serverSource = server.getCommandSource();

            ChatUtils.sendChatMessages(serverSource, "Please wait while " + botName + " connects to the language model.");

            initializeOllamaClient();

            new Thread( () -> {

                while (!isInitialized) {
                    try {
                        Thread.sleep(500L); // Check every 500ms
                    } catch (InterruptedException e) {
                        LOGGER.error("Ollama client initialization failed.");
                        throw new RuntimeException(e);
                    }
                }

                sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4));

                try {
                    Thread.sleep(1500);
                    AutoFaceEntity.startAutoFace(bot);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }).start();


        }


    }

    private static void notImplementedMessage(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        String botName = StringArgumentType.getString(context, "bot_name");

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

            server.getCommandManager().executeWithPrefix(botSource, "/say §cThis command has not been implemented yet and is a work in progress! ");


        }


    }

    private static void teleportForward(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {
            String botName = bot.getName().getLiteralString();

            BlockPos currentPosition = bot.getBlockPos();
            BlockPos newPosition = currentPosition.add(1, 0, 0); // Move one block forward
            bot.teleport(newPosition.getX(), newPosition.getY(), newPosition.getZ());

            LOGGER.info("Teleported {} 1 positive block ahead", botName);

        }

    }

    private static void botWalk(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        int travelTime = IntegerArgumentType.getInteger(context, "till");


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);
            moveForward(server, botSource, botName);

            scheduler.schedule(new BotStopTask(server, botSource, botName), travelTime, TimeUnit.SECONDS);


        }

    }


    private static void botJump(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            bot.jump();


            LOGGER.info("{} jumped!", botName);


        }

    }

    private static void testChatMessage(CommandContext<ServerCommandSource> context) {

        String response = "I am doing great! It feels good to be able to chat with you again after a long time. So, how have you been doing? Are you enjoying the game world and having fun playing Minecraft with me? Let's continue chatting about whatever topic comes to mind! I love hearing from you guys and seeing your creations in the game. Don't hesitate to share anything with me, whether it's an idea, a problem, or simply something that makes you laugh. Cheers!";

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot != null) {

            ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();
            ChatUtils.sendChatMessages(botSource, response);

        }
        else {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

    }

    private static void botGo(CommandContext<ServerCommandSource> context) {

        // A work in progress.

        MinecraftServer server = context.getSource().getServer();

        BlockPos position = BlockPosArgumentType.getBlockPos(context, "pos");

        int x_distance = position.getX();

        int y_distance = position.getY();

        int z_distance = position.getZ();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {
            String botName = bot.getName().getLiteralString();

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

            server.sendMessage(Text.literal("Finding the shortest path to the target, please wait patiently if the game seems hung"));
            // Calculate path
            ServerPlayerEntity finalBot = bot;
            new Thread(() -> {

                List<BlockPos> path = calculatePath(finalBot.getBlockPos(), new BlockPos(x_distance, y_distance , z_distance));

                path = simplifyPath(path);

                LOGGER.info("{}", path);

                tracePath(server, botSource, botName, path);


            }).start();


        }

    }



    public static void moveForward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");

        }

    }

    private static void moveBackward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move backward");

        }


    }

    public static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {

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
