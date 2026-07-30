package net.shasankp000.GameAI.planner;

import net.shasankp000.AIPlayer;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.ServiceLLMClients.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps natural language goals to numeric goal IDs.
 *
 * Pipeline (in order of speed):
 *  1. Weighted token scorer  — pure Java, < 1 ms, handles synonyms via SynonymMap.
 *  2. Edge LLM fallback      — async, only triggered on GOAL_UNKNOWN.
 *     Recommended edge models (fast on CPU/integrated GPU, runs via Ollama):
 *       - qwen2.5:0.5b   (~300 MB, ~50–120 tok/s on CPU)
 *       - qwen2.5:1.5b   (~900 MB, ~30–80  tok/s on CPU)
 *       - smollm2:135m   (~90  MB, ~150+   tok/s on CPU)  ← fastest option
 *       - smollm2:360m   (~220 MB, ~100+   tok/s on CPU)
 *       - tinyllama:1.1b (~640 MB, ~20–50  tok/s on CPU)
 *       - gemma3:1b      (~815 MB, ~30–60  tok/s on CPU)
 *     For this single-token classification task any of these will respond in
 *     well under 1 second even on a low-end machine.
 *     To use: pull the model with `ollama pull <name>` and set
 *     aiplayer.edgeFallbackModel=<name>  (system property or config).
 *     Falls back silently to GOAL_UNKNOWN if Ollama is unavailable.
 */
