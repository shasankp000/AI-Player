package net.shasankp000.GameAI.autonomous;

import net.shasankp000.GameAI.companion.CompanionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the periodic background tasks for a single bot's autonomous system.
 *
 * <h3>Scheduled tasks</h3>
 * <ol>
 *   <li><b>Idle re-plan (every 60 s):</b> if the goal queue is empty, asks
 *       {@link AutonomousGoalEngine#triggerReplan()} to fetch a fresh LLM plan.</li>
 *   <li><b>Drift-check / companion tick (every 2 s):</b> runs the companion
 *       stance ticks so FOLLOW bots navigate toward their target player and
 *       STAY bots return to their anchor if knocked back.</li>
 * </ol>
 */
public class AutonomousScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("autonomous-scheduler");

    private final AutonomousGoalEngine engine;
    private final String               botName;

    private final ScheduledExecutorService scheduler;

    public AutonomousScheduler(AutonomousGoalEngine engine, String botName) {
        this.engine  = engine;
        this.botName = botName;
        this.scheduler = Executors.newScheduledThreadPool(1,
                r -> Thread.ofVirtual().name("autonomous-scheduler-" + botName).unstarted(r));
    }

    /** Start all periodic tasks. */
    public void start() {
        // Task 1: idle re-plan every 60 seconds
        scheduler.scheduleAtFixedRate(
                this::idleReplanTask,
                60, 60, TimeUnit.SECONDS
        );

        // Task 2: drift-check + companion stance tick every 2 seconds
        scheduler.scheduleAtFixedRate(
                this::driftCheckTask,
                2, 2, TimeUnit.SECONDS
        );

        LOGGER.info("[autonomous-scheduler] Started for bot '{}'", botName);
    }

    /** Stop all scheduled tasks. */
    public void shutdown() {
        scheduler.shutdownNow();
        LOGGER.info("[autonomous-scheduler] Shut down for bot '{}'", botName);
    }

    // -------------------------------------------------------------------------
    // Scheduled task implementations
    // -------------------------------------------------------------------------

    /**
     * If the goal queue is empty (bot has nothing to do), trigger a fresh
     * LLM re-plan so the bot is never idle for long.
     */
    private void idleReplanTask() {
        try {
            if (engine.queueSize() == 0 && !engine.isPlayerControlled()
                    && !engine.isExecutingGoal() && !engine.isBotSleeping()) {
                LOGGER.info("[autonomous-scheduler] Queue empty for '{}' — triggering re-plan", botName);
                engine.triggerReplan();
            }
        } catch (Exception e) {
            LOGGER.error("[autonomous-scheduler] Re-plan task error for '{}': {}", botName, e.getMessage(), e);
        }
    }

    /**
     * Drives companion stance side-effects every 2 seconds:
     * <ul>
     *   <li>{@link CompanionController#followTick(String)} — re-injects a
     *       navigate goal if the bot has drifted more than 5 blocks from its
     *       follow target.  No-op if stance is not {@code FOLLOW}.</li>
     *   <li>{@link CompanionController#stayDriftCheck(String)} — re-injects a
     *       return-to-anchor goal if the bot has been knocked more than 2
     *       blocks from its anchor.  No-op if stance is not {@code STAY}.</li>
     * </ul>
     */
    private void driftCheckTask() {
        try {
            CompanionController companion = CompanionController.getInstance();
            companion.followTick(botName);
            companion.stayDriftCheck(botName);
        } catch (Exception e) {
            LOGGER.error("[autonomous-scheduler] Drift-check task error for '{}': {}", botName, e.getMessage(), e);
        }
    }
}
