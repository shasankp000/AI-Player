package net.shasankp000.ChatUtils;

import io.github.amithkoujalgi.ollama4j.core.OllamaAPI;
import io.github.amithkoujalgi.ollama4j.core.exceptions.OllamaBaseException;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestBuilder;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatRequestModel;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole;
import io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatResult;
import io.github.amithkoujalgi.ollama4j.core.utils.PromptBuilder;
import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.io.IOException;
import java.util.*;


public class NLPProcessor {

    private static final String HOST = "http://localhost:11434/";
    private static OllamaAPI ollamaAPI;

    // Private constructor to prevent instantiation
    private NLPProcessor() {
        ollamaAPI = new OllamaAPI(HOST);
        ollamaAPI.setRequestTimeoutSeconds(90);
    }

    // Singleton instance holder
    private static class NLPProcessorHolder {
        private static final NLPProcessor INSTANCE = new NLPProcessor();
    }

    public static NLPProcessor getInstance() {
        return NLPProcessorHolder.INSTANCE;
    }


    public static final Map<String, String> INTENT_KEYWORDS = Map.<String, String>ofEntries(
            // Movement
            Map.entry("move", "REQUEST_ACTION"),
            Map.entry("go", "REQUEST_ACTION"),
            Map.entry("walk", "REQUEST_ACTION"),
            Map.entry("run", "REQUEST_ACTION"),
            Map.entry("navigate", "REQUEST_ACTION"),
            Map.entry("travel", "REQUEST_ACTION"),
            Map.entry("step", "REQUEST_ACTION"),
            Map.entry("approach", "REQUEST_ACTION"),
            Map.entry("advance", "REQUEST_ACTION"),

            // Mining
            Map.entry("mine", "REQUEST_ACTION"),
            Map.entry("dig", "REQUEST_ACTION"),
            Map.entry("excavate", "REQUEST_ACTION"),
            Map.entry("collect", "REQUEST_ACTION"),
            Map.entry("gather", "REQUEST_ACTION"),
            Map.entry("break", "REQUEST_ACTION"),
            Map.entry("harvest", "REQUEST_ACTION"),

            // Combat
            Map.entry("attack", "REQUEST_ACTION"),
            Map.entry("fight", "REQUEST_ACTION"),
            Map.entry("defend", "REQUEST_ACTION"),
            Map.entry("slay", "REQUEST_ACTION"),
            Map.entry("kill", "REQUEST_ACTION"),
            Map.entry("vanquish", "REQUEST_ACTION"),
            Map.entry("destroy", "REQUEST_ACTION"),
            Map.entry("battle", "REQUEST_ACTION"),

            // Crafting
            Map.entry("craft", "REQUEST_ACTION"),
            Map.entry("create", "REQUEST_ACTION"),
            Map.entry("make", "REQUEST_ACTION"),
            Map.entry("build", "REQUEST_ACTION"),
            Map.entry("forge", "REQUEST_ACTION"),
            Map.entry("assemble", "REQUEST_ACTION"),

            // Trading/Bartering
            Map.entry("trade", "REQUEST_ACTION"),
            Map.entry("barter", "REQUEST_ACTION"),
            Map.entry("exchange", "REQUEST_ACTION"),
            Map.entry("buy", "REQUEST_ACTION"),
            Map.entry("sell", "REQUEST_ACTION"),

            // Exploration
            Map.entry("explore", "REQUEST_ACTION"),
            Map.entry("discover", "REQUEST_ACTION"),
            Map.entry("find", "REQUEST_ACTION"),
            Map.entry("search", "REQUEST_ACTION"),
            Map.entry("locate", "REQUEST_ACTION"),
            Map.entry("scout", "REQUEST_ACTION"),

            // Building
            Map.entry("construct", "REQUEST_ACTION"),
            Map.entry("erect", "REQUEST_ACTION"),
            Map.entry("place", "REQUEST_ACTION"),
            Map.entry("set", "REQUEST_ACTION"),

            // Farming
            Map.entry("farm", "REQUEST_ACTION"),
            Map.entry("plant", "REQUEST_ACTION"),
            Map.entry("grow", "REQUEST_ACTION"),
            Map.entry("cultivate", "REQUEST_ACTION"),

            // Utility
            Map.entry("use", "REQUEST_ACTION"),
            Map.entry("utilize", "REQUEST_ACTION"),
            Map.entry("activate", "REQUEST_ACTION"),
            Map.entry("employ", "REQUEST_ACTION"),
            Map.entry("operate", "REQUEST_ACTION"),
            Map.entry("handle", "REQUEST_ACTION"),

            // Information Requests
            Map.entry("what", "ASK_INFORMATION"),
            Map.entry("how", "ASK_INFORMATION"),
            Map.entry("why", "ASK_INFORMATION"),
            Map.entry("when", "ASK_INFORMATION"),
            Map.entry("who", "ASK_INFORMATION"),
            Map.entry("check", "ASK_INFORMATION"),

            // General Conversation
            Map.entry("hello", "GENERAL_CONVERSATION"),
            Map.entry("hi", "GENERAL_CONVERSATION"),
            Map.entry("hey", "GENERAL_CONVERSATION"),
            Map.entry("greetings", "GENERAL_CONVERSATION")
    );

