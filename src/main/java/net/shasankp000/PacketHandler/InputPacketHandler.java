package net.shasankp000.PacketHandler;


import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.minecraft.util.math.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class InputPacketHandler {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static int ticksRemaining = 0;
    private static Vec3d lastPosition = null;


    public static class BotLookController {

        /**
         * Makes the bot look in the given cardinal direction.
         * @param bot The bot whose direction is being set.
         * @param direction The direction (NORTH, SOUTH, EAST, WEST, UP, DOWN).
         */
        public static void lookInDirection(ServerPlayerEntity bot, Direction direction) {
            switch (direction) {
                case NORTH -> setLook(bot, 180, 0);
                case SOUTH -> setLook(bot, 0, 0);
                case EAST -> setLook(bot, -90, 0);
                case WEST -> setLook(bot, 90, 0);
                case UP -> setLook(bot, bot.getYaw(), -90);
                case DOWN -> setLook(bot, bot.getYaw(), 90);
            }
        }

        /**
         * Sets the bot's yaw and pitch manually.
         * @param bot The bot whose yaw and pitch are being set.
         * @param yaw The yaw (horizontal rotation).
         * @param pitch The pitch (vertical rotation).
         */
        public static void setLook(ServerPlayerEntity bot, float yaw, float pitch) {
            bot.setYaw(normalizeYaw(yaw)); // Normalize yaw to [0, 360)
            bot.setPitch(MathHelper.clamp(pitch, -90, 90)); // Clamp pitch to valid range
        }

        /**
         * Normalizes a yaw value to the range [0, 360).
         * @param yaw The yaw value to normalize.
         * @return The normalized yaw value.
         */
        private static float normalizeYaw(float yaw) {
            return (yaw % 360 + 360) % 360;
        }
    }


    /**
     * Stops the bot from sprinting by sending a "STOP_SPRINTING" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketStopSprint(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerPlayNetworkHandler networkHandler = bot.networkHandler;

            // Create a packet to simulate releasing the sprint key.
            ClientCommandC2SPacket packet = new ClientCommandC2SPacket(bot, ClientCommandC2SPacket.Mode.STOP_SPRINTING);

            // Send the packet to the server
            networkHandler.onClientCommand(packet);

            context.getSource().sendMessage(Text.of("Sneak action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending stop sprint packet: {}", e.getMessage());
        }
    }


    // My own code begins here.

    /**
     * Starts the bot sprinting by sending a "START_SPRINTING" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketSprint(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerPlayNetworkHandler networkHandler = bot.networkHandler;

            // Create a packet to simulate holding down the sprint key.
            ClientCommandC2SPacket packet = new ClientCommandC2SPacket(bot, ClientCommandC2SPacket.Mode.START_SPRINTING);

            // Send the packet to the server
            networkHandler.onClientCommand(packet);

            context.getSource().sendMessage(Text.of("Sprint action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending sprint packet: {}", e.getMessage());
        }
    }

    /**
     * Makes the bot start sneaking by sending a "PRESS_SHIFT_KEY" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketSneak(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerPlayNetworkHandler networkHandler = bot.networkHandler;

            // Create a packet to simulate pressing the sneak key
            ClientCommandC2SPacket packet = new ClientCommandC2SPacket(bot, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY);

            // Send the packet to the server
            networkHandler.onClientCommand(packet);

            context.getSource().sendMessage(Text.of("Sneak action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending sneak packet: {}", e.getMessage());
        }
    }

    /**
     * Makes the bot stop sneaking by sending a "RELEASE_SHIFT_KEY" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketUnSneak(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {
            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerPlayNetworkHandler networkHandler = bot.networkHandler;

            // Create a packet to simulate releasing the sneak key
            ClientCommandC2SPacket packet = new ClientCommandC2SPacket(bot, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY);

            // Send the packet to the server
            networkHandler.onClientCommand(packet);

            context.getSource().sendMessage(Text.of("Sneak action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending unSneak packet: {}", e.getMessage());
        }
    }

    /**
     * Simulates pressing the "W" key for the bot, moving it forward.
     * This method also calculates and updates the bot's position manually during the action.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketPressWKey(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.getPos();

        ServerPlayNetworkHandler networkHandler = bot.networkHandler;
        PlayerInputC2SPacket packet = new PlayerInputC2SPacket(0.0f, 1.0f, false, false); // W key packet.
        networkHandler.onPlayerInput(packet);


        System.out.println("Recorded current bot position as last pos: " + lastPosition);


        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getHorizontalFacing();
            System.out.println(direction.getAxis().getName());

            if(direction.getAxis().equals(Direction.Axis.X)) {


                final ServerPlayerEntity[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(1.0F).multiply(0.1);
                        finalBot[0].setPos(finalBot[0].getX() + forwardMovement.x, finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }

            else if(direction.getAxis().equals(Direction.Axis.Z)) {

                final ServerPlayerEntity[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(1.0F).multiply(0.1);
                        finalBot[0].setPos(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z);
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }

            context.getSource().sendMessage(Text.of("W key press action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending W key packet: {}", e.getMessage());
        }
    }

    /**
     * Simulates pressing the "S" key for the bot, moving it backward.
     * This method also calculates and updates the bot's position manually during the action.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketPressSKey(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.getPos();

        ServerPlayNetworkHandler networkHandler = bot.networkHandler;
        PlayerInputC2SPacket packet = new PlayerInputC2SPacket(0.0f, -1.0f, false, false); // S key packet.
        networkHandler.onPlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);

        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getHorizontalFacing();
            System.out.println(direction.getAxis().getName());

            if(direction.getAxis().equals(Direction.Axis.X)) {


                final ServerPlayerEntity[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(1.0F).multiply(-0.1);
                        finalBot[0].setPos(finalBot[0].getX() + forwardMovement.x, finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }

            else if(direction.getAxis().equals(Direction.Axis.Z)) {

                final ServerPlayerEntity[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(1.0F).multiply(-0.1);
                        finalBot[0].setPos(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z);
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }


            context.getSource().sendMessage(Text.of("S key press action performed for bot: " + bot.getName().getString()));

        } catch (Exception e) {
            LOGGER.error("Caught exception while sending S key packet: {}", e.getMessage());
        }
    }

    /**
     * Simulates pressing the "A" key for the bot, moving it left (strafe).
     * This method also calculates and updates the bot's position manually during the action.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketPressAKey(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.getPos();

        ServerPlayNetworkHandler networkHandler = bot.networkHandler;
        PlayerInputC2SPacket packet = new PlayerInputC2SPacket(-1.0f, 0.0f, false, false); // A key packet.
        networkHandler.onPlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);


        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getHorizontalFacing();
            System.out.println(direction.getAxis().getName());

            final ServerPlayerEntity[] finalBot = {bot};

            if (direction.getAxis().equals(Direction.Axis.X)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(2.5F).multiply(0.3);
                        finalBot[0].setPos(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }
            else if (direction.getAxis().equals(Direction.Axis.Z)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(2.5F).multiply(0.3);
                        finalBot[0].setPos(finalBot[0].getX() + forwardMovement.getX(), finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }

            }

            context.getSource().sendMessage(Text.of("A key press action performed for bot: " + bot.getName().getString()));

        } catch (Exception e) {
            LOGGER.error("Caught exception while sending A key packet: {}", e.getMessage());
        }
    }


    /**
     * Simulates pressing the "D" key for the bot, moving it right (strafe).
     * This method also calculates and updates the bot's position manually during the action.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketPressDKey(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.getPos();

        ServerPlayNetworkHandler networkHandler = bot.networkHandler;
        PlayerInputC2SPacket packet = new PlayerInputC2SPacket(1.0f, 0.0f, false, false); // D key packet.
        networkHandler.onPlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);

        try {
            ticksRemaining = 20; // Number of ticks to hold the key


            Direction direction = bot.getHorizontalFacing();
            System.out.println(direction.getAxis().getName());

            final ServerPlayerEntity[] finalBot = {bot};

            if (direction.getAxis().equals(Direction.Axis.X)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(2.5F).multiply(-0.3);
                        finalBot[0].setPos(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }


            }
            else if (direction.getAxis().equals(Direction.Axis.Z)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3d forwardMovement = finalBot[0].getRotationVec(2.5F).multiply(-0.3);
                        finalBot[0].setPos(finalBot[0].getX() + forwardMovement.getX(), finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].getPos());
                }

            }

            context.getSource().sendMessage(Text.of("D key press action performed for bot: " + bot.getName().getString()));

        } catch (Exception e) {
            LOGGER.error("Caught exception while sending D key packet: {}", e.getMessage());
        }
    }

    /**
     * Releases all movement keys for the bot, the sneak and sprint keys to be specific.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketReleaseMovementKey(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayerEntity bot = null;

        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            manualPacketUnSneak(context);
            manualPacketStopSprint(context);

            context.getSource().sendMessage(Text.of("Released movement keys for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending release movement key packet: {}", e.getMessage());
        }
    }

}
