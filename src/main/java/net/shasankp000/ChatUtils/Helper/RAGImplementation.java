package net.shasankp000.ChatUtils.Helper;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.Database.SQLiteDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.shasankp000.ChatUtils.Helper.helperMethods.*;
import static net.shasankp000.ChatUtils.Helper.helperMethods.classify_events;

public class RAGImplementation {
    private static final Logger logger = LoggerFactory.getLogger("ai-player");
    private static final String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
    private static final String host = "http://localhost:11434";
    private static final OllamaAPI ollamaAPI = new OllamaAPI(host);

    public static class Conversation {
        // DS for the SQL return type.
        public int id;
        public String timestamp;
        public String prompt;
        public String response;
        public List<Double> promptEmbedding;
        public List<Double> responseEmbedding;
        public double similarity;

        Conversation(int id, String timestamp ,String prompt, String response, List<Double> promptEmbedding, List<Double> responseEmbedding) {
            this.id = id;
            this.timestamp = timestamp;
            this.prompt = prompt;
            this.response = response;
            this.promptEmbedding = promptEmbedding;
            this.responseEmbedding = responseEmbedding;
        }
    }

    public static class Event {
        // DS for the SQL return type.
        public int id;
        public String timestamp;
        public String event;
        public String event_context;
        public String event_result;
        public List<Double> eventEmbedding;
        public List<Double> eventContextEmbedding;
        public List<Double> eventResultEmbedding;
        public double similarity;

        Event(int id, String timestamp, String event, String event_context, String event_result, List<Double> eventEmbedding, List<Double> eventContextEmbedding, List<Double> eventResultEmbedding) {

            this.id = id;
            this.timestamp = timestamp;
            this.event = event;
            this.event_context = event_context;
            this.event_result = event_result;
            this.eventEmbedding = eventEmbedding;
            this.eventContextEmbedding = eventContextEmbedding;
            this.eventResultEmbedding = eventResultEmbedding;

        }


    }


    public static class InitialResponse {
        // DS for SQL return type.

        public int id;
        public String Timestamp;
        public String response;

        InitialResponse(int id, String timestamp, String response) {

            this.id = id;
            this.Timestamp = timestamp;
            this.response = response;

        }


    }


    private static List<Double> parseEmbedding(String embeddingString) {
        List<Double> embedding = new ArrayList<>();
        if (embeddingString != null) {
            String[] parts = embeddingString.split(",");
            for (String part : parts) {
                embedding.add(Double.parseDouble(part));
            }
        }
        return embedding;
    }


    public static List<Conversation> findRelevantConversations(List<Double> queryEmbedding, List<Conversation> conversations, int topN) {
        for (Conversation conv : conversations) {
            double promptSimilarity = calculateCosineSimilarity(queryEmbedding, conv.promptEmbedding);
            double responseSimilarity = calculateCosineSimilarity(queryEmbedding, conv.responseEmbedding);
            conv.similarity = (promptSimilarity + responseSimilarity) / 2; // Average similarity
        }
        conversations.sort((c1, c2) -> Double.compare(c2.similarity, c1.similarity)); // Sort in descending order
        return conversations.subList(0, Math.min(topN, conversations.size()));
    }

    public static List<Event> findRelevantEvents(List<Double> queryEmbedding, List<Event> events, int topN) {
        for (Event event : events) {
            double eventSimilarity = calculateCosineSimilarity(queryEmbedding, event.eventEmbedding);
            double eventContextSimilarity = calculateCosineSimilarity(queryEmbedding, event.eventContextEmbedding);
            double eventResultSimilarity = calculateCosineSimilarity(queryEmbedding, event.eventResultEmbedding);
            event.similarity = (eventSimilarity + eventContextSimilarity + eventResultSimilarity) / 3; // Average similarity
        }
        events.sort((c1, c2) -> Double.compare(c2.similarity, c1.similarity)); // Sort in descending order
        return events.subList(0, Math.min(topN, events.size()));
    }



    // Cosine similarity where if the angle between two vectors overlap, they are similar (angle = 0)
    // If angle is 90 then the vectors are dissimilar.

    // cos_sim(x,y) = [(x.y) / [|x| . |y|]]