    public static final Map<String, Integer> KEYWORD_CONFIDENCE = Map.<String, Integer>ofEntries(
            // Materials
            Map.entry("wood", 80),
            Map.entry("stone", 80),
            Map.entry("iron", 80),
            Map.entry("diamond", 90),
            Map.entry("gold", 80),
            Map.entry("emerald", 85),
            Map.entry("obsidian", 85),
            Map.entry("lava", 70),
            Map.entry("water", 70),

            // Tools
            Map.entry("axe", 80),
            Map.entry("pickaxe", 80),
            Map.entry("shovel", 80),
            Map.entry("sword", 85),
            Map.entry("bow", 75),
            Map.entry("hoe", 70),
            Map.entry("shield", 80),
            Map.entry("armor", 80),

            // Tools
            Map.entry("axes", 80),
            Map.entry("pickaxes", 80),
            Map.entry("shovels", 80),
            Map.entry("swords", 85),
            Map.entry("bows", 75),
            Map.entry("hoes", 70),
            Map.entry("shields", 80),
            Map.entry("armors", 80),

            // Mobs
            Map.entry("zombie", 85),
            Map.entry("skeleton", 85),
            Map.entry("creeper", 85),
            Map.entry("spider", 80),
            Map.entry("enderman", 90),
            Map.entry("blaze", 90),
            Map.entry("ender dragon", 95),
            Map.entry("villager", 80),
            Map.entry("pillager", 85),

            // Plural variants.
            Map.entry("zombies", 85),
            Map.entry("skeletons", 85),
            Map.entry("creepers", 85),
            Map.entry("spiders", 80),
            Map.entry("endermen", 90),
            Map.entry("blazes", 90),
            Map.entry("villagers", 80),
            Map.entry("pillagers", 85),

            // Structures
            Map.entry("house", 70),
            Map.entry("village", 75),
            Map.entry("fortress", 85),
            Map.entry("stronghold", 90),
            Map.entry("portal", 85),
            Map.entry("tower", 75),

            // Plural variants
            Map.entry("houses", 70),
            Map.entry("villages", 75),
            Map.entry("fortresses", 85),
            Map.entry("strongholds", 90),
            Map.entry("portals", 85),
            Map.entry("towers", 75),

            // Locations
            Map.entry("nether", 85),
            Map.entry("end", 90),
            Map.entry("overworld", 70),
            Map.entry("mine", 80),
            Map.entry("cave", 80),

            // Actions
            Map.entry("build", 80),
            Map.entry("craft", 80),
            Map.entry("explore", 75),
            Map.entry("fight", 85),
            Map.entry("trade", 80),
            Map.entry("farm", 75),
            Map.entry("defend", 80),
            Map.entry("use", 75),

            // Movement

            Map.entry("coordinates", 90)
    );

    public static final Map<String, Set<String>> SYNONYM_MAP = new HashMap<>();

