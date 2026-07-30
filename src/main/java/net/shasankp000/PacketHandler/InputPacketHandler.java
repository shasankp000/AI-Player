package net.shasankp000.PacketHandler;


import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class InputPacketHandler {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static int ticksRemaining = 0;
    private static Vec3 lastPosition = null;


    public static class BotLookController {

        /**
         * Makes the bot look in the given cardinal direction.
         * @param bot The bot whose direction is being set.
         * @param direction The direction (NORTH, SOUTH, EAST, WEST, UP, DOWN).
         */
        public static void lookInDirection(ServerPlayer bot, Direction direction) {
            switch (direction) {
                case NORTH -> setLook(bot, 180, 0);
                case SOUTH -> setLook(bot, 0, 0);
                case EAST -> setLook(bot, -90, 0);
                case WEST -> setLook(bot, 90, 0);
                case UP -> setLook(bot, bot.getYRot(), -90);
                case DOWN -> setLook(bot, bot.getYRot(), 90);
            }
        }

        /**
         * Sets the bot's yaw and pitch manually.
         * @param bot The bot whose yaw and pitch are being set.
         * @param yaw The yaw (horizontal rotation).
         * @param pitch The pitch (vertical rotation).
         */
        public static void setLook(ServerPlayer bot, float yaw, float pitch) {
            bot.setYRot(normalizeYaw(yaw)); // Normalize yaw to [0, 360)
            bot.setXRot(Mth.clamp(pitch, -90, 90)); // Clamp pitch to valid range
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
    public static void manualPacketStopSprint(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerGamePacketListenerImpl networkHandler = bot.connection;

            // Create a packet to simulate releasing the sprint key.
            ServerboundPlayerCommandPacket packet = new ServerboundPlayerCommandPacket(bot, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);

            // Send the packet to the server
            networkHandler.handlePlayerCommand(packet);

            context.getSource().sendSystemMessage(Component.nullToEmpty("Sneak action performed for bot: " + bot.getName().getString()));
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
    public static void manualPacketSprint(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            // Get the bot's network handler (which implements ServerPlayPacketListener)
            ServerGamePacketListenerImpl networkHandler = bot.connection;

            // Create a packet to simulate holding down the sprint key.
            ServerboundPlayerCommandPacket packet = new ServerboundPlayerCommandPacket(bot, ServerboundPlayerCommandPacket.Action.START_SPRINTING);

            // Send the packet to the server
            networkHandler.handlePlayerCommand(packet);

            context.getSource().sendSystemMessage(Component.nullToEmpty("Sprint action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending sprint packet: {}", e.getMessage());
        }
    }

    /**
     * Makes the bot start sneaking by sending a "PRESS_SHIFT_KEY" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketSneak(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            bot.setShiftKeyDown(true);

            context.getSource().sendSystemMessage(Component.nullToEmpty("Sneak action performed for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending sneak packet: {}", e.getMessage());
        }
    }

    /**
     * Makes the bot stop sneaking by sending a "RELEASE_SHIFT_KEY" packet to the server.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketUnSneak(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {
            bot.setShiftKeyDown(false);

            context.getSource().sendSystemMessage(Component.nullToEmpty("Sneak action performed for bot: " + bot.getName().getString()));
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
    public static void manualPacketPressWKey(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.position();

        ServerGamePacketListenerImpl networkHandler = bot.connection;
        ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(true, false, false, false, false, false, false)); // W key packet.
        networkHandler.handlePlayerInput(packet);


        System.out.println("Recorded current bot position as last pos: " + lastPosition);


        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getDirection();
            System.out.println(direction.getAxis().getName());

            if(direction.getAxis().equals(Direction.Axis.X)) {


                final ServerPlayer[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(1.0F).scale(0.1);
                        finalBot[0].setPosRaw(finalBot[0].getX() + forwardMovement.x, finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }

            else if(direction.getAxis().equals(Direction.Axis.Z)) {

                final ServerPlayer[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(1.0F).scale(0.1);
                        finalBot[0].setPosRaw(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z);
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }

            context.getSource().sendSystemMessage(Component.nullToEmpty("W key press action performed for bot: " + bot.getName().getString()));
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
    public static void manualPacketPressSKey(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.position();

        ServerGamePacketListenerImpl networkHandler = bot.connection;
        ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, true, false, false, false, false, false)); // S key packet.
        networkHandler.handlePlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);

        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getDirection();
            System.out.println(direction.getAxis().getName());

            if(direction.getAxis().equals(Direction.Axis.X)) {


                final ServerPlayer[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(1.0F).scale(-0.1);
                        finalBot[0].setPosRaw(finalBot[0].getX() + forwardMovement.x, finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }

            else if(direction.getAxis().equals(Direction.Axis.Z)) {

                final ServerPlayer[] finalBot = {bot};
                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {

                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(1.0F).scale(-0.1);
                        finalBot[0].setPosRaw(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z);
                        System.out.println("Updating movement value for S key by 1");


                        ticksRemaining--;

                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }


            context.getSource().sendSystemMessage(Component.nullToEmpty("S key press action performed for bot: " + bot.getName().getString()));

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
    public static void manualPacketPressAKey(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.position();

        ServerGamePacketListenerImpl networkHandler = bot.connection;
        ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, true, false, false, false, false)); // A key packet.
        networkHandler.handlePlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);


        try {
            ticksRemaining = 20; // Number of ticks to hold the key

            Direction direction = bot.getDirection();
            System.out.println(direction.getAxis().getName());

            final ServerPlayer[] finalBot = {bot};

            if (direction.getAxis().equals(Direction.Axis.X)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(2.5F).scale(0.3);
                        finalBot[0].setPosRaw(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }
            else if (direction.getAxis().equals(Direction.Axis.Z)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(2.5F).scale(0.3);
                        finalBot[0].setPosRaw(finalBot[0].getX() + forwardMovement.x(), finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }

            }

            context.getSource().sendSystemMessage(Component.nullToEmpty("A key press action performed for bot: " + bot.getName().getString()));

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
    public static void manualPacketPressDKey(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }


        lastPosition = bot.position();

        ServerGamePacketListenerImpl networkHandler = bot.connection;
        ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, true, false, false, false)); // D key packet.
        networkHandler.handlePlayerInput(packet);

        System.out.println("Recorded current bot position as last pos: " + lastPosition);

        try {
            ticksRemaining = 20; // Number of ticks to hold the key


            Direction direction = bot.getDirection();
            System.out.println(direction.getAxis().getName());

            final ServerPlayer[] finalBot = {bot};

            if (direction.getAxis().equals(Direction.Axis.X)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(2.5F).scale(-0.3);
                        finalBot[0].setPosRaw(finalBot[0].getX(), finalBot[0].getY(), finalBot[0].getZ() + forwardMovement.z());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }


            }
            else if (direction.getAxis().equals(Direction.Axis.Z)) {

                ServerTickEvents.END_SERVER_TICK.register(server1 -> {
                    if (ticksRemaining > 0) {
                        // Manually update the bot's position
                        Vec3 forwardMovement = finalBot[0].getViewVector(2.5F).scale(-0.3);
                        finalBot[0].setPosRaw(finalBot[0].getX() + forwardMovement.x(), finalBot[0].getY(), finalBot[0].getZ());
                        System.out.println("Updating movement value for A key");

                        ticksRemaining--;
                    }
                });

                if (ticksRemaining <= 0) {
                    System.out.println("Current bot position: " + finalBot[0].position());
                }

            }

            context.getSource().sendSystemMessage(Component.nullToEmpty("D key press action performed for bot: " + bot.getName().getString()));

        } catch (Exception e) {
            LOGGER.error("Caught exception while sending D key packet: {}", e.getMessage());
        }
    }

    /**
     * Releases all movement keys for the bot, the sneak and sprint keys to be specific.
     *
     * @param context The command context containing the server and bot information.
     */
    public static void manualPacketReleaseMovementKey(CommandContext<CommandSourceStack> context) {

        MinecraftServer server = context.getSource().getServer();
        ServerPlayer bot = null;

        try {
            bot = EntityArgument.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendSystemMessage(Component.nullToEmpty("The requested bot could not be found on the server!"));
            server.sendSystemMessage(Component.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        try {

            manualPacketUnSneak(context);
            manualPacketStopSprint(context);

            context.getSource().sendSystemMessage(Component.nullToEmpty("Released movement keys for bot: " + bot.getName().getString()));
        } catch (Exception e) {
            LOGGER.error("Caught exception while sending release movement key packet: {}", e.getMessage());
        }
    }

}
