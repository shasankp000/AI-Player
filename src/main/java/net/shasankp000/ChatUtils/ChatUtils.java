package net.shasankp000.ChatUtils;

import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ChatUtils {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static final int MAX_CHAT_LENGTH = 100; // Adjust based on Minecraft's chat length limit


    public static String chooseFormatterRandom() {
        List<String> givenList = Arrays.asList("§9", "§b", "§d", "§e", "§6", "§5", "§c", "§7");
        Random rand = new Random();
        return givenList.get(rand.nextInt(givenList.size()));
    }

    public static List<String> splitMessage(String message) {
        List<String> messages = new ArrayList<>();
        String[] sentences = message.split("(?<=[.!?])\\s*"); // Split by punctuation marks

        StringBuilder currentMessage = new StringBuilder();
        for (String sentence : sentences) {
            if (currentMessage.length() + sentence.length() + 1 > MAX_CHAT_LENGTH) {
                messages.add(currentMessage.toString().trim());
                currentMessage.setLength(0);
            }
            if (!currentMessage.isEmpty()) {
                currentMessage.append(" ");
            }
            currentMessage.append(sentence);
        }

        if (!currentMessage.isEmpty()) {
            messages.add(currentMessage.toString().trim());
        }

        return messages;
    }


    public static void sendChatMessages(ServerCommandSource source, String message) {
        List<String> messages = splitMessage(message);


        new Thread(() -> {
            for (String msg : messages) {
                try {
                    String formatter = chooseFormatterRandom();
                    source.getServer().getCommandManager().executeWithPrefix(source, "/say " + formatter + msg);
                    Thread.sleep(2500); // Introduce a slight delay between messages
                } catch (InterruptedException e) {
                    LOGGER.error("{}", e.getMessage());
                }
            }

        }).start();

    }
}