    static {
        // Movement-related actions
        SYNONYM_MAP.put("move", new HashSet<>(Arrays.asList("go", "walk", "run", "travel", "proceed", "advance", "head", "step", "march")));
        SYNONYM_MAP.put("stop", new HashSet<>(Arrays.asList("halt", "pause", "cease", "stand", "stay", "wait")));

        // Attack and combat-related actions
        SYNONYM_MAP.put("attack", new HashSet<>(Arrays.asList("defeat", "fight", "strike", "assault", "hit", "battle", "destroy", "kill", "slay")));
        SYNONYM_MAP.put("defend", new HashSet<>(Arrays.asList("protect", "guard", "shield", "block", "secure")));

        // Gathering and resource-related actions
        SYNONYM_MAP.put("gather", new HashSet<>(Arrays.asList("collect", "harvest", "pick", "fetch", "acquire", "accumulate", "obtain")));
        SYNONYM_MAP.put("mine", new HashSet<>(Arrays.asList("dig", "excavate", "extract", "gather")));
        SYNONYM_MAP.put("craft", new HashSet<>(Arrays.asList("build", "construct", "make", "create", "forge")));

        // Exploration and discovery actions
        SYNONYM_MAP.put("explore", new HashSet<>(Arrays.asList("discover", "search", "find", "locate", "uncover", "reveal", "investigate", "scout")));
        SYNONYM_MAP.put("check", new HashSet<>(Arrays.asList("inspect", "examine", "observe", "survey", "review")));

        // Interaction with objects or entities
        SYNONYM_MAP.put("use", new HashSet<>(Arrays.asList("activate", "operate", "apply", "utilize")));
        SYNONYM_MAP.put("talk", new HashSet<>(Arrays.asList("speak", "communicate", "chat", "converse", "discuss")));
        SYNONYM_MAP.put("trade", new HashSet<>(Arrays.asList("exchange", "barter", "buy", "sell", "deal")));

        // Directions
        SYNONYM_MAP.put("front", new HashSet<>(Arrays.asList("ahead", "forward", "in front")));
        SYNONYM_MAP.put("back", new HashSet<>(Arrays.asList("behind", "reverse", "retreat")));
        SYNONYM_MAP.put("left", new HashSet<>(Arrays.asList("port", "side", "to the left")));
        SYNONYM_MAP.put("right", new HashSet<>(Arrays.asList("starboard", "to the right", "side")));
        SYNONYM_MAP.put("up", new HashSet<>(Arrays.asList("above", "over", "ascend", "rise", "elevate")));
        SYNONYM_MAP.put("down", new HashSet<>(Arrays.asList("below", "under", "descend", "drop", "fall")));

        // General Conversation
        SYNONYM_MAP.put("hello", new HashSet<>(Arrays.asList("hi", "hey", "greetings", "salutations", "howdy")));
        SYNONYM_MAP.put("goodbye", new HashSet<>(Arrays.asList("bye", "farewell", "see you", "later", "take care")));
        SYNONYM_MAP.put("thanks", new HashSet<>(Arrays.asList("thank you", "appreciate it", "grateful", "much obliged")));
        SYNONYM_MAP.put("yes", new HashSet<>(Arrays.asList("yeah", "yep", "affirmative", "sure", "okay")));
        SYNONYM_MAP.put("no", new HashSet<>(Arrays.asList("nope", "negative", "nah", "not really")));

        // Weather and environment-related terms
        SYNONYM_MAP.put("weather", new HashSet<>(Arrays.asList("climate", "conditions", "forecast", "temperature")));
        SYNONYM_MAP.put("day", new HashSet<>(Arrays.asList("morning", "afternoon", "sunrise", "sunset")));
        SYNONYM_MAP.put("night", new HashSet<>(Arrays.asList("evening", "dark", "dusk", "midnight")));

        SYNONYM_MAP.put("coordinates", new HashSet<>(Arrays.asList("coordinates","coords", "co-ordinates")));
    }



    public static Map<Intent, List<String>> runNlpTask(String userInput) {
        Map<Intent, List<String>> intentsAndEntities = new HashMap<>();

        // Generate and process intent prompt
        String intentPrompt = buildIntentPrompt(userInput);
        String intentResponse = processInput(intentPrompt);

        System.out.println(intentResponse);

        // Recognize the intent from the response
        Intent recognizedIntent = recognizeIntent(userInput);

        System.out.println(recognizedIntent);

        // Generate and process entity extraction prompt
        // String entityPrompt = buildEntityExtractionPrompt(userInput);
        // String entityResponse = processInput(entityPrompt);

        // Extract entities from the response
        List<String> entitiesFromPrompt = extractEntities(userInput);

        intentsAndEntities.put(recognizedIntent, entitiesFromPrompt);

        return intentsAndEntities;
    }

    private static String processInput(String prompt) {
        PromptBuilder promptBuilder = new PromptBuilder().addLine(prompt);
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.PHI3);

