package net.shasankp000.Commands;


import carpet.CarpetSettings;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
//import net.shasankp000.HttpClient.httpClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class spawnFakePlayer {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static class BotMovementTask implements Runnable {
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
                                startMoving(server, botSource, botName);

                                scheduler.schedule(new BotMovementTask(server, botSource, botName), travelTime, TimeUnit.SECONDS);


                            }

                            return 1;
                        })))));

    }


    private static void startMoving(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");

        }

    }

    private static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " stop");

        }


    }


    private static @NotNull BlockPos getBlockPos(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer(); // gets the player who executed the command


        // Set spawn location for the second player
        assert player != null;
        return new BlockPos((int) player.getX() + 5, (int) player.getY(), (int) player.getZ());
    }

}
