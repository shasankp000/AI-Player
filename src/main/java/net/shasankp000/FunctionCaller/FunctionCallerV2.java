// Kudos to this guy, Matt Williams, https://www.youtube.com/watch?v=IdPdwQdM9lA, for opening my eyes on function calling.

package net.shasankp000.FunctionCaller;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;

import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;


import net.minecraft.server.command.ServerCommandSource;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.PathFinding.GoTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.shasankp000.ChatUtils.Helper.JsonUtils.cleanJsonString;
import net.shasankp000.Entity.RayCasting;


public class FunctionCallerV2 {

    private static final Logger logger = LoggerFactory.getLogger("ai-player");
    private static ServerCommandSource botSource = null;

    private static final String DB_URL = "jdbc:sqlite:" + "./sqlite_databases/" + "memory_agent.db";
    private static final String host = "http://localhost:11434/";
    private static final OllamaAPI ollamaAPI = new OllamaAPI(host);
    private static volatile String functionOutput = null;


    public FunctionCallerV2(ServerCommandSource botSource) {

        FunctionCallerV2.botSource = botSource;
        ollamaAPI.setRequestTimeoutSeconds(90);

    }

    private static class ExecutionRecord {
        String timestamp;
        String command;
        List<Double> eventEmbedding;
        List<Double> eventContextEmbedding;
        List<Double> eventResultEmbedding;
        String result;
        String context;

        private ExecutionRecord(String Timestamp, String command, String context, String result, List<Double> eventEmbedding, List<Double> eventContextEmbedding, List<Double> eventResultEmbedding) {
            this.context = context;
            this.timestamp = Timestamp;
            this.command = command;
            this.eventEmbedding = eventEmbedding;
            this.eventContextEmbedding = eventContextEmbedding;
            this.eventResultEmbedding = eventResultEmbedding;
            this.result = result;
        }

        private void updateRecords() {

            try {
                SQLiteDB.storeEventWithEmbedding(DB_URL, this.command, this.context, this.result, this.eventEmbedding, this.eventContextEmbedding, this.eventResultEmbedding);
            } catch (SQLException e) {
                logger.error("Caught exception: {} ", (Object) e.getStackTrace());
                throw new RuntimeException(e);
            }


        }

    }

    private static String getCurrentDateandTime() {

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        return dtf.format(now);

    }

    private static void getMovementOutput(String movementMethod) {

        functionOutput = String.valueOf(movementMethod);

    }

    private static void getBlockCheckOutput(String blockCheckMethod) {

        functionOutput = String.valueOf(blockCheckMethod);

    }


    private static class Tools {

        private static void goTo(int x, int y, int z) {

            System.out.println("Going to coordinates " + x + " " + y + " " + z);


            if (botSource == null) {

                System.out.println("Bot not found.");
            }

            else {
                getMovementOutput(GoTo.goTo(botSource, x, y, z));
            }


        }

        private static void searchBlock(String direction) {
            switch (direction) {
                case "front" -> {

                    System.out.println("Checking for block in front");

                    getBlockCheckOutput(RayCasting.detect(botSource.getPlayer()));
                }
                case "behind" -> {
                    System.out.println("Rotating..");
                    System.out.println("Checking for block behind");
                }
                case "left" -> {
                    System.out.println("Rotating..");
                    System.out.println("Checking for block in left");
                }
                case "right" -> {
                    System.out.println("Rotating..");
                    System.out.println("Checking for block in right");
                }
            }
        }

    }

    private static String toolBuilder() {

        List<Map<String, Object>> functions = new ArrayList<>();

        functions.add(buildFunction("goTo", "Move to a specific set of x y z coordinates", List.of(
                buildParameter("x", "The x-axis coordinate"),
                buildParameter("y", "The y-axis coordinate"),
                buildParameter("z", "The z-axis coordinate")
        )));

        functions.add(buildFunction("searchBlock", "Check or search for a block in a particular direction", List.of(
                buildParameter("direction", "The direction to check (front, behind, left, right)")
        )));

        Map<String, Object> toolMap = new HashMap<>();
        toolMap.put("functions", functions);

        return new Gson().toJson(toolMap);
    }

    private static Map<String, Object> buildFunction(String name, String description, List<Map<String, Object>> parameters) {
        Map<String, Object> function = new HashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        return function;
    }

    private static Map<String, Object> buildParameter(String name, String description) {
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("name", name);
        parameter.put("description", description);
        parameter.put("required", true);
        return parameter;
    }

    // This code right here is pure EUREKA moment.