        OllamaChatRequestModel requestModel = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, promptBuilder.build())
                .withMessage(OllamaChatMessageRole.USER, prompt)
                .build();

        OllamaChatResult chatResult;
        try {
            chatResult = ollamaAPI.chat(requestModel);
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return chatResult.getResponse();
    }

    private static String buildIntentPrompt(String userInput) {
        return "You are an NLP processing agent within the context of Minecraft. Your task is to analyze the user's input and determine the intent. " +
                userInput + "Classify commands into the following intents: REQUEST_ACTION, ASK_INFORMATION, GENERAL_CONVERSATION, UNSPECIFIED. " +
                "Consider synonyms and context-specific phrases such as 'go' for 'move', 'defeat' for 'attack', irrespective of lower/upper case, " +
                "'gather' for 'collect', etc. For questions starting with 'what', 'how', 'why', 'when', 'who', irrespective of lower/upper case, classify as ASK_INFORMATION. " +
                "Identify directional words like 'front', 'back', 'left', 'right', 'up', 'down', and their synonyms as entities irrespective of lower/upper case. " +
                "Also, recognize any sequences of numbers, positive or negative as potential coordinates or numerical entities within the context of Minecraft. If you detect negative numbers, do not change them to positive. Return them as coordinates exactly as is." +
                "Respond in the format: Intent: [intent]. Detected Entities: [entities]. Also rate your own results with an accuracy percentage. Answer in the format: Accuracy: 10% for example.";
    }

    private static Intent recognizeIntent(String userInput) {
        String[] words = userInput.split("\\s+");
        Map<String, Integer> intentScores = new HashMap<>();

        // Iterate over each word in the response and calculate the confidence score
        for (String word : words) {
            for (Map.Entry<String, String> entry : INTENT_KEYWORDS.entrySet()) {
                String keyword = entry.getKey();
                String intent = entry.getValue();
                int score = calculateConfidenceScore(word, keyword);

                // Aggregate scores for each intent
                intentScores.put(intent, intentScores.getOrDefault(intent, 0) + score);

                // Break the loop if maximum score is reached
                if (score == 100) {
                    break;
                }
            }
        }

        // Find the intent with the highest score
        String recognizedIntent = intentScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNSPECIFIED");

        return switch (recognizedIntent) {
            case "REQUEST_ACTION" -> Intent.REQUEST_ACTION;
            case "ASK_INFORMATION" -> Intent.ASK_INFORMATION;
            case "GENERAL_CONVERSATION" -> Intent.GENERAL_CONVERSATION;
            default -> Intent.UNSPECIFIED;
        };
    }


    private static List<String> extractEntities(String userInput) {
        String[] words = userInput.split("\\s+");
        List<String> entities = new ArrayList<>();
        int minConfidenceThreshold = 50; // Minimum confidence score to consider an entity

        boolean foundCoordinates = false;
        List<String> coordinates = new ArrayList<>();

        for (String word : words) {
            for (Map.Entry<String, Integer> entry : KEYWORD_CONFIDENCE.entrySet()) {
                String keyword = entry.getKey();
                int score = calculateConfidenceScore(word, keyword);

                if (score >= minConfidenceThreshold) {
                    if (keyword.equals("coordinates")) {
                        foundCoordinates = true;
                    } else {
                        entities.add(keyword);
                    }
                    // Break if a significant confidence score is achieved
                    break;
                }
            }

            // Check for numbers if "coordinates" is detected
            if (foundCoordinates && word.matches("-?[0-9]+")) {
                coordinates.add(word);
            }
        }

        if (!coordinates.isEmpty()) {
            entities.add("coordinates");
            entities.addAll(coordinates);
        }

        return entities;
    }


    private static int calculateConfidenceScore(String word, String keyword) {
       // System.out.println("Word: " + word + " Keyword: " + keyword);
        if (word.equalsIgnoreCase(keyword)) {
            return 100; // Exact match
        } else if (areSynonyms(word, keyword)) {
            return 80; // Synonym match
        } else {
            return 0; // No match
        }
    }

    public static boolean areSynonyms(String word1, String word2) {
        word1 = word1.toLowerCase();
        word2 = word2.toLowerCase();

        if (word1.equals(word2)) {
            return true; // Exact match
        }

        // Check if word1 is a synonym of word2
        if (SYNONYM_MAP.containsKey(word2)) {
            return SYNONYM_MAP.get(word2).contains(word1);
        }

        // Check if word2 is a synonym of word1
        if (SYNONYM_MAP.containsKey(word1)) {
            return SYNONYM_MAP.get(word1).contains(word2);
        }

        return false;
    }

    public static void handleEntities(Intent intent, List<String> entities) {
        switch (intent) {
            case REQUEST_ACTION:
                handleRequestActionEntities(entities);
                break;
            case ASK_INFORMATION:
                handleAskInformationEntities(entities);
                break;
            case GENERAL_CONVERSATION:
                handleGeneralConversationEntities(entities);
                break;
            default:
                System.out.println("Unhandled intent: " + intent);
        }
    }

    private static void handleRequestActionEntities(List<String> entities) {
        Set<String> uniqueEntities = new HashSet<>(entities);
        List<String> nonActionDescriptors = List.of("coordinates");

        String coordinate = null;
        String targetObject = null;
        String primaryAction = null;

        for (String entity : uniqueEntities) {
            if (entity.matches("-?\\d+\\s+-?\\d+\\s+-?\\d+")) {
                coordinate = entity;
            } else if (nonActionDescriptors.contains(entity.toLowerCase())) {
                continue;
            } else if (entity.equalsIgnoreCase("move")) {
                primaryAction = "move";
            } else if (entity.equalsIgnoreCase("mine")) {
                primaryAction = "mine";
            } else if (entity.equalsIgnoreCase("check")) {
                primaryAction = "check";
            } else if (entity.equalsIgnoreCase("build")) {
                primaryAction = "build";
            } else if (entity.equalsIgnoreCase("craft")) {
                primaryAction = "craft";
            } else if (entity.equalsIgnoreCase("attack")) {
                primaryAction = "attack";
            } else {
                targetObject = entity; // Capture the target object like "stone block"
            }
        }

        if (primaryAction != null) {
            switch (primaryAction) {
                case "move":
                    dummyMoveAction();
                    break;
                case "mine":
                    if (targetObject != null) {
                        System.out.println("Mining the " + targetObject + "...");
                    }
                    dummyMineAction();
                    break;
                case "check":
                    dummyCheckAction();
                    break;
                case "build":
                    dummyBuildAction();
                    break;
                case "craft":
                    dummyCraftAction();
                    break;
                case "attack":
                    dummyAttackAction();
                    break;
                default:
                    System.out.println("Unknown action: " + primaryAction);
            }
        }

        if (coordinate != null) {
            System.out.println("Navigating to coordinates: " + coordinate);
        }
    }



    private static void handleAskInformationEntities(List<String> entities) {
        for (String entity : entities) {
            if (entity.equalsIgnoreCase("check")) {
                dummyCheckAction();
            } else {
                System.out.println("Unhandled information request: " + entity);
            }
        }
    }

    private static void handleGeneralConversationEntities(List<String> entities) {
        System.out.println("Handling general conversation...");
        // Add logic for general conversation if needed
    }

    public static void dummyMoveAction() {
        System.out.println("Executing dummy move action...");
    }

    public static void dummyMineAction() {
        System.out.println("Executing dummy mine action...");
    }

    public static void dummyCheckAction() {
        System.out.println("Executing dummy check action...");
    }

    public static void dummyBuildAction() {
        System.out.println("Executing dummy build action...");
    }

    public static void dummyCraftAction() {
        System.out.println("Executing dummy craft action...");
    }

    public static void dummyAttackAction() {
        System.out.println("Executing dummy attack action...");
    }

    public static void dummyCoordinateAction(String coordinates) {
        System.out.println("Navigating to coordinates: " + coordinates);
    }


//    public static void main(String[] args) {
//        NLPProcessor ignored = new NLPProcessor();
//        Scanner scanner = new Scanner(System.in);
//
//        while (true) {
//            System.out.print("Enter message: ");
//            String userInput = scanner.nextLine().trim();
//
//            Map<NLPProcessor.Intent, List<String>> intentsAndEntities = runNlpTask(userInput);
//
//            for (Map.Entry<NLPProcessor.Intent, List<String>> entry : intentsAndEntities.entrySet()) {
//                System.out.println("Recalculated Intent: " + entry.getKey());
//                System.out.println("Entities: " + entry.getValue());
//
//                handleEntities(entry.getKey(), entry.getValue());
//            }
//
//            System.out.println("Enter 'exit' to quit.");
//            String exitInput = scanner.nextLine().trim();
//            if (exitInput.equalsIgnoreCase("exit")) {
//                break;
//            }
//        }
//        scanner.close();
//    }

    public enum Intent {
        REQUEST_ACTION,
        ASK_INFORMATION,
        GENERAL_CONVERSATION,
        UNSPECIFIED
    }
}