    public static double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size()) {
            LOGGER.warn("Vectors are not of same length, possible initial response in the data.");
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += Math.pow(vec1.get(i), 2);
            norm2 += Math.pow(vec2.get(i), 2);
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public static List<InitialResponse> fetchInitialResponse() throws SQLException {
        // fetch all conversations from the database.

        String DB_URL = "jdbc:sqlite:" + "./sqlite_databases/" + "memory_agent.db";

        List<InitialResponse> initialResponse = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, timestamp, response FROM conversations WHERE id = 1")) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String timeStamp = resultSet.getString("timestamp");
                String response = resultSet.getString("response");

                initialResponse.add(new InitialResponse(id, timeStamp, response));
            }
        }
        catch (Exception e) {
            logger.error("Caught exception: {}", e.getMessage());
        }

        return initialResponse;
    }

    public static List<Conversation> fetchConversations() throws SQLException {
        // fetch all conversations from the database.

        String DB_URL = "jdbc:sqlite:" + "./sqlite_databases/" + "memory_agent.db";

        List<Conversation> conversations = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, timestamp, prompt, response, prompt_embedding, response_embedding FROM conversations")) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String timeStamp = resultSet.getString("timestamp");
                String prompt = resultSet.getString("prompt");
                String response = resultSet.getString("response");
                String promptEmbeddingString = resultSet.getString("prompt_embedding");
                String responseEmbeddingString = resultSet.getString("response_embedding");
                List<Double> promptEmbedding = parseEmbedding(promptEmbeddingString);
                List<Double> responseEmbedding = parseEmbedding(responseEmbeddingString);

                conversations.add(new Conversation(id, timeStamp ,prompt, response, promptEmbedding, responseEmbedding));
            }
        }
        catch (Exception e) {
            logger.error("Caught exception while fetching conversation data: {}", e.getMessage());
        }

        return conversations;
    }

    private static String buildSystemPrompt() {
        return """
                
                You are an AI agent who specializes in Retrieval Augmented Generation (or RAG) with the context of minecraft.
                
                You are capable of having memory of every conversation you have ever had with this user/ every event triggered from such conversation. On every prompt from the user, the system will check for any relevant messages you have had with the user. If any embedded previous conversations are attached, use them for context to respond to the user, if the context is relevant and useful to responding. If the recalled conversations are irrelevant or if there are no previous conversations at all, disregard speaking about them and respond normally as an AI assistant. Do not talk about recalling conversations. Just use any useful data from the previous conversations and respond normally as an intelligent AI assistant.
                
                You are acting as a helper agent for a main agent who is based in minecraft, so DO NOT INTRODUCE YOURSELF.
                
                Here is an EXAMPLE of the type of context data which you will receive:
                
                EXAMPLE 1:
                
                ID: 1
                Timestamp: 2024-xx-xx 22:21:34
                Prompt: "What is the best way to build a farm in Minecraft?"
                Response: "The best way to build a farm in Minecraft is to start by choosing a flat area of land. Use fences to keep mobs out and plant crops like wheat or carrots. Make sure to place water nearby to keep the soil hydrated."
                Similarity: 0.765342
                
                
                EXAMPLE 2:
                
                ID: 2
                Timestamp: 2024-xx-xx 22:30:45
                Prompt: "How do I craft a shield?"
                Response: "To craft a shield, you'll need six planks of wood and one iron ingot. Arrange the planks in a Y shape with the iron ingot in the top-center slot."
                Similarity: 0.892349
                
                EXAMPLE: 3
                
                ID: 3
                Timestamp: 2024-xx-xx 23:15:36
                Event: "Could you please go to coordinates <x> <y> <z>"
                Event Context: "The player asked you go to coordinates <x> <y> <z>."
                Event Result: "The bot (you) reached the target coordinates successfully" or "The bot has reached target position."
                Similarity: 0.75678
                
                EXAMPLE: 4
                
                ID: 4
                Timestamp: 2024-xx-xx 00:35:36
                Event: "Could you check if there is a block in front of you?"
                Event Context: "The player asked you to check if there any block in front of you."
                Event Result: "The bot(you) detected that there is indeed a block in front of you."
                Similarity: 0.75678
                
                What the properties of the examples mean:
                
                1 Timestamp: The date and time when the player asked you something/ or to do something.
                2. Prompt : The question which the user asked you.
                 (Alternate case) Event: The event which triggered based on the player's question.
                3. Response: The response you provided.
                  (Alternate case) Event Context: The context of the event which the player wanted to happen.
                4. (In case of event) Event Result: The outcome of the event that took place.
                5. Similarity: This is a similarity score which relates to the similarity between the context you receive and the player's prompt. The closer the similarity number is to 1, the higher the similarity it is.
              
                Alternatively sometimes during the cases of types of examples 1 and 2 you may receive this exact current prompt with a response which greets the player. If you find any context which pertains to that, remember that those prompts are simply different greeting messages every time you joined the game.
                
                The given data is just an EXAMPLE. DO NOT use any reference to it during conversations where context is not adequate or is missing. You must just learn the type of context data accordingly to reply to the user's input.
              
                Remember that when you respond with the context data, you MUST DO SO in the PAST TENSE, since the context data is also a recording of your past actions.
                
                And it goes without saying that you have to analyse the context data and find it's relevancy to the player's prompt and only answer what you find relevant among the context data.
                
                DO NOT MAKE UP RESPONSES WHICH DO NOT EXIST IN THE CONTEXT DATA. USE ONLY THE CONTEXT DATA FOR YOUR RESPONSES. NO OTHER INFORMATION MUST BE USED AT ALL.
                
                If you find this message instead: "No relevant data found from database. Analyze current user input and answer accordingly."
               
                Then this means that based on the current user input, no relevant data was found on the database. So you must analyze the current user input and answer accordingly. Be it providing an answer to a new question or solving a problem in the game.
                
                You will also be given the current date and time at which the user asks the current question to you. Use this information for sorting through the context data for the most recent conversation or if the user asks you a question about a topic from a specific date.
               
                """;

    }

    private static String recall(String dateTime, String playerMessage) throws SQLException {

        System.out.println("Recalling from memory.....");

        String tableType;

        String finalRelevantConversationString = "";

        List<String> queryList = createQueries(playerMessage);

        System.out.println("Query List: " + queryList.toString());

        tableType = classify_queries(queryList, playerMessage);

        if(tableType.equalsIgnoreCase("conversation") || tableType.startsWith("Conversation") || tableType.startsWith("conversation") || tableType.contains("conversation") || tableType.contains("Conversation")) {


            List<RAGImplementation.Conversation> conversationList = RAGImplementation.fetchConversations();
            Set<Conversation> relevantConversationSet = new HashSet<>();

            List<Double> embedding;

            for (String query : queryList) {
                try {
                    embedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, query);
                    List<RAGImplementation.Conversation> relevantConversations = RAGImplementation.findRelevantConversations(embedding, conversationList, 2);
                    relevantConversationSet.addAll(relevantConversations);
                } catch (IOException | InterruptedException | OllamaBaseException e) {
                    LOGGER.error("Caught new exception in fetching relevant conversations: {}", (Object) e.getStackTrace());
                    throw new RuntimeException(e);
                }
            }


            StringBuilder relevantConversationString = new StringBuilder();

            double maxSimilarity = findMaxSimilarityConversations(relevantConversationSet);

            for (RAGImplementation.Conversation conversation : relevantConversationSet) {
                boolean isRecent = isRecentTimestamp(conversation.timestamp, dateTime);
                boolean isHighSimilarity = isHighSimilarity(conversation.similarity, maxSimilarity);

                // Consider the conversation relevant if it's recent and has the highest similarity,
                // or if it's not recent but has the highest similarity
                if ((isRecent && isHighSimilarity) || (!isRecent && isHighSimilarity)) {
                    relevantConversationString.append("ID: ").append(conversation.id).append("\n");
                    relevantConversationString.append("Timestamp: ").append(conversation.timestamp).append("\n");
                    relevantConversationString.append("Prompt: ").append(conversation.prompt).append("\n");
                    relevantConversationString.append("Response: ").append(conversation.response).append("\n");
                    relevantConversationString.append("Similarity: ").append(conversation.similarity).append("\n");
                }

                else {
                    relevantConversationString.append("Irrelevant");
                }

            }

            if (!relevantConversationString.toString().equals("Irrelevant")) {

                finalRelevantConversationString = relevantConversationString.toString();

            }

            else {

                finalRelevantConversationString = "No relevant data found from database. Analyze current user input and answer accordingly.";
            }

        }

        else {
            List<RAGImplementation.Event> eventList = RAGImplementation.fetchEvents();
            Set<RAGImplementation.Event> relevantEventSet = new HashSet<>();

            List<Double> embedding;

            for (String query : queryList) {


                try {
                    embedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, query);
                    List<RAGImplementation.Event> relevantEvents = RAGImplementation.findRelevantEvents(embedding, eventList, 2);
                    relevantEventSet.addAll(relevantEvents);
                } catch (IOException | InterruptedException | OllamaBaseException e) {
                    LOGGER.error("Caught new exception in fetching relevant events: {}", (Object) e.getStackTrace());
                    throw new RuntimeException(e);
                }
            }

            StringBuilder relevantEventString = new StringBuilder();

            double maxSimilarity = findMaxSimilarityEvents(relevantEventSet);

            for (RAGImplementation.Event event : relevantEventSet) {
                boolean isRecent = isRecentTimestamp(event.timestamp, dateTime);
                boolean isHighSimilarity = isHighSimilarity(event.similarity, maxSimilarity);

                // Consider the conversation relevant if it's recent and has the highest similarity,
                // or if it's not recent but has the highest similarity
                if ((isRecent && isHighSimilarity) || (!isRecent && isHighSimilarity)) {
                    relevantEventString.append("ID: ").append(event.id).append("\n");
                    relevantEventString.append("Timestamp: ").append(event.timestamp).append("\n");
                    relevantEventString.append("Event: ").append(event.event).append("\n");
                    relevantEventString.append("Event Context: ").append(event.event_context).append("\n");
                    relevantEventString.append("Event Result: ").append(event.event_result).append("\n");
                    relevantEventString.append("Similarity: ").append(event.similarity).append("\n");
                }
            }

            if (classify_events(dateTime, playerMessage, relevantEventString.toString())) {

                finalRelevantConversationString = relevantEventString.toString();
            }

            else {

                finalRelevantConversationString = "No relevant data found from database. Analyze current user input and answer accordingly.";

            }


        }


        return finalRelevantConversationString;

    }


    public static void runRagTask(String dateTime, String PlayerMessage, ServerCommandSource botSource) {

        String relevantContext = "";

        String DB_URL = "jdbc:sqlite:" + "./sqlite_databases/" + "memory_agent.db";

        try {
            relevantContext = recall(dateTime, PlayerMessage);
        } catch (SQLException e) {
            logger.error("SQL Exception occurred: {}", e.getMessage() );
        }

        String systemPrompt = buildSystemPrompt();

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.GEMMA2);

        OllamaChatRequestModel chatRequestModel = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, systemPrompt)
                .withMessage(OllamaChatMessageRole.USER, "User prompt: " + PlayerMessage)
                .withMessage(OllamaChatMessageRole.USER, "Context data from database: " + relevantContext)
                .withMessage(OllamaChatMessageRole.USER, "Current date and time: " + dateTime)
                .build();

        OllamaChatResult chatResult = null;
        String response;

        try {

             chatResult = ollamaAPI.chat(chatRequestModel);
             response = chatResult.getResponse();

            ChatUtils.sendChatMessages(botSource, response);


            List<Double> promptEmbedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, PlayerMessage);
            List<Double> responseEmbedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, response);

            SQLiteDB.storeConversationWithEmbedding(DB_URL, PlayerMessage, response, promptEmbedding, responseEmbedding);


        } catch (OllamaBaseException | IOException | InterruptedException | SQLException e) {
            LOGGER.error("Caught new exception while running RAG task: {}", e.getMessage());
            throw new RuntimeException(e);
        }

    }



    public static List<Event> fetchEvents() throws SQLException {

        String DB_URL = "jdbc:sqlite:" + gameDir + "/sqlite_databases/memory_agent.db";

        List<Event> events = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, timestamp, event, event_context, event_result, event_embedding, event_context_embedding, event_result_embedding FROM events")) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String timeStamp = resultSet.getString("timestamp");
                String event = resultSet.getString("event");
                String event_context = resultSet.getString("event_context");
                String event_result = resultSet.getString("event_result");
                String eventEmbeddingString = resultSet.getString("event_embedding");
                String eventContextEmbeddingString = resultSet.getString("event_context_embedding");
                String eventResultEmbeddingString = resultSet.getString("event_result_embedding");
                List<Double> eventEmbedding = parseEmbedding(eventEmbeddingString);
                List<Double> eventContextEmbedding = parseEmbedding(eventContextEmbeddingString);
                List<Double> eventResultEmbedding = parseEmbedding(eventResultEmbeddingString);

                events.add(new Event(id, timeStamp ,event, event_context, event_result,eventEmbedding, eventContextEmbedding, eventResultEmbedding));
            }
        }
        catch (Exception e) {
            logger.error("Caught exception while fetching event data: {}", e.getMessage());
        }

        return events;
    }


}