    private static String buildPrompt(String toolString) {
        return "You are a first-principles reasoning function caller AI agent that takes a question/user prompt from a minecraft player and finds " +
                "the most appropriate tool or tools to execute, along with the " +
                "parameters required to run the tool in the context of minecraft. " +

                """
                Here are some example prompts that you may receive: \n
                        1. Could you check if there is a block in front of you? \n
                        2. Look around for any hostile mobs, and report to me if you find any. \n
                        3. Could you mine some stone and bring them to me? \n
                        4. Craft a set of iron armor. \n
                        \s
                        \s""" +

                """
                         A few more variations of the prompts may be: \n
                        
                         "Could you search for blocks in front of you?"
                         "Do you see if there is a block in front of you?"
                         "Can you mine some stone and bring them to me?
                        
                        "Please move to 10 -60 20." or "Please go to the coords 10 -60 20" or "Please go to 10 -60 20" and so on... \n
                        
                        PLEASE REMEMBER TO CORRECTLY ANALYZE THE PROMPT. You have a history of committing silly mistakes like producing a json output which calls the movement method when the user asks you to check something.
                        
                        To minimize such errors, you must focus on the keywords to look for in the user prompts and then match them against the tool names that you have been provided.
                        
                        Such keywords include:
                        
                        move
                        go
                        walk
                        run
                        navigate
                        travel
                        step
                        approach
                        advance
                        mine
                        dig
                        excavate
                        collect
                        gather
                        break
                        harvest
                        attack
                        fight
                        defend
                        slay
                        kill
                        vanquish
                        destroy
                        battle
                        craft
                        create
                        make
                        build
                        forge
                        assemble
                        trade
                        barter
                        exchange
                        buy
                        sell
                        explore
                        discover
                        find
                        search
                        locate
                        scout
                        construct
                        erect
                        place
                        set
                        farm
                        plant
                        grow
                        cultivate
                        use
                        utilize
                        activate
                        employ
                        operate
                        handle
                        check
                        search
                        
                        Some of the above keywords are synonyms of each other. (e.g check -> search, kill -> vanquish, gather->collect)
                        
                        So you must be on the lookout for the synonyms of such keywords as well.
                        
                        These keywords fall under the category of action-verbs. Since your purpose is to design the output that will call a function, which will trigger an action, you need to know what a verb is and what action-verbs are to further your ease in selecting the appropriate function.
                        
                        A verb is a a word used to describe an action, state, or occurrence, and forming the main part of the predicate of a sentence, such as hear, become, happen.
                        
                        An action verb (also called a dynamic verb) describes the action that the subject of the sentence performs (e.g., “I  run”).
                        
                        Example of action verbs:
                        
                        We "traveled" to Spain last summer.
                        My grandfather "walks" with a stick.
                        
                        The train "arrived" on time.
                        
                        I "ate" a sandwich for lunch.
                        
                        All the verbs within quotations cite actions that were caused/triggered.
                        
                        So when you are supplied with a prompt that contain the *keywords* which is provided earlier, know that these are actions which correspond to a particular tool within the provided tools.
                        
                        """ +


                "Respond as JSON using the " +
                "following schema: \n\n" +

                "{" +
                "\"functionName\": \"function name\", " +
                "\"parameters\": [" +
                        "{\"" +
                            "parameterName\": \"name of parameter\", " +
                            "\"parameterValue\": \"value of parameter\"" +
                        "}" +
                    "]" +
                "}" +

                "\nReturn the json with proper indentation so that there are no parsing errors. DO NOT modify the json field names. It is absolutely imperative that the field names are NOT MODIFIED. \n" +

                "The tools are: " + toolString +
                "\n While returning the json output, do not say anything else. By anything else, I mean any other word at all. \n" +
                "Do not worry about actually executing this function, that will be taken care of by another system by analyzing your JSON output. \n" +
                "Thus it is imperative that you output only the JSON, and nothing else. \n";
    }

    private static String generatePromptContext(String userPrompt) {

        String contextOutput = "";

        String sysPrompt = """
                 You are a context generation AI agent in terms of minecraft. \n
                 This means that you will have a prompt from a user, who is the player and you need to analyze the context of the player's prompts, i.e what the player means by the prompt. \n
                 This context information will then be used by a minecraft bot to understand what the user is trying to say. \n
                 \n
                 Here are some example player prompts you may receive: \n
                 1. Could you check if there is a block in front of you? \n
                 2. Look around for any hostile mobs, and report to me if you find any. \n
                 3. Could you mine some stone and bring them to me? \n
                 4. Craft a set of iron armor. \n
                 5. Please go to coordinates 10 -60 20. \n
                 \n
                 A few more variations of the prompts may be: \n
               
                  "Could you search for blocks in front of you?"
                  "Do you see if there is a block in front of you?"
                  "Can you mine some stone and bring them to me?
               
                 "Please move to 10 -60 20." or "Please go to the coords 10 -60 20" or "Please go to 10 -60 20" and so on... \n
               
               
                 \n
                 Here are some examples of the format in which you MUST answer.
                 \n
                 1. The player asked you to check whether there is a block in front of you or not. \n
                 2. The player asked you to search for hostile mobs around you, and to report to the player if you find any such hostile mob. \n
                 3. The player asked you to mine some stone and then bring the stone to the player. \n
                 4. The player asked you to craft a set of iron armor. \n
                 5. The player asked you to go to coordinates 10 -60 20. You followed the instructions and began movement to the coordinates. \n
               
               Remember that all the context you generate should be in the past tense, sense it is being recorded after the deed has been done.
               
               \n
                "Remember that when dealing with prompts that ask the bot to go to a specific set of x y z coordinates, you MUST NOT alter the coordinates, they SHOULD BE the exact same as in the prompt given by the player.
               \n
                Now,remember that you must only generate the context as stated in the examples, nothing more, nothing less. DO NOT add your own opinions/statements/thinking to the context you generate. \n
                Remember that if you generate incorrect context then the bot will not be able to understand what the user has asked of it.
               
               \s
               \s""";


        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.LLAMA2);

