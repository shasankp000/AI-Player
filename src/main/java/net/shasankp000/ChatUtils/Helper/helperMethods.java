package net.shasankp000.ChatUtils.Helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class helperMethods {
    private static final DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434/");
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

    public static boolean isRecentTimestamp(String timestamp, String currentDateTime) {
        LocalDateTime conversationTime = LocalDateTime.parse(timestamp, formatter2);
        LocalDateTime currentTime = LocalDateTime.parse(currentDateTime, formatter1);

        // Within the last hour
        return conversationTime.isAfter(currentTime.minusHours(1));
    }

    public static double findMaxSimilarityConversations(Set<RAGImplementation.Conversation> conversationSet) {
        double maxSimilarity = Double.MIN_VALUE;

        for (RAGImplementation.Conversation conversation : conversationSet) {
            if (conversation.similarity > maxSimilarity) {
                maxSimilarity = conversation.similarity;
            }
        }

        return maxSimilarity;
    }

    public static double findMaxSimilarityEvents(Set<RAGImplementation.Event> eventSet) {
        double maxSimilarity = Double.MIN_VALUE;

        for (RAGImplementation.Event event : eventSet) {
            if (event.similarity > maxSimilarity) {
                maxSimilarity = event.similarity;
            }
        }

        return maxSimilarity;
    }


    public static boolean isHighSimilarity(double similarityScore, double maxSimilarity) {
        return similarityScore == maxSimilarity;
    }

    public static String getCurrentDateAndTime() {

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        return dtf.format(now);

    }

    public static List<String> createQueries(String prompt) {

        String query_msg = "You are a first-principles reasoning search query AI agent. Your task is to generate a list of queries to search an embeddings database for relevant data. The output must be a valid Python list of strings in JSON format. Here are a few examples of input strings you may receive:\n" +
                """
                 "How can I build an automatic farm in Minecraft?",
                 "What were the resources needed for the enchantment table?",
                 "Tell me about the mining strategy we discussed last time."
                 "Did you go somewhere recently?"
                 "What ores did you mine recently?"
                 "Please go to coordinates 10 20 30."
                 "Please be on the lookout for hostile mobs nearby."
                
                \n""" +
                "And here are examples of the format your output must follow:\n" +
                "[\"What are the steps to build an automatic farm in Minecraft?\", \"What resources were listed for creating an enchantment table?\", \"What mining strategy was discussed on yyyy/mm/dd hh:mm:ss?\", \"Where did the bot go to recently?\", \"What ores did the bot mine recently?\"]\n" +

                "Please remember that it is absolutely crucial that you generate queries relevant to the user prompts. When dealing with responses that contain coordinates or three sets of numbers, keep the numbers exactly as they are in your queries, DO NOT ALTER THEM" +

                "The output must be a single Python list of strings in JSON format, with no additional explanations or syntax errors. The queries must be directly related to the user's input. If you receive a date or timestamp, format it as 'yyyy/mm/dd hh:mm:ss'. Do not generate anything other than the list.";


        // + "\n Especially explaining your response like \" Here are some Python list queries to search the embeddings database for necessary information to correctly respond to the prompt: \". Such responses should not be in, before or after the final output of the list of queries";


        List<Map<String, String>> queryConvo = new ArrayList<>();
        Map<String, String> queryMap1 = new HashMap<>();

        List<String> vectorDBQueries = new ArrayList<>();

        queryMap1.put("role", "system");
        queryMap1.put("content", query_msg);

        queryConvo.add(queryMap1);

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("llama3.2:latest");
        OllamaChatRequestModel requestModel1 = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, queryConvo.toString())
                .withMessage(OllamaChatMessageRole.USER, prompt)
                .build();

        String response = "";

        try {
            OllamaChatResult chatResult1 = ollamaAPI.chat(requestModel1);

            response = chatResult1.getResponse();


            int startIndex = response.indexOf('[');
            int endIndex = response.lastIndexOf(']') + 1;

            if (startIndex != -1 && endIndex != -1) {
                String listString = response.substring(startIndex, endIndex);

                ObjectMapper objectMapper = new ObjectMapper();
                vectorDBQueries = objectMapper.readValue(listString, new TypeReference<List<String>>() {
                });

                return vectorDBQueries;
            }

        } catch (OllamaBaseException | IOException | InterruptedException e) {
            LOGGER.error("Caught exception while creating queries: {} ", (Object) e.getStackTrace());
            System.out.println(response);
            throw new RuntimeException(e);
        }

        return vectorDBQueries;
    }

