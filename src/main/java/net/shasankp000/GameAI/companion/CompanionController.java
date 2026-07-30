package net.shasankp000.GameAI.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.shasankp000.AIPlayer;
import net.shasankp000.GameAI.autonomous.AutonomousGoalEngine;
import net.shasankp000.GameAI.autonomous.AutonomousManager;
import net.shasankp000.GameAI.autonomous.GoalQueueEntry;
import net.shasankp000.ChatUtils.ChatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-bot companion stances ({@link BotStance}) and applies the
 * side-effects of each stance transition.
 *
 * <h3>Thread-safety</h3>
 * All public methods are safe to call from any thread. The internal maps
 * use {@link ConcurrentHashMap} and state mutations are kept atomic enough
 * for the 2-second poll cadence used by {@link net.shasankp000.GameAI.autonomous.AutonomousScheduler}.
 *
 * <h3>Integration points</h3>
 * <ul>
 *   <li>{@link #setStance} — called by the {@code /bot stance} command and
 *       the chat-trigger parser in {@link net.shasankp000.GameAI.autonomous.WorldEventListener}.</li>
 *   <li>{@link #followTick} — called every 2 s by {@code AutonomousScheduler}'s
 *       drift-check thread; drives FOLLOW navigation.</li>
 *   <li>{@link #stayDriftCheck} — called every 2 s; handles STAY knockback recovery.</li>
 *   <li>{@link #onBotDespawn} — called by {@code BotEventHandler.onBotDespawn()} to
 *       evict state on death / despawn.</li>
 * </ul>
 */
public class CompanionController {

    private static final Logger LOGGER = LoggerFactory.getLogger("companion-controller");

    /** Priority injected for FOLLOW navigation goals — above WORLD_EVENT (10), below hard interrupts. */
    private static final int FOLLOW_GOAL_PRIORITY = 15;
    /** Priority injected for STAY return-to-anchor goals. */
    private static final int STAY_RETURN_PRIORITY = 20;

    /** Singleton instance. */
    private static final CompanionController INSTANCE = new CompanionController();

    // ── Per-bot state maps ────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, BotStance>  stances       = new ConcurrentHashMap<>();
    /** UUID of the player being followed (FOLLOW stance only). */
    private final ConcurrentHashMap<String, UUID>       followTargets = new ConcurrentHashMap<>();
    /** Anchor position recorded on STAY stance change. */
    private final ConcurrentHashMap<String, BlockPos>   anchorPos     = new ConcurrentHashMap<>();

    private CompanionController() {}

    public static CompanionController getInstance() {
        return INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the active stance for {@code botName}, defaulting to {@link BotStance#WANDER}
     * if none has been set.
     */
    public BotStance getStance(String botName) {
        return stances.getOrDefault(botName, BotStance.WANDER);
    }

    /**
     * Changes the stance for {@code botName} and applies all immediate side-effects.
     *
     * @param botName      Minecraft username of the bot.
     * @param newStance    The desired new stance.
     * @param targetPlayer For {@link BotStance#FOLLOW}: the player to follow.
     *                     For {@link BotStance#STAY} and {@link BotStance#WANDER}: ignored (pass {@code null}).
     */
    public void setStance(String botName, BotStance newStance, ServerPlayer targetPlayer) {
        BotStance previous = stances.put(botName, newStance);
        LOGGER.info("[companion] Bot '{}' stance: {} → {}", botName,
                previous == null ? "WANDER" : previous, newStance);

        AutonomousGoalEngine engine = AutonomousManager.getInstance().getEngine(botName);

        switch (newStance) {
            case FOLLOW -> {
                if (targetPlayer == null) {
                    LOGGER.warn("[companion] FOLLOW requested for '{}' but no target player supplied — ignoring", botName);
                    stances.put(botName, BotStance.WANDER);
                    return;
                }
                followTargets.put(botName, targetPlayer.getUUID());
                anchorPos.remove(botName);

                // Resume autonomous loop so navigation goals can execute
                if (engine != null) engine.setPlayerControlled(false);

                // Inject an immediate first navigation goal
                injectFollowGoal(botName, targetPlayer, engine);

                // Confirm in chat
                sendBotMessage(botName, "Following you, " + targetPlayer.getName().getString() + "! 👣");
            }

            case STAY -> {
                // Record current bot position as anchor
                ServerPlayer bot = resolveBot(botName);
                if (bot != null) {
                    anchorPos.put(botName, bot.blockPosition());
                }
                followTargets.remove(botName);

                // Pause the autonomous goal loop
                if (engine != null) engine.setPlayerControlled(true);

                sendBotMessage(botName, "I'll stay right here. 🛶");
            }

            case WANDER -> {
                followTargets.remove(botName);
                anchorPos.remove(botName);

                // Resume normal autonomous operation
                if (engine != null) engine.setPlayerControlled(false);

                sendBotMessage(botName, "Going to explore on my own! 🧭");
            }
        }
    }

    /**
     * Called every ~2 seconds by {@code AutonomousScheduler} for bots in
     * {@link BotStance#FOLLOW} stance.
     *
     * <p>Measures distance to the follow target:
     * <ul>
     *   <li>If the target is &gt; 5 blocks away, inject a navigate goal.</li>
     *   <li>If the target is within 3 blocks, do nothing (already close enough).</li>
     *   <li>If the target has left the server, automatically revert to WANDER.</li>
     * </ul>
     */
    public void followTick(String botName) {
        if (getStance(botName) != BotStance.FOLLOW) return;

        UUID targetUUID = followTargets.get(botName);
        if (targetUUID == null) return;

        ServerPlayer bot = resolveBot(botName);
        if (bot == null) return;

        // Resolve target player
        ServerPlayer target = AIPlayer.serverInstance == null ? null
                : AIPlayer.serverInstance.getPlayerList().getPlayer(targetUUID);

        if (target == null) {
            LOGGER.info("[companion] Follow target for '{}' has left the server — reverting to WANDER", botName);
            setStance(botName, BotStance.WANDER, null);
            return;
        }

        double distanceSq = bot.distanceToSqr(target);

        if (distanceSq > 25.0) { // 5 blocks
            AutonomousGoalEngine engine = AutonomousManager.getInstance().getEngine(botName);
            injectFollowGoal(botName, target, engine);
        }
        // Within 3 blocks — already close, no goal needed
    }

    /**
     * Called every ~2 seconds by {@code AutonomousScheduler} for bots in
     * {@link BotStance#STAY} stance.
     *
     * <p>If the bot has drifted more than 2 blocks from the recorded anchor
     * (e.g. due to knockback), injects a priority-20 return-to-anchor goal.
     */
    public void stayDriftCheck(String botName) {
        if (getStance(botName) != BotStance.STAY) return;

        BlockPos anchor = anchorPos.get(botName);
        if (anchor == null) return;

        ServerPlayer bot = resolveBot(botName);
        if (bot == null) return;

        double drift = Math.sqrt(bot.distanceToSqr(
                anchor.getX() + 0.5, bot.getY(), anchor.getZ() + 0.5));

        if (drift > 2.0) {
            LOGGER.info("[companion] Bot '{}' drifted {:.1f} blocks from anchor — injecting return goal",
                    botName, drift);
            AutonomousGoalEngine engine = AutonomousManager.getInstance().getEngine(botName);
            if (engine != null) {
                // Temporarily unlock so the goal executes, then re-lock
                engine.setPlayerControlled(false);
                engine.injectGoalWithPriority(
                        "navigate to " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ(),
                        STAY_RETURN_PRIORITY);
            }
        }
    }

    /**
     * Evicts all per-bot state. Call from {@code BotEventHandler.onBotDespawn()}.
     */
    public void onBotDespawn(String botName) {
        stances.remove(botName);
        followTargets.remove(botName);
        anchorPos.remove(botName);
        LOGGER.info("[companion] Evicted stance state for bot '{}'", botName);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void injectFollowGoal(String botName, ServerPlayer target, AutonomousGoalEngine engine) {
        if (engine == null) {
            LOGGER.warn("[companion] No engine found for bot '{}' — cannot inject follow goal", botName);
            return;
        }
        String goalText = "navigate to player " + target.getName().getString();
        engine.injectGoalWithPriority(goalText, FOLLOW_GOAL_PRIORITY);
        LOGGER.debug("[companion] Injected follow goal for '{}': '{}'", botName, goalText);
    }

    private static ServerPlayer resolveBot(String botName) {
        if (AIPlayer.serverInstance == null) return null;
        return AIPlayer.serverInstance.getPlayerList().getPlayerByName(botName);
    }

    private static void sendBotMessage(String botName, String message) {
        ServerPlayer bot = resolveBot(botName);
        if (bot == null) return;
        try {
            ChatUtils.sendChatMessages(
                    bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS),
                    message);
        } catch (Exception e) {
            LOGGER.warn("[companion] Could not send bot chat message: {}", e.getMessage());
        }
    }
}
