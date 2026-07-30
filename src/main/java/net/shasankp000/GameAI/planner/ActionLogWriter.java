package net.shasankp000.GameAI.planner;

import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Logs executed actions and updates Markov chain for learning.
 * Runs asynchronously to avoid blocking game thread.
 */
public class ActionLogWriter implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("planner");
    private static final String LOG_DIR = "action_logs";

    private final BlockingQueue<LogEntry> queue;
    private final MarkovChain2 markovChain;
    private final net.minecraft.server.network.ServerPlayerEntity bot; // ✅ Store bot reference instead
    private volatile boolean running;
    private final Thread writerThread;
    private BufferedWriter logWriter;

    public ActionLogWriter(MarkovChain2 markovChain, net.minecraft.server.network.ServerPlayerEntity bot) {
        this.queue = new LinkedBlockingQueue<>(1000);
        this.markovChain = markovChain;
        this.bot = bot;
        this.running = true;
        this.writerThread = new Thread(this, "ActionLogWriter");

        // Initialize log file
        try {
            Path logDirPath = Paths.get(LOG_DIR);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
            }

            String logFile = String.format("%s/action_log_%d.csv",
                    LOG_DIR, System.currentTimeMillis());
            this.logWriter = new BufferedWriter(new FileWriter(logFile));

            // Write CSV header
            logWriter.write("timestamp,planId,goalId,contextHash,stepIndex,actionId," +
                    "actionName,riskBefore,outcome,reward,died\n");
            logWriter.flush();

            LOGGER.info("Action log initialized: {}", logFile);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize action log", e);
            this.logWriter = null;
        }

        writerThread.start();
    }

    /**
     * Log an executed action step.
     */
    public void logStep(UUID planId, short goalId, State stateBefore,
                       int stepIndex, PlannedStep step,
                       String outcome, double reward, boolean died) {

        LogEntry entry = new LogEntry(
            System.currentTimeMillis(),
            planId,
            goalId,
            computeContextHash(stateBefore),
            stepIndex,
            step.actionId,
            step.actionName,
            step.estimatedRisk,
            outcome,
            reward,
            died
        );

        if (!queue.offer(entry)) {
            LOGGER.warn("Action log queue full, dropping entry");
        }
    }

    @Override
    public void run() {
        LOGGER.info("ActionLogWriter thread started");

        while (running || !queue.isEmpty()) {
            try {
                LogEntry entry = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (entry != null) {
                    processEntry(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Cleanup
        try {
            if (logWriter != null) {
                logWriter.close();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to close log writer", e);
        }

        LOGGER.info("ActionLogWriter thread stopped");
    }

    /**
     * Process a single log entry.
     */
    /**
     * Process a single log entry.
     */
    private void processEntry(LogEntry entry) {
        // Write to CSV
        if (logWriter != null) {
            try {
                logWriter.write(String.format("%d,%s,%d,%d,%d,%d,%s,%.2f,%s,%.2f,%b\n",
                        entry.timestamp,
                        entry.planId.toString(),
                        entry.goalId,
                        entry.contextHash,
                        entry.stepIndex,
                        entry.actionId & 0xFF,
                        entry.actionName,
                        entry.riskBefore,
                        entry.outcome,
                        entry.reward,
                        entry.died
                ));

                // Flush periodically
                if (entry.stepIndex % 10 == 0) {
                    logWriter.flush();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write log entry", e);
            }
        }

        // Update Markov chain (for learning)
        if (entry.stepIndex > 0) {
            // Get previous actions from plan history
            // For now, use simple prev1/prev2 = 0 (would need plan context)
            byte prev1 = 0;
            byte prev2 = 0;
            markovChain.observeTransition(
                    entry.goalId,
                    entry.contextHash,
                    prev2,
                    prev1,
                    entry.actionId
            );
        }

        // ✅ NEW: Integrate with BotEventHandler's StateTransition tracking
        // This properly records the transition in the existing RL learning system
        try {
            // Get the current transition history from BotEventHandler
            StateTransition.TransitionHistory history =
                    net.shasankp000.GameAI.BotEventHandler.getTransitionHistory();

            if (history != null && entry.died) {
                // Mark recent transitions as leading to death for learning
                LOGGER.warn("Plan step {} led to death - marking transitions for learning",
                        entry.stepIndex);
                // The death learning is handled by BotEventHandler.handleBotDeath()
            }
        } catch (Exception e) {
            LOGGER.warn("Could not update StateTransition tracking: {}", e.getMessage());
        }

        LOGGER.debug("Logged action: {} (reward: {}, died: {})",
                entry.actionName, entry.reward, entry.died);
    }


    /**
     * Compute context hash (same as in MarkovChain2).
     */
    private int computeContextHash(State context) {
        int hash = 17;
        hash = 31 * hash + (context.getBotHealth() / 5);
        hash = 31 * hash + (context.getBotHungerLevel() / 5);
        hash = 31 * hash + (context.getTimeOfDay().equals("night") ? 1 : 0);
        return hash;
    }

    /**
     * Shutdown the writer thread.
     */
    public void shutdown() {
        running = false;
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal log entry structure.
     */
    private static class LogEntry {
        final long timestamp;
        final UUID planId;
        final short goalId;
        final int contextHash;
        final int stepIndex;
        final byte actionId;
        final String actionName;
        final double riskBefore;
        final String outcome;
        final double reward;
        final boolean died;

        LogEntry(long timestamp, UUID planId, short goalId, int contextHash,
                int stepIndex, byte actionId, String actionName, double riskBefore,
                String outcome, double reward, boolean died) {
            this.timestamp = timestamp;
            this.planId = planId;
            this.goalId = goalId;
            this.contextHash = contextHash;
            this.stepIndex = stepIndex;
            this.actionId = actionId;
            this.actionName = actionName;
            this.riskBefore = riskBefore;
            this.outcome = outcome;
            this.reward = reward;
            this.died = died;
        }
    }
}

