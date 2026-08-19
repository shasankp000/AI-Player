package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.GameAI.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Main planner orchestrator implementing beam-search refinement.
 * Generates and refines action plans using Markov chains and risk analysis.
 */
public class Planner {
    private static final Logger LOGGER = LoggerFactory.getLogger("planner");

    // Hyperparameters
    private static final int INITIAL_DRAFTS = 4;
    private static final int BEAM_WIDTH = 3;
    private static final int MAX_REFINEMENT_ITERS = 6;
    private static final double SAFE_THRESHOLD = 50.0;
    private static final double EXPLORATION_EPSILON = 0.15;
    private static final int MAX_PLAN_LENGTH = 12;
    private static final int MIN_PLAN_LENGTH = 3;

    private final MarkovChain2 markovChain;
    private final SequenceRiskAnalyzer riskAnalyzer;
    private final ExecutorService executor;
    private final Random random;

    public Planner(MarkovChain2 markovChain, RLAgent rlAgent) {
        this.markovChain = markovChain;
        this.riskAnalyzer = new SequenceRiskAnalyzer(rlAgent);
        this.executor = Executors.newFixedThreadPool(4,
            r -> new Thread(r, "PlannerWorker-" + System.identityHashCode(r)));
        this.random = new Random();
    }

    /**
     * Build an action plan for a goal.
     *
     * @param currentState Current game state
     * @param goalSpec Goal specification (goalId)
     * @return Optimized plan or null if failed
     */
    public Plan buildPlan(State currentState, short goalSpec) {
        long startTime = System.currentTimeMillis();

        LOGGER.info("Building plan for goal: {}", goalSpec);

        // Initialize shared state based on goal
        markovChain.clearSharedState();
        String goalName = GoalMapper.getGoalName(goalSpec);

        // Set goal-specific context in shared state
        if (goalName.equalsIgnoreCase("gather")) {
            markovChain.updateSharedState("targetBlockType", "minecraft:oak_log");
        } else if (goalName.equalsIgnoreCase("build")) {
            markovChain.updateSharedState("targetBlockType", "minecraft:stone");
        }

        // Debug: Check ActionRegistry status
        int registeredActions = ActionRegistry.getAllActionBytes().size();
        if (registeredActions <= 1) {
            LOGGER.error("❌ ActionRegistry has only {} actions registered! Cannot plan.", registeredActions);
            return null;
        }
        LOGGER.debug("ActionRegistry has {} actions available", registeredActions);

        // Step 1: Generate initial drafts in parallel
        List<Future<ScoredPlan>> draftFutures = new ArrayList<>();
        for (int i = 0; i < INITIAL_DRAFTS; i++) {
            draftFutures.add(executor.submit(() -> generateDraft(goalSpec, currentState)));
        }

        // Collect drafts
        List<ScoredPlan> drafts = new ArrayList<>();
        for (Future<ScoredPlan> future : draftFutures) {
            try {
                ScoredPlan draft = future.get(2, TimeUnit.SECONDS);
                if (draft != null) {
                    drafts.add(draft);
                }
            } catch (Exception e) {
                LOGGER.warn("Draft generation failed: {}", e.getMessage());
            }
        }

        if (drafts.isEmpty()) {
            LOGGER.warn("No valid drafts generated");
            return null;
        }

        // Sort by score (lower is better)
        drafts.sort(Comparator.comparingDouble(sp -> sp.score));

        ScoredPlan bestDraft = drafts.get(0);
        LOGGER.info("Generated {} drafts, best score: {}", drafts.size(), String.format("%.2f", bestDraft.score));

        // Log best draft's actions for debugging
        if (LOGGER.isDebugEnabled() && !bestDraft.steps.isEmpty()) {
            LOGGER.debug("Best draft plan:");
            for (int i = 0; i < Math.min(5, bestDraft.steps.size()); i++) {
                PlannedStep step = bestDraft.steps.get(i);
                LOGGER.debug("  {}: {} (byte: {})", i + 1, step.actionName, step.actionId & 0xFF);
            }
        }

        // Step 2: Beam search refinement
        List<ScoredPlan> beam = new ArrayList<>();
        for (int i = 0; i < Math.min(BEAM_WIDTH, drafts.size()); i++) {
            beam.add(drafts.get(i));
        }

        ScoredPlan bestPlan = beam.get(0);

        for (int iter = 0; iter < MAX_REFINEMENT_ITERS; iter++) {
            LOGGER.debug("Refinement iteration {}/{}", iter + 1, MAX_REFINEMENT_ITERS);

            // Generate neighbors for each plan in beam
            List<ScoredPlan> neighbors = new ArrayList<>();
            for (ScoredPlan plan : beam) {
                neighbors.addAll(generateNeighbors(plan, goalSpec, currentState));
            }

            if (neighbors.isEmpty()) {
                break; // No improvements possible
            }

            // Sort all neighbors
            neighbors.sort(Comparator.comparingDouble(sp -> sp.score));

            // Update beam with top-K
            beam.clear();
            for (int i = 0; i < Math.min(BEAM_WIDTH, neighbors.size()); i++) {
                beam.add(neighbors.get(i));
            }

            // Check for improvement
            if (beam.get(0).score < bestPlan.score) {
                bestPlan = beam.get(0);
                LOGGER.debug("Improved plan score: {}", String.format("%.2f", bestPlan.score));
            }

            // Early stop if safe enough
            if (bestPlan.score < SAFE_THRESHOLD) {
                LOGGER.info("Plan reached safe threshold");
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Planning completed in {}ms, final score: {}", duration, String.format("%.2f", bestPlan.score));

        // Create final plan object
        if (bestPlan.score < 200.0) { // Reject plans that are too risky
            Plan plan = new Plan(UUID.randomUUID(), goalSpec, bestPlan.steps);
            plan.estimatedRisk = bestPlan.score;

            // Log final plan
            LOGGER.info("✓ Final plan with {} steps:", plan.length());
            for (int i = 0; i < Math.min(10, plan.steps.size()); i++) {
                PlannedStep step = plan.steps.get(i);
                LOGGER.info("  Step {}: {}", i + 1, step.actionName);
            }

            return plan;
        } else {
            LOGGER.warn("Best plan score too high ({}), rejecting", String.format("%.2f", bestPlan.score));
            return null;
        }
    }

    /**
     * Generate a single draft plan.
     */
    private ScoredPlan generateDraft(short goalId, State state) {
        int length = MIN_PLAN_LENGTH + random.nextInt(MAX_PLAN_LENGTH - MIN_PLAN_LENGTH);

        List<PlannedStep> steps = markovChain.draftPlan(
            goalId, state, length, EXPLORATION_EPSILON);

        if (steps.isEmpty()) {
            return null;
        }

        double score = riskAnalyzer.scorePlan(steps, state, goalId);
        return new ScoredPlan(steps, score);
    }

    /**
     * Generate neighbor plans via local edits.
     */
    private List<ScoredPlan> generateNeighbors(ScoredPlan parent, short goalId, State state) {
        List<ScoredPlan> neighbors = new ArrayList<>();

        // Strategy 1: Replace a segment with Markov resample
        if (parent.steps.size() >= 3) {
            ScoredPlan neighbor = replaceSegment(parent, goalId, state);
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }

        // Strategy 2: Insert safety action (eat, shield, torch)
        ScoredPlan safetyNeighbor = insertSafetyAction(parent, state);
        if (safetyNeighbor != null) {
            neighbors.add(safetyNeighbor);
        }

        // Strategy 3: Delete duplicate/redundant actions
        ScoredPlan dedupNeighbor = removeDuplicates(parent, goalId, state);
        if (dedupNeighbor != null) {
            neighbors.add(dedupNeighbor);
        }

        return neighbors;
    }

    /**
     * Replace a random segment with Markov-generated sequence.
     */
    private ScoredPlan replaceSegment(ScoredPlan parent, short goalId, State state) {
        List<PlannedStep> steps = new ArrayList<>(parent.steps);

        if (steps.size() < 3) {
            return null;
        }

        // Pick random segment to replace
        int start = random.nextInt(steps.size() - 2);
        int end = start + 1 + random.nextInt(Math.min(3, steps.size() - start));

        // Generate replacement
        List<PlannedStep> replacement = markovChain.draftPlan(
            goalId, state, end - start, EXPLORATION_EPSILON * 2);

        if (replacement.isEmpty()) {
            return null;
        }

        // Splice in replacement
        steps.subList(start, end).clear();
        steps.addAll(start, replacement);

        double score = riskAnalyzer.scorePlan(steps, state, goalId);
        return new ScoredPlan(steps, score);
    }

    /**
     * Insert a safety action at a risky point.
     */
    private ScoredPlan insertSafetyAction(ScoredPlan parent, State state) {
        List<PlannedStep> steps = new ArrayList<>(parent.steps);

        // Find highest risk step
        int maxRiskIdx = 0;
        double maxRisk = 0.0;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).estimatedRisk > maxRisk) {
                maxRisk = steps.get(i).estimatedRisk;
                maxRiskIdx = i;
            }
        }