        try {

            OllamaChatRequestModel requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, sysPrompt)
                    .withMessage(OllamaChatMessageRole.USER, "Player prompt: " + userPrompt)
                    .build();

            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            contextOutput = chatResult.getResponse();



        } catch (OllamaBaseException | IOException | InterruptedException | JsonSyntaxException e) {
            logger.error("{}", (Object) e.getStackTrace());
        }

        return contextOutput;
    }


    public static void run(String userPrompt) {

        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.GEMMA2); // LLAMA2 is surprisingly much less error-prone compared to phi3.

        String systemPrompt = FunctionCallerV2.buildPrompt(toolBuilder());
        Gson ignored = new Gson();
        String response;


        try {


            OllamaChatRequestModel requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, systemPrompt)
                    .withMessage(OllamaChatMessageRole.USER, userPrompt)
                    .build();

            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            response = chatResult.getResponse();



            executeFunction(userPrompt,response);
            // Call the function and capture the result


            } catch (OllamaBaseException | IOException | InterruptedException | JsonSyntaxException e) {
                logger.error("Error while running function caller task: {}", (Object) e.getStackTrace());
            }

    }




    private static void executeFunction(String userInput,String response) {

        String executionDateTime = getCurrentDateandTime();

        try {

            new Thread( () -> {

                String cleanedResponse = cleanJsonString(response);
                System.out.println("Cleaned JSON Response: " + cleanedResponse); // Log the cleaned JSON response for debugging
                JsonReader reader = new JsonReader(new StringReader(cleanedResponse));
                reader.setLenient(true);
                JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                String functionName = jsonObject.get("functionName").getAsString();
                JsonArray parameters = jsonObject.get("parameters").getAsJsonArray();

                StringBuilder params = new StringBuilder();
                Map<String, String> parameterMap = new ConcurrentHashMap<>();

                for (JsonElement parameter : parameters) {
                    JsonObject paramObj = parameter.getAsJsonObject();
                    String paramName = paramObj.get("parameterName").getAsString();
                    String paramValue = paramObj.get("parameterValue").getAsString();
                    params.append(paramName).append("=").append(paramValue).append(", ");

                    parameterMap.put(paramName, paramValue);

                }

                System.out.println("Params: " + params);
                System.out.println("Parameter Map: " + parameterMap);

                // call the actual function
                String result = "Executed " + functionName + " with parameters " + params;

                callFunction(functionName, parameterMap).thenRun(() -> {
                    getFunctionResultAndSave(userInput, executionDateTime);
                });




                System.out.println(result);

            } ).start();


        } catch (JsonSyntaxException | NullPointerException e) {
            System.err.println("Error processing JSON response: " + e.getMessage());
        }

    }


    private static void getFunctionResultAndSave(String userInput, String executionDateTime) {

        try {
            // Generate context synchronously
            String eventContext = generatePromptContext(userInput);

            // Generate event embedding synchronously
            List<Double> eventEmbedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, userInput);

            // Generate event context embedding synchronously
            List<Double> eventContextEmbedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, eventContext);

            // Wait until functionOutput is a valid string
            while (functionOutput == null || !(functionOutput instanceof String)) {
                try {
                    Thread.sleep(500L); // Check every 500ms
                } catch (InterruptedException e) {
                    logger.error("Couldn't get function call output");
                    throw new RuntimeException(e);
                }
            }

            System.out.println("Received output: " + functionOutput);

            // Generate result embedding based on the function output
            List<Double> resultEmbedding = ollamaAPI.generateEmbeddings(OllamaModelType.NOMIC_EMBED_TEXT, functionOutput);

            // Create execution record and save it
            ExecutionRecord executionRecord = new ExecutionRecord(executionDateTime, userInput, eventContext, functionOutput, eventEmbedding, eventContextEmbedding, resultEmbedding);
            executionRecord.updateRecords();

            // Clear the functionOutput to reset state
            functionOutput = null;

            System.out.println("Event data saved successfully.");
        } catch (IOException | OllamaBaseException | InterruptedException e) {
            // Log or handle the exception
            logger.error("Error occurred while processing the function result: ", e);
            throw new RuntimeException(e);
        }
    }


    private static CompletableFuture<Void> callFunction(String functionName, Map<String, String> paramMap) {
        return CompletableFuture.runAsync( () -> {
            switch (functionName) {
                case "goTo":
                    int x = Integer.parseInt(paramMap.get("x"));
                    int y = Integer.parseInt(paramMap.get("y"));
                    int z = Integer.parseInt(paramMap.get("z"));
                    System.out.println("Calling method: goTo");
                    Tools.goTo(x, y, z);
                    break;

                case "searchBlock":
                    String direction = paramMap.get("direction");
                    Tools.searchBlock(direction);
                    break;

                default:
            }
        } );
    }

}
