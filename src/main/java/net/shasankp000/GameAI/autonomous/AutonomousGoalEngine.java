package net.shasankp000.GameAI.autonomous;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.planner.GoalMapper;
import net.shasankp000.GameAI.planner.HybridPlanner;
import net.shasankp000.ServiceLLMClients.LLMClient;
import net.shasankp000.ServiceLLMClients.LLMServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core autonomous loop for the AI bot.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>On bot spawn (or explicit trigger) ask the LLM to generate a JSON array
 *       of natural-language goal strings and enqueue them.</li>
 *   <li>Pop goals one at a time and dispatch them via {@link GoalMapper} +
 *       {@link HybridPlanner}.</li>
 *   <li>Accept priority goal injections from {@link WorldEventListener} via
 *       {@link #injectUrgentGoal(String)} or from the companion system via
 *       {@link #injectGoalWithPriority(String, int)}.</li>
 *   <li>Pause autonomous execution while a human player is directly addressing
 *       the bot ({@link #setPlayerControlled(boolean)}).</li>
 * </ol>
 *
 * <p>The goal queue is a {@link PriorityBlockingQueue} so urgent world-event
 * goals (priority=10) always surface before normal LLM plan goals (priority=0).
 * Companion FOLLOW goals use priority 15 and STAY return goals use priority 20.
 * Maximum depth is capped at {@value #MAX_QUEUE_DEPTH} to prevent runaway
 * re-plans from stacking up.
 */
public class AutonomousGoalEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("autonomous-goal-engine");

    /** Maximum number of queued goals at any time. */
    private static final int MAX_QUEUE_DEPTH = 10;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final String   botName;
    private final UUID     botUUID;

    /** True while a player is talking directly to the bot; autonomous loop yields. */
    private final AtomicBoolean playerControlled = new AtomicBoolean(false);

    /** True once shutdown() has been called. */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Priority queue — higher {@link GoalQueueEntry#priority()} values are
     * dequeued first.  Capacity is a soft hint; we enforce MAX_QUEUE_DEPTH
     * manually on insert.
     */
    private final PriorityBlockingQueue<GoalQueueEntry> goalQueue =
            new PriorityBlockingQueue<>(MAX_QUEUE_DEPTH);

    /** Single-thread executor that drives the goal execution loop. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name("autonomous-goal-loop").unstarted(r));

    // -------------------------------------------------------------------------
    // Construction & lifecycle
    // -------------------------------------------------------------------------

    public AutonomousGoalEngine(String botName, UUID botUUID) {
        this.botName  = botName;
        this.botUUID  = botUUID;
    }

    /**
     * Call once after the bot has joined the server.
     * Generates the initial goal list and starts the execution loop.
     */
    public void start() {
        LOGGER.info("[autonomous] Starting for bot '{}'", botName);
        CompletableFuture.runAsync(this::generateAndEnqueueGoals, executor);
        executor.submit(this::executionLoop);
    }

    /** Graceful shutdown — drains the queue and stops the executor. */
    public void shutdown() {
        stopped.set(true);
        goalQueue.clear();
        executor.shutdownNow();
        LOGGER.info("[autonomous] Shut down for bot '{}'", botName);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Inject a high-priority goal from {@link WorldEventListener}.
     * Silently dropped if the queue is already at max depth.
     */
    public void injectUrgentGoal(String goalText) {
        enqueue(new GoalQueueEntry(goalText, 10, GoalQueueEntry.Source.WORLD_EVENT));
    }

    /**
     * Inject a player-requested goal (normal priority).
     * Also flips the bot back to autonomous mode once done.
     */
    public void injectPlayerGoal(String goalText) {
        enqueue(new GoalQueueEntry(goalText, 5, GoalQueueEntry.Source.PLAYER));
    }

    /**
     * Inject a goal with an explicit priority value.
     *
     * <p>Used by the companion system:
     * <ul>
     *   <li>FOLLOW navigation goals — priority 15 (above world-events, below hard interrupts)</li>
     *   <li>STAY return-to-anchor goals — priority 20</li>
     * </ul>
     *
     * Silently dropped if the queue is already at max depth.
     *
     * @param goalText Human-readable goal string forwarded to {@link GoalMapper}.
     * @param priority Numeric priority; higher values dequeue first.
     */
    public void injectGoalWithPriority(String goalText, int priority) {
        enqueue(new GoalQueueEntry(goalText, priority, GoalQueueEntry.Source.PLAYER));
    }

    /**
     * Pause or resume autonomous execution.
     * Call with {@code true} when a human addresses the bot directly;
     * call with {@code false} when the interaction is complete.
     */
    public void setPlayerControlled(boolean controlled) {
        playerControlled.set(controlled);
        if (!controlled) {
            LOGGER.debug("[autonomous] Resuming autonomous mode for bot '{}'", botName);
        }
    }

    public boolean isPlayerControlled() {
        return playerControlled.get();
    }

    /** Returns the current number of queued goals. */
    public int queueSize() {
        return goalQueue.size();
    }

    /**
     * Trigger a fresh LLM re-plan and replace the current queue.
     * Called by {@link AutonomousScheduler} on its idle re-plan tick.
     */
    public void triggerReplan() {
        goalQueue.clear();
        generateAndEnqueueGoals();
    }

    // -------------------------------------------------------------------------
    // Goal generation (LLM call)
    // -------------------------------------------------------------------------

    /**
     * Calls the configured LLM with a structured state-aware prompt and
     * expects a JSON array of natural-language goal strings in return.
     *
     * Example expected response:
     * {@code ["gather 32 wood", "craft a crafting table", "mine stone", "build a shelter"]}
     */
    void generateAndEnqueueGoals() {
        if (stopped.get()) return;

        String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");
        LOGGER.info("[autonomous] Requesting goal plan from LLM (provider={})", llmProvider);

        try {
            ServerPlayerEntity bot = resolveBot();
            String stateSnapshot = buildStateSnapshot(bot);

            String systemPrompt =
                    "You are controlling a Minecraft bot. Based on the bot's current state, " +
                    "generate a prioritised list of 4-6 short, achievable goals for the bot to " +
                    "complete right now. Reply with ONLY a valid JSON array of strings. " +
                    "Each string must be a single concise goal in plain English. " +
                    "Example: [\"gather 32 wood\", \"craft a crafting table\", \"mine 16 stone\"]";

            String userPrompt = "Bot state:\n" + stateSnapshot +
                    "\n\nGenerate the goal list JSON array now:";

            String response = callLLM(llmProvider, systemPrompt, userPrompt);
            if (response == null || response.isBlank()) {
                LOGGER.warn("[autonomous] LLM returned empty response — no goals enqueued");
                return;
            }

            List<String> goals = parseGoalArray(response);
            if (goals.isEmpty()) {
                LOGGER.warn("[autonomous] Could not parse goal array from: {}", response);
                return;
            }

            int enqueued = 0;
            for (String goal : goals) {
                if (enqueue(new GoalQueueEntry(goal.trim(), 0, GoalQueueEntry.Source.LLM_PLAN))) {
                    enqueued++;
                }
            }
            LOGGER.info("[autonomous] Enqueued {} goals from LLM plan", enqueued);

        } catch (Exception e) {
            LOGGER.error("[autonomous] Goal generation failed: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Execution loop
    // -------------------------------------------------------------------------

    private void executionLoop() {
        LOGGER.info("[autonomous] Execution loop started for bot '{}'", botName);
        while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Yield while a player is talking to the bot
                if (playerControlled.get()) {
                    Thread.sleep(500);
                    continue;
                }

                // Block up to 5 s waiting for a goal
                GoalQueueEntry entry = goalQueue.poll(5, TimeUnit.SECONDS);
                if (entry == null) continue;

                executeGoal(entry);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("[autonomous] Unexpected error in execution loop: {}", e.getMessage(), e);
            }
        }
        LOGGER.info("[autonomous] Execution loop ended for bot '{}'", botName);
    }

    private void executeGoal(GoalQueueEntry entry) {
        LOGGER.info("[autonomous] Executing goal '{}' (priority={}, source={})",
                entry.goalText(), entry.priority(), entry.source());

        String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

        // For WORLD_EVENT goals that are purely conversational, route through
        // LLMServiceHandler so the bot produces a natural chat response.
        if (entry.source() == GoalQueueEntry.Source.WORLD_EVENT) {
            try {
                LLMClient client = buildClient(llmProvider);
                if (client != null) {
                    LLMServiceHandler.runFromChat(entry.goalText(), botName, botUUID, client);
                }
            } catch (Exception e) {
                LOGGER.error("[autonomous] Chat response failed for '{}': {}", entry.goalText(), e.getMessage());
            }
            return;
        }

        // For LLM_PLAN / PLAYER goals, parse via GoalMapper and dispatch to HybridPlanner
        short goalId = GoalMapper.parseGoal(entry.goalText());
        if (goalId == GoalMapper.GOAL_UNKNOWN) {
            LOGGER.warn("[autonomous] Could not map '{}' to a known goal — skipping", entry.goalText());
            return;
        }

        try {
            ServerPlayerEntity bot = resolveBot();
            if (bot == null) {
                LOGGER.warn("[autonomous] Bot '{}' not found on server — skipping goal", botName);
                return;
            }
            HybridPlanner.executeGoal(bot, goalId, entry.goalText());
        } catch (Exception e) {
            LOGGER.error("[autonomous] HybridPlanner execution failed for '{}': {}", entry.goalText(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean enqueue(GoalQueueEntry entry) {
        if (goalQueue.size() >= MAX_QUEUE_DEPTH) {
            LOGGER.debug("[autonomous] Queue full ({}) — dropping goal '{}'",
                    MAX_QUEUE_DEPTH, entry.goalText());
            return false;
        }
        goalQueue.offer(entry);
        return true;
    }

    private ServerPlayerEntity resolveBot() {
        if (AIPlayer.serverInstance == null) return null;
        return AIPlayer.serverInstance.getPlayerManager().getPlayer(botName);
    }

    /**
     * Builds a compact, LLM-readable snapshot of the bot's current game state.
     * Gracefully degrades when the bot entity is not available.
     */
    private String buildStateSnapshot(ServerPlayerEntity bot) {
        if (bot == null) return "(bot not found — assume fresh spawn, daytime, overworld)";

        StringBuilder sb = new StringBuilder();
        sb.append("- Health: ").append(String.format("%.1f", bot.getHealth()))
          .append(" / ").append(String.format("%.1f", bot.getMaxHealth())).append("\n");
        sb.append("- Hunger: ").append(bot.getHungerManager().getFoodLevel()).append(" / 20\n");
        sb.append("- Position: ").append(bot.getBlockPos()).append("\n");
        sb.append("- Dimension: ").append(bot.getWorld().getRegistryKey().getValue()).append("\n");

        long timeOfDay = bot.getWorld().getTimeOfDay() % 24000;
        String period = (timeOfDay < 6000) ? "morning" :
                        (timeOfDay < 12000) ? "afternoon" :
                        (timeOfDay < 13000) ? "sunset" : "night";
        sb.append("- Time: ").append(period).append(" (").append(timeOfDay).append(")\n");

        // Main hand item
        String held = bot.getMainHandStack().isEmpty() ? "nothing"
                : bot.getMainHandStack().getName().getString();
        sb.append("- Holding: ").append(held).append("\n");

        // Inventory item count (non-empty slots)
        int itemCount = 0;
        for (int i = 0; i < bot.getInventory().size(); i++) {
            if (!bot.getInventory().getStack(i).isEmpty()) itemCount++;
        }
        sb.append("- Inventory slots used: ").append(itemCount).append(" / 36\n");

        return sb.toString();
    }

    private String callLLM(String provider, String systemPrompt, String userPrompt) {
        try {
            if (provider.equals("ollama")) {
                // Delegate to ollamaClient's raw API
                io.github.amithkoujalgi.ollama4j.core.OllamaAPI api =
                        new io.github.amithkoujalgi.ollama4j.core.OllamaAPI("http://localhost:11434");
                var messages = java.util.List.of(
                        new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                                io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole.SYSTEM,
                                systemPrompt),
                        new io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessage(
                                io.github.amithkoujalgi.ollama4j.core.models.chat.OllamaChatMessageRole.USER,
                                userPrompt)
                );
                var resp = net.shasankp000.OllamaClient.OllamaAPIHelper.smartChat(
                        api, "http://localhost:11434",
                        AIPlayer.CONFIG.getSelectedLanguageModel(), messages);
                return resp.getContent();
            } else {
                LLMClient client = buildClient(provider);
                if (client == null) return null;
                return client.sendPrompt(systemPrompt, userPrompt);
            }
        } catch (Exception e) {
            LOGGER.error("[autonomous] LLM call failed: {}", e.getMessage());
            return null;
        }
    }

    private LLMClient buildClient(String provider) {
        if (provider.equals("player2")) {
            LOGGER.warn("[autonomous] Player2 provider not supported in autonomous mode (no chatCallback available here)");
            return null;
        }
        return LLMClientFactory.createClient(provider);
    }

    /**
     * Extracts a JSON array of strings from the LLM response.
     * Tolerates leading/trailing prose around the JSON array.
     */
    static List<String> parseGoalArray(String response) {
        try {
            // Find first '[' and last ']'
            int start = response.indexOf('[');
            int end   = response.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) return List.of();

            String json = response.substring(start, end + 1);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            List<String> goals = new java.util.ArrayList<>();
            for (JsonElement el : arr) {
                String goal = el.getAsString().trim();
                if (!goal.isEmpty()) goals.add(goal);
            }
            return goals;
        } catch (Exception e) {
            LOGGER.warn("[autonomous] JSON parse error: {} — raw: {}", e.getMessage(), response);
            return List.of();
        }
    }
}