//    public static boolean classify_conversations(String DateTime, String prompt, String retrieved_context) {
//
//        boolean isRelevant = false;
//
//        String sys_prompt = """
//                You are an conversation classification AI agent within the context of minecraft. Your inputs will be a prompt, the current date and time when the prompt was asked and a chunk of text that has the following parameters: \n
//                1. ID: This is just an ID pertaining to it's order in the database. If the id's number is small then it refers to early conversations.
//                2. Timestamp: This is the timestamp at which each conversation is recorded. This is useful for analysis if the current user prompt asks something related to "most recent conversation" or a conversation on a specific date.
//                3. Prompt: The question asked/statement made by the user.
//                4. Response: The response made by the language model in use at that time of recording the conversation.
//                5. Similarity: The similarity score which is obtained after a vector similarity calculation. The closer it is to 1, the better the chances of the conversation being similar to the current user input.
//              \n
//               \n
//               You will not respond as an AI assistant. You only respond "yes" or "no". \n
//               \n
//                Determine whether the context contains data that directly is related to the search query. \n
//               \n
//                If the context is seemingly exactly what the search query needs, respond "yes" if it is anything but directly 'related respond "no". Do not respond "yes" unless the content is highly relevant to the search query. \n
//               \n
//                Here's an example of the prompts you may receive: \n
//               \n
//               Example 1: Based on everything that you know about me so far, tell me about myself. \n
//               Example 2: What is the derivative of x^y with respect to y? \n
//               Example 3: What did we discuss last Tuesday? \n
//               Example 4: What is the weather in Bengaluru right now? \n
//               Example 5: How can I craft a shield? \n
//               Example 6: Tell me the recipe for the fire resistance potion. \n
//               Example 7: What is the best way to raid an ocean monument? \n
//               \n
//               And here's the type of context data you will receive: \n
//              \n
//               "\\n \\"ID: 1\\""
//               "\\n \\"Timestamp: 2024-xx-xx 23:35:36 \\""
//               "\\n \\"Prompt: Hi there! My name is John Doe. How is your day today?\\""
//               "\\n \\"Response: Hi John! I'm doing well, thank you for asking! It's great to be chatting with you again. Can you tell me more about what brings you here today? Are you working on a specific project or just looking for some general information?" +
//               "\\n \\"Similarity: 0.40820406249846664\\""
//
//               "\\n \\"ID: 2 \\""
//               "\\n \\"Timestamp: 2024-xx-xx 00:35:36\\""
//               "\\n \\"Prompt: How can I craft a shield?\\""
//               "\\n \\"Response: To craft a shield, you'll need six planks of wood and one iron ingot. Arrange the planks in a Y shape with the iron ingot in the top-center slot."
//               "\\n \\"Similarity: 0.76855590124578\\""
//              \n
//           \n
//           Sometimes you might end up receiving this in your context data:
//           \n
//           \\n \\"ID: 1\\""
//           "\\n \\"Timestamp: 2024-xx-xx 23:35:36 \\""
//           "\\n \\"Prompt: null\\""
//           "\\n \\"Response: Hi John! I'm doing well, thank you for asking! It's great to be chatting with you again. Can you tell me more about what brings you here today? Are you working on a specific project or just looking for some general information?" +
//           "\\n \\"Similarity: 0.0\\""
//           \s
//           When you receive a context with an exact similarity of 0.0, it is the first response you ever said when you were first called, and you must analyse the response part of it according to the user prompt and return it if it is relevant according to the prompt.
//           \s
//           \s
//              Note how in this example the prompts and context are completely unrelated. That doesn't mean this will always be the case. You must analyse both the prompts and context data properly and return only a single word answer. Yes or No, irrespective of case. \n
//             \n
//              If you receive no such data, then it means there is no "probable relevant data" to classify. In that case simply say No, irrespective of case.
//             \n
//      \s
//           \s""";
//
//        String userEnd = "This is the user prompt: " + prompt;
//        String contextData = "This is the context data from the database: " + "\n" + retrieved_context;
//
//        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.GEMMA2);
//        OllamaChatRequestModel requestModel2 = builder
//                .withMessage(OllamaChatMessageRole.SYSTEM, sys_prompt)
//                .withMessage(OllamaChatMessageRole.USER, userEnd)
//                .withMessage(OllamaChatMessageRole.USER, contextData)
//                .withMessage(OllamaChatMessageRole.USER, "Current date and time: " + DateTime)
//                .build();
//
//        String response;
//
//
//        try {
//            OllamaChatResult chatResult1 = ollamaAPI.chat(requestModel2);
//
//            response = chatResult1.getResponse();
//
//            System.out.println("Conversation classifier: " + "\n" + response);
//
//            if (response.equalsIgnoreCase("yes") || response.startsWith("Yes") || response.startsWith("yes") || response.contains("Yes") || response.contains("yes")) {
//
//                isRelevant = true;
//
//            }
//
//
//        } catch (OllamaBaseException | IOException | InterruptedException e) {
//            LOGGER.error("Caught new exception while classifying conversations: {}", e.getMessage());
//            throw new RuntimeException(e);
//        }
//
//
//        return isRelevant;
//    }

    public static String classify_queries(List<String> QueryList, String prompt) {

        String tableType;

        String sys_prompt = """
                          \n
                          You are an query classification AI agent within the context of minecraft. Your inputs will be the user input and a list of strings, which are queries generated by a language model based on a user input. \n
                          \n
                          Here are some example user prompts you may receive:
                          \n
                           Example 1: Based on everything that you know about me so far, tell me about myself. \n
                           Example 2: What is the derivative of x^y with respect to y? \n
                           Example 3: What did we discuss last Tuesday? \n
                           Example 4: What is the weather in Bengaluru right now? \n
                           Example 5: How can I craft a shield? \n
                           Example 6: Tell me the recipe for the fire resistance potion. \n
                           Example 7: What is the best way to raid an ocean monument? \n
                          \n
                          \n
                          Here is an example list of example queries you may receive:
                          \n
                          "[\\"What are the steps to build an automatic farm in Minecraft?\\", \\"What resources were listed for creating an enchantment table?\\", \\"What mining strategy was discussed on yyyy/mm/dd hh:mm:ss?\\", \\"Where did the bot go to recently?\\", \\"What ores did the bot mine recently?\\"]\\n"
                          You will classify the queries into two types: "conversations" or "events" by analyzing the user prompt to see if the user asked anything that could lead to a past conversation, or is making a new conversation, or is asking about a past event, or is triggering a new event.
                          \n
                          Events include activities like going, moving, exploration, mining, scouting/searching, attacking, crafting etc.
                          \n
                         \n
                         You will not respond as an AI assistant. You only respond "event" or "conversation", irrespective of case. \n
                         REMEMBER TO ONLY RESPOND AS "event" OR "conversation". DO NOT RESPOND IN ANY OTHER WORD.
                         \s
                \s
                     \s""";

        String userEnd = "This is the user prompt: " + prompt;
        String queryData = "This is the generated query list: " + "\n" + QueryList.toString();

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("llama3.2:latest");
        OllamaChatRequestModel requestModel2 = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, sys_prompt)
                .withMessage(OllamaChatMessageRole.USER, userEnd)
                .withMessage(OllamaChatMessageRole.USER, queryData)
                .build();

        String response;


        try {
            OllamaChatResult chatResult1 = ollamaAPI.chat(requestModel2);

            response = chatResult1.getResponse();

            System.out.println("Event classifier: " + "\n" + response);

            if (response.equalsIgnoreCase("conversation") || response.startsWith("Conversation") || response.startsWith("conversation") || response.contains("conversation") || response.contains("Conversation")) {

                tableType = "conversations";

            }

            else {

                tableType = "events";
            }


        } catch (OllamaBaseException | IOException | InterruptedException e) {
            LOGGER.error("Caught new exception while classifying queries: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        return tableType;
    }


    public static boolean classify_events(String DateTime, String prompt, String retrieved_context) {

        boolean isRelevant = false;

        String sys_prompt = """
                You are an event classification AI agent within the context of minecraft. Your inputs will be a prompt, the current date and time when the prompt was asked and a chunk of text that has the following parameters: \n
                1. ID: This is just an ID pertaining to it's order in the database. If the id's number is small then it refers to early conversations.
                2. Timestamp: This is the timestamp at which each conversation is recorded. This is useful for analysis if the current user prompt asks something related to "most recent conversation" or a conversation on a specific date.
                3. Event: The question asked/statement made by the user.
                4. Event Context: The event context generated by the language model in use at that time of recording the conversation.
                5. Event Result: The event result which was received when the method pertaining to that event was called and executed.
                6. Similarity: The similarity score which is obtained after a vector similarity calculation. The closer it is to 1, the better the chances of the conversation being similar to the current user input.
              \n
               \n
               You will not respond as an AI assistant. You only respond "yes" or "no". \n
               \n
                Determine whether the context contains data that directly is related to the search query. \n
               \n
                If the context is seemingly exactly what the search query needs, respond "yes" if it is anything but directly 'related respond "no". Do not respond "yes" unless the content is highly relevant to the search query. \n
               \n
                Here's an example of the prompts you may receive: \n
               \n
               Example 1: Could you please go to coordinates 10 -60 11? \n
               Example 2: Please check if there is a block in front of you. \n
               Example 3: Please scout your surroundings for zombies nearby. \n
               Example 4: Could please mine some iron for me? \n
               Example 5: Please chop some wood. \n
               Example 6: Please harvest some crops. \n
               \n
               And here's the type of context data you will receive: \n
              \n
               "\\n \\"ID: 1\\""
               "\\n \\Timestamp: 2024-xx-xx 20:35:36\\""
               "\\n \\"Event: Could you please go to coordinates 10 -60 11?\\""
               "\\n \\"Event Context: The player asked you to go to coordinates 10 -60 11. You must analyze your surroundings carefully and chart a course to the coordinates.\\""
               "\\n \\"Event Result: The bot has reached target position.\\""
               "\\n \\"Similarity: 0.40820406249846664\\""
      
               "\\n \\"ID: 2 \\""
               "\\n \\Timestamp: 2024-xx-xx 21:35:36\\"
               "\\n \\"Event: Please scout your surroundings for zombies nearby.\\""
               "\\n \\"Event Context: The player asked you to search for hostile mobs around you, and to report to the player if you find any such hostile mob.\\""
               "\\n \\"Event Result: The bot found a zombie nearby."
               "\\n \\"Similarity: 0.76855590124578\\""
              \n
           \n
              Note how in this example the prompts and context are completely unrelated. That doesn't mean this will always be the case. You must analyse both the prompts and context data properly and return only a single word answer. Yes or No, irrespective of case. \n
             \n
              If you receive no such data, then it means there is no "probable relevant data" to classify. In that case simply say No, irrespective of case.
             \n
      \s
           \s""";

        String userEnd = "This is the user prompt: " + prompt;
        String contextData = "This is the context data from the database: " + "\n" + retrieved_context;

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("llama3.2:latest");
        OllamaChatRequestModel requestModel2 = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, sys_prompt)
                .withMessage(OllamaChatMessageRole.USER, userEnd)
                .withMessage(OllamaChatMessageRole.USER, contextData)
                .withMessage(OllamaChatMessageRole.USER, "Current date and time: " + DateTime)
                .build();

        String response;


        try {
            OllamaChatResult chatResult1 = ollamaAPI.chat(requestModel2);

            response = chatResult1.getResponse();

            System.out.println("Event classifier: " + "\n" + response);

            if (response.equalsIgnoreCase("yes") || response.startsWith("Yes") || response.startsWith("yes") || response.contains("Yes") || response.contains("yes")) {

                isRelevant = true;

            }


        } catch (OllamaBaseException | IOException | InterruptedException e) {
            LOGGER.error("Caught new exception while classifying events: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        return isRelevant;
    }



}