public class GoalMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoalMapper");
    private static final Map<Short, String> GOAL_ID_TO_NAME = new HashMap<>();
    private static final Map<String, Short> GOAL_KEYWORD_TO_ID = new HashMap<>();

    // ── Edge-LLM fallback settings ────────────────────────────────────────────
    /**
     * System property that selects the edge model used for GOAL_UNKNOWN fallback.
     * Recommended value: "smollm2:135m" or "qwen2.5:0.5b".
     * Can also be set via the mod config under selectedLanguageModel when running
     * a tiny model specifically for classification.
     */
    private static final String EDGE_MODEL_PROP   = "aiplayer.edgeFallbackModel";
    private static final String EDGE_MODEL_DEFAULT = "smollm2:135m";

    /** Hard timeout for the edge-model call.  Should comfortably finish in < 2 s. */
    private static final long   EDGE_LLM_TIMEOUT_MS = 3_000;

    private static final ExecutorService EXECUTOR =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GoalMapper-EdgeLLM");
            t.setDaemon(true);
            return t;
        });

    // ── Goal IDs ──────────────────────────────────────────────────────────────
    public static final short GOAL_MINE    = 1;
    public static final short GOAL_BUILD   = 2;
    public static final short GOAL_CRAFT   = 3;
    public static final short GOAL_NAVIGATE = 4;
    public static final short GOAL_COMBAT  = 5;
    public static final short GOAL_GATHER  = 6;
    public static final short GOAL_EXPLORE = 7;
    public static final short GOAL_FARM    = 8;
    public static final short GOAL_TRADE   = 9;
    public static final short GOAL_UNKNOWN = 0;

    static {
        registerGoal(GOAL_MINE,    "mine",     "mine", "mining", "dig", "excavate", "extract");
        registerGoal(GOAL_BUILD,   "build",    "build", "place", "construct", "create structure");
        registerGoal(GOAL_CRAFT,   "craft",    "craft", "make", "create", "assemble");
        registerGoal(GOAL_NAVIGATE,"navigate", "go", "navigate", "move", "travel", "walk");
        registerGoal(GOAL_COMBAT,  "combat",   "fight", "attack", "combat", "kill", "defend");
        registerGoal(GOAL_GATHER,  "gather",   "gather", "collect", "fetch", "get", "obtain");
        registerGoal(GOAL_EXPLORE, "explore",  "explore", "search", "find", "look for");
        registerGoal(GOAL_FARM,    "farm",     "farm", "harvest", "plant", "grow");
        registerGoal(GOAL_TRADE,   "trade",    "trade", "buy", "sell", "exchange");
    }

    private static void registerGoal(short goalId, String name, String... keywords) {
        GOAL_ID_TO_NAME.put(goalId, name);
        for (String kw : keywords) {
            GOAL_KEYWORD_TO_ID.put(kw.toLowerCase(), goalId);
        }
    }

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Parse a natural-language goal into a goal ID.
     *
     * Fast path  : weighted token scorer (synonym-normalised), < 1 ms.
     * Slow path  : edge LLM via Ollama (async, 3 s timeout), only on GOAL_UNKNOWN.
     */
    public static short parseGoal(String naturalLanguageGoal) {
        if (naturalLanguageGoal == null || naturalLanguageGoal.isEmpty()) {
            return GOAL_UNKNOWN;
        }

        // Step 1 — normalise with SynonymMap, then score tokens
        short scored = parseGoalWithScoring(naturalLanguageGoal);
        if (scored != GOAL_UNKNOWN) {
            return scored;
        }

        LOGGER.info("Token scorer returned UNKNOWN for '{}', trying edge-LLM fallback…",
            naturalLanguageGoal);

        // Step 2 — edge-LLM fallback (async with hard timeout)
        try {
            Future<Short> future = EXECUTOR.submit(() -> parseGoalWithEdgeLLM(naturalLanguageGoal));
            short llmResult = future.get(EDGE_LLM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (llmResult != GOAL_UNKNOWN) {
                LOGGER.info("✓ Edge-LLM classified '{}' → {} ({})",
                    naturalLanguageGoal, llmResult, getGoalName(llmResult));
                return llmResult;
            }
        } catch (TimeoutException e) {
            LOGGER.warn("⏱ Edge-LLM timed out after {}ms for '{}'",
                EDGE_LLM_TIMEOUT_MS, naturalLanguageGoal);
        } catch (Exception e) {
            LOGGER.warn("⚠ Edge-LLM fallback failed: {}", e.getMessage());
        }

        LOGGER.warn("⚠ Could not classify '{}' — returning GOAL_UNKNOWN", naturalLanguageGoal);
        return GOAL_UNKNOWN;
    }

    // ── Weighted token scorer ─────────────────────────────────────────────────

    /**
     * Normalise input via {@link SynonymMap}, tokenise, then tally keyword hits
     * per goal.  The goal with the highest tally wins.  Runs in O(tokens × keywords).
     */
    private static short parseGoalWithScoring(String input) {
        // 1. Synonym normalisation
        String normalised = SynonymMap.normalize(input);
        String lower      = normalised.toLowerCase();
        String[] tokens   = lower.split("\\W+");

        Map<Short, Integer> scores = new HashMap<>();

        // 2. Score each token against the keyword map
        for (String token : tokens) {
            if (token.length() < 2) continue;
            Short goalId = GOAL_KEYWORD_TO_ID.get(token);
            if (goalId != null) {
                scores.merge(goalId, 1, Integer::sum);
            }
        }

        // 3. Also check multi-word keywords against the full normalised string
        for (Map.Entry<String, Short> entry : GOAL_KEYWORD_TO_ID.entrySet()) {
            if (entry.getKey().contains(" ") && lower.contains(entry.getKey())) {
                scores.merge(entry.getValue(), 2, Integer::sum); // bonus weight for phrase match
            }
        }

        if (scores.isEmpty()) {
            return GOAL_UNKNOWN;
        }

        // 4. Return goal with highest accumulated score
        short best = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(GOAL_UNKNOWN);

        LOGGER.info("✓ Token scorer: '{}' → normalised='{}' → goalId={} ({})",
            input, normalised, best, getGoalName(best));
        return best;
    }

    // ── Edge-LLM fallback ─────────────────────────────────────────────────────

    /**
     * Fire a minimal classification prompt at a tiny edge model running via Ollama.
     *
     * The prompt is deliberately tiny: just the goal list + the input sentence.
     * A single-digit response is all that is expected, so even a 135 M-param
     * model (smollm2:135m) handles this in < 500 ms on a low-end CPU.
     *
     * Model selection order:
     *  1. System property  aiplayer.edgeFallbackModel
     *  2. Mod config       selectedLanguageModel  (if it looks like an edge model)
     *  3. Hardcoded default: smollm2:135m
     */
    private static short parseGoalWithEdgeLLM(String naturalLanguageGoal) {
        String llmProvider = System.getProperty("aiplayer.llmMode", "custom");

        try {
            // Build goal list
            StringBuilder goalList = new StringBuilder();
            for (short id : getAllGoalIds()) {
                if (id != GOAL_UNKNOWN) {
                    goalList.append(id).append("=").append(getGoalName(id)).append(", ");
                }
            }

            String prompt = String.format(
                "Classify this Minecraft goal. Reply with ONE digit only.\n" +
                "Categories: %s0=unknown\n" +
                "Goal: \"%s\"\n" +
                "Answer:",
                goalList, naturalLanguageGoal
            );

            final String systemPrompt =
                "You classify Minecraft player goals. Reply with ONLY a single digit (0-9).";

            switch (llmProvider) {

                case "openai", "gpt", "google", "gemini",
                     "anthropic", "claude", "xAI", "xai", "grok", "custom": {
                    LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                    if (llmClient == null) return GOAL_UNKNOWN;
                    return extractGoalDigit(llmClient.sendPrompt(systemPrompt, prompt));
                }

                default:
                    LOGGER.warn("Unsupported LLM mode '{}' for edge fallback", llmProvider);
                    return GOAL_UNKNOWN;
            }

        } catch (Exception e) {
            LOGGER.error("Edge-LLM goal parsing error: {}", e.getMessage());
            return GOAL_UNKNOWN;
        }
    }

    /**
     * Determine which edge model to use.
     * Priority: system property → mod config (if small model) → default.
     */
    private static String resolveEdgeModel() {
        // 1. Explicit override via system property
        String prop = System.getProperty(EDGE_MODEL_PROP);
        if (prop != null && !prop.isBlank()) {
            LOGGER.info("Using edge model from system property: {}", prop);
            return prop;
        }

        // 2. Mod config — use if it looks like an edge / small model
        try {
            String configured = AIPlayer.CONFIG.getSelectedLanguageModel();
            if (configured != null && !configured.isBlank()) {
                // Heuristic: treat as edge model if name contains known small-model identifiers
                String lower = configured.toLowerCase();
                if (lower.contains("smollm") || lower.contains("0.5b") ||
                    lower.contains("1.5b")   || lower.contains("135m") ||
                    lower.contains("360m")   || lower.contains("tinyllama") ||
                    lower.contains("gemma3:1b") || lower.contains("qwen2.5:0.5b")) {
                    LOGGER.info("Using configured model as edge model: {}", configured);
                    return configured;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read mod config for edge model: {}", e.getMessage());
        }

        // 3. Hardcoded default
        LOGGER.info("Using default edge model: {}", EDGE_MODEL_DEFAULT);
        return EDGE_MODEL_DEFAULT;
    }

    /** Extract the first valid goal digit from an LLM response string. */
    private static short extractGoalDigit(String content) {
        if (content == null || content.isBlank()) return GOAL_UNKNOWN;
        Matcher m = Pattern.compile("\\b([0-9])\\b").matcher(content);
        if (m.find()) {
            short id = Short.parseShort(m.group(1));
            if (isValidGoal(id) || id == GOAL_UNKNOWN) return id;
        }
        try { return Short.parseShort(content.trim()); } catch (NumberFormatException ignored) {}
        return GOAL_UNKNOWN;
    }

    // ── Utility methods ───────────────────────────────────────────────────────

    public static String getGoalName(short goalId) {
        return GOAL_ID_TO_NAME.getOrDefault(goalId, "unknown");
    }

    public static short[] getAllGoalIds() {
        List<Short> sorted = new ArrayList<>(GOAL_ID_TO_NAME.keySet());
        Collections.sort(sorted);
        short[] result = new short[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) result[i] = sorted.get(i);
        return result;
    }

    public static boolean isValidGoal(short goalId) {
        return GOAL_ID_TO_NAME.containsKey(goalId);
    }
}
