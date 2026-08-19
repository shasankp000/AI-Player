package net.shasankp000.GameAI.autonomous;

import net.shasankp000.GameAI.proximity.GreetingCooldownTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry that owns every bot's {@link AutonomousGoalEngine},
 * {@link AutonomousScheduler}, and {@link WorldEventListener}.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 * Bot spawns / initialises
 *   └─ ollamaClient.initializeOllamaClient()  →  AutonomousManager.startBot(name, uuid)
 *
 * Any server message arrives
 *   └─ ServerChatEventBridge.onSystemMessage()  →  AutonomousManager.broadcastServerMessage(msg)
 *
 * Player talks directly to the bot
 *   └─ ollamaClient.runFromChat / LLMServiceHandler  →  AutonomousManager.setPlayerControlled(name, true)
 *      (after response is sent)                      →  AutonomousManager.setPlayerControlled(name, false)
 *
 * Server stops
 *   └─ AIPlayer.onInitialize()  SERVER_STOPPED event  →  AutonomousManager.stopAll()
 * </pre>
 */
public class AutonomousManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("autonomous-manager");

    /** Singleton instance. */
    private static final AutonomousManager INSTANCE = new AutonomousManager();

    /** Map of bot name → engine. */
    private final ConcurrentHashMap<String, AutonomousGoalEngine> engines = new ConcurrentHashMap<>();
    /** Map of bot name → scheduler. */
    private final ConcurrentHashMap<String, AutonomousScheduler> schedulers = new ConcurrentHashMap<>();
    /** Map of bot name → world event listener. */
    private final ConcurrentHashMap<String, WorldEventListener> listeners = new ConcurrentHashMap<>();

    private AutonomousManager() {}

    /** Returns the singleton. */
    public static AutonomousManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Bot lifecycle
    // -------------------------------------------------------------------------

    /**
     * Start the autonomous loop for a newly-initialised bot.
     * Safe to call multiple times for the same bot (idempotent — stops the
     * old instance before starting a fresh one if called again).
     *
     * @param botName The Minecraft username of the bot.
     * @param botUUID The UUID of the bot.
     */
    public void startBot(String botName, UUID botUUID) {
        // Tear down any prior instance for this bot
        stopBot(botName);

        LOGGER.info("[autonomous-manager] Starting autonomous loop for bot '{}'", botName);

        AutonomousGoalEngine engine = new AutonomousGoalEngine(botName, botUUID);
        AutonomousScheduler scheduler = new AutonomousScheduler(engine, botName);
        WorldEventListener listener = new WorldEventListener(engine, botName);

        engines.put(botName, engine);
        schedulers.put(botName, scheduler);
        listeners.put(botName, listener);

        engine.start();
        scheduler.start();

        LOGGER.info("[autonomous-manager] Autonomous loop started for bot '{}'", botName);
    }

    /**
     * Stop the autonomous loop for the given bot and release all resources.
     */
    public void stopBot(String botName) {
        AutonomousGoalEngine engine = engines.remove(botName);
        AutonomousScheduler scheduler = schedulers.remove(botName);
        listeners.remove(botName);
        GreetingCooldownTracker.clearBot(botName);

        if (scheduler != null) scheduler.shutdown();
        if (engine    != null) engine.shutdown();

        LOGGER.info("[autonomous-manager] Stopped autonomous loop for bot '{}'", botName);
    }

    /**
     * Stop all running bots. Called on server shutdown.
     */
    public void stopAll() {
        LOGGER.info("[autonomous-manager] Stopping all autonomous loops ({} bots)", engines.size());
        // Copy keys to avoid ConcurrentModificationException
        engines.keySet().forEach(this::stopBot);
    }

    // -------------------------------------------------------------------------
    // Player-control gate
    // -------------------------------------------------------------------------

    /**
     * Pause or resume a bot's autonomous loop.
     *
     * Call with {@code true} when a human is addressing the bot directly;
     * call with {@code false} when the interaction ends.
     */
    public void setPlayerControlled(String botName, boolean controlled) {
        AutonomousGoalEngine engine = engines.get(botName);
        if (engine != null) engine.setPlayerControlled(controlled);
    }

    // -------------------------------------------------------------------------
    // World event routing
    // -------------------------------------------------------------------------

    /**
     * Broadcast a raw server system message to every registered
     * {@link WorldEventListener}.
     *
     * Call this from {@link ServerChatEventBridge} for every system message
     * that arrives on the server (join/leave/death/advancement etc.).
     *
     * @param rawMessage Full raw text of the server message.
     */
    public void broadcastServerMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;
        listeners.values().forEach(listener -> listener.process(rawMessage));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns true if an autonomous loop is currently registered for {@code botName}. */
    public boolean isRunning(String botName) {
        return engines.containsKey(botName);
    }

    /**
     * Returns the {@link AutonomousGoalEngine} for a bot, or {@code null} if
     * the bot has no active autonomous loop.
     */
    public AutonomousGoalEngine getEngine(String botName) {
        return engines.get(botName);
    }
}