        // Choose safety action based on context
        PlannedStep safety = chooseSafetyAction(state);

        if (safety == null) {
            return null;
        }

        // Insert before risky action
        steps.add(maxRiskIdx, safety);

        double score = riskAnalyzer.scorePlan(steps, state, (short) 0);
        return new ScoredPlan(steps, score);
    }

    /**
     * Choose appropriate safety action.
     */
    private PlannedStep chooseSafetyAction(State state) {
        // Eat if hungry
        if (state.getBotHungerLevel() < 14) {
            byte eatAction = ActionRegistry.getActionByte("eat");
            if (eatAction != ActionRegistry.ACTION_UNKNOWN) {
                return new PlannedStep(eatAction, "eat", 0.0, null);
            }
        }

        // Shield if enemies nearby
        if (state.getNearbyEntities().stream().anyMatch(e -> e.isHostile())) {
            byte shieldAction = ActionRegistry.getActionByte("shield");
            if (shieldAction != ActionRegistry.ACTION_UNKNOWN) {
                return new PlannedStep(shieldAction, "shield", 0.0, null);
            }
        }

        // Retreat if low health
        if (state.getBotHealth() < 10) {
            byte retreatAction = ActionRegistry.getActionByte("retreat");
            if (retreatAction != ActionRegistry.ACTION_UNKNOWN) {
                return new PlannedStep(retreatAction, "retreat", 0.0, null);
            }
        }

        return null;
    }

    /**
     * Remove duplicate or redundant actions.
     */
    private ScoredPlan removeDuplicates(ScoredPlan parent, short goalId, State state) {
        List<PlannedStep> steps = new ArrayList<>();

        PlannedStep prev = null;
        int consecCount = 0;

        for (PlannedStep step : parent.steps) {
            if (prev != null && step.actionId == prev.actionId) {
                consecCount++;
                // Keep max 2 consecutive identical actions
                if (consecCount < 2) {
                    steps.add(step);
                }
            } else {
                steps.add(step);
                consecCount = 0;
            }
            prev = step;
        }

        if (steps.size() == parent.steps.size()) {
            return null; // No change
        }

        double score = riskAnalyzer.scorePlan(steps, state, goalId);
        return new ScoredPlan(steps, score);
    }

    /**
     * Shutdown executor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ===== INNER CLASSES =====

    /**
     * Scored plan for beam search.
     */
    private static class ScoredPlan {
        final List<PlannedStep> steps;
        final double score;

        ScoredPlan(List<PlannedStep> steps, double score) {
            this.steps = new ArrayList<>(steps);
            this.score = score;
        }
    }
}

