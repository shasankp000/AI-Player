package net.shasankp000.ChatUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ChatUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    // Configuration
    private static final int MAX_CHAT_LENGTH = 100;
    private static final long MESSAGE_DELAY_MS = 2500L;
    private static final List<String> COLOR_CODES = Arrays.asList(
            "§9", "§b", "§d", "§e", "§6", "§5", "§c", "§7"
    );

    private static final Random random = new Random();

    // We no longer use a separate scheduler for chat messages.
    // The server's main thread will handle scheduling internally.

    /**
     * Send a message from the bot with optional delay.
     * This method is designed to be called from any thread.
     * @param source The command source (usually the bot).
     * @param message The message to send.
     * @param withDelay Whether to add typing delays between message parts.
     */
    public static void sendChatMessages(ServerCommandSource source, String message, boolean withDelay) {
        if (message == null || message.trim().isEmpty()) {
            LOGGER.warn("Attempted to send null or empty message");
            return;
        }

        MinecraftServer server = source.getServer();
        if (server == null) {
            LOGGER.error("Server is null, cannot send message");
            return;
        }

        LOGGER.info("Sending chat message (withDelay={}): '{}'", withDelay, message);

        List<String> messageParts = splitMessage(message.trim());
        LOGGER.info("Message split into {} parts", messageParts.size());

        // This is the core fix. We are queuing a single task on the server's main thread.
        // The task itself handles all logic, including delays.
        server.execute(() -> scheduleAndSendMessages(server, source, messageParts, 0, withDelay));
    }

    /**
     * A recursive helper method to handle delayed message sending on the server thread.
     * @param server The Minecraft server instance.
     * @param source The command source.
     * @param messageParts The list of message parts to send.
     * @param partIndex The current index of the message part to send.
     * @param withDelay If delays should be used.
     */
    private static void scheduleAndSendMessages(MinecraftServer server, ServerCommandSource source, List<String> messageParts, int partIndex, boolean withDelay) {
        if (partIndex >= messageParts.size()) {
            // All parts have been sent, stop the recursion.
            return;
        }

        // Send the current message part.
        sendSingleMessage(server, source, messageParts.get(partIndex), partIndex);

        // Schedule the next part if there are more to send and a delay is requested.
        if (withDelay && partIndex < messageParts.size() - 1) {
            // Schedule a new task on the main thread after a delay.
            // This prevents freezing the main thread.
            new Thread(() -> {
                try {
                    Thread.sleep(MESSAGE_DELAY_MS);
                    server.execute(() -> scheduleAndSendMessages(server, source, messageParts, partIndex + 1, true));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    /**
     * Send a single message part to the chat.
     */
    private static void sendSingleMessage(MinecraftServer server, ServerCommandSource source, String message, int partIndex) {
        try {

            // Inside sendSingleMessage
            String sourceName = (source.getPlayer() != null) ? source.getPlayer().getName().getString() : source.getName();
            String formattedMessage = message;

            // Check if the message already starts with the source name.
            if (formattedMessage.trim().startsWith(sourceName + ": ")) {
                formattedMessage = formattedMessage.replace(sourceName + ": ", "");
            }

            // Now use the formattedMessage to build the final colored message.
            String coloredMessage = getRandomColorCode() + formattedMessage;


            LOGGER.info("Broadcasting message part {}: '{}' from source: {}", partIndex, coloredMessage, sourceName);

            // Using the command manager to send the message
            server.getCommandManager().executeWithPrefix(source, "/say " + coloredMessage);

            LOGGER.debug("Successfully broadcasted message part {}", partIndex);

        } catch (Exception e) {
            LOGGER.error("Failed to send message part {}: {}", partIndex, e.getMessage(), e);
        }
    }

    /**
     * Send an immediate system message from the server (no delay, no color randomization).
     * @param source The command source.
     * @param message The message to send.
     */
    public static void sendSystemMessage(ServerCommandSource source, String message) {
        if (message == null || message.trim().isEmpty()) {
            LOGGER.warn("Attempted to send null or empty system message");
            return;
        }

        LOGGER.info("Sending system message: '{}'", message);

        MinecraftServer server = source.getServer();
        if (server == null) {
            LOGGER.error("Server is null, cannot send system message");
            return;
        }

        // Execute immediately on the server thread to prevent threading issues.
        server.execute(() -> {
            try {
                Text textComponent = Text.literal("§7" + message); // Gray color for system messages
                server.getCommandManager().executeWithPrefix(source.withSilent(), "/say " + textComponent.getString());
                LOGGER.debug("Successfully sent system message");
            } catch (Exception e) {
                LOGGER.error("Failed to send system message: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Split a long message into smaller parts that fit within chat limits.
     */
    private static List<String> splitMessage(String message) {
        List<String> parts = new ArrayList<>();
        if (message.length() <= MAX_CHAT_LENGTH) {
            parts.add(message);
            return parts;
        }

        String[] sentences = message.split("(?<=[.!?])\\s+");
        StringBuilder currentPart = new StringBuilder();

        for (String sentence : sentences) {
            if (currentPart.length() + sentence.length() + 1 > MAX_CHAT_LENGTH) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString().trim());
                    currentPart.setLength(0);
                }
                if (sentence.length() > MAX_CHAT_LENGTH) {
                    parts.addAll(splitLongSentence(sentence));
                } else {
                    currentPart.append(sentence);
                }
            } else {
                if (currentPart.length() > 0) {
                    currentPart.append(" ");
                }
                currentPart.append(sentence);
            }
        }

        if (currentPart.length() > 0) {
            parts.add(currentPart.toString().trim());
        }
        return parts;
    }

    /**
     * Split a very long sentence by words.
     */
    private static List<String> splitLongSentence(String sentence) {
        List<String> parts = new ArrayList<>();
        String[] words = sentence.split("\\s+");
        StringBuilder currentPart = new StringBuilder();

        for (String word : words) {
            if (currentPart.length() + word.length() + 1 > MAX_CHAT_LENGTH) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString().trim());
                    currentPart.setLength(0);
                }
                if (word.length() > MAX_CHAT_LENGTH) {
                    parts.add(word.substring(0, MAX_CHAT_LENGTH - 3) + "...");
                } else {
                    currentPart.append(word);
                }
            } else {
                if (currentPart.length() > 0) {
                    currentPart.append(" ");
                }
                currentPart.append(word);
            }
        }

        if (currentPart.length() > 0) {
            parts.add(currentPart.toString().trim());
        }
        return parts;
    }


    /**
     * Get a random color code for message formatting.
     */
    public static String getRandomColorCode() {
        return COLOR_CODES.get(random.nextInt(COLOR_CODES.size()));
    }

    /**
     * Send a message with default delay behavior (delayed).
     * This is a convenience method.
     */
    public static void sendChatMessages(ServerCommandSource source, String message) {
        sendChatMessages(source, message, true);
    }
}
