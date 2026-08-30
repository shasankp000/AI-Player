package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.shasankp000.AIPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Drives the STAY and FOLLOW stance correction loops for all active bots.
 *
 * <p>Design constraints respected:</p>
 * <ul>
 *   <li>Movement is server-sided and only starts when that bot has no active navigation session.</li>
 *   <li>Stop is not block-precise — STAY uses a {@value STAY_TRIGGER_DISTANCE}-block
 *       threshold before issuing a correction path.</li>
 *   <li>FOLLOW re-plans only when the target has moved > {@value FOLLOW_TRIGGER_DISTANCE}
 *       blocks from the bot's last re-path origin to avoid constant thrash.</li>
 * </ul>
 */
public class StanceController {

    private static final Logger LOGGER = LoggerFactory.getLogger("stance-controller");

    /** Minimum drift before STAY issues a correction path (blocks). */
    private static final double STAY_TRIGGER_DISTANCE  = 2.5;

    /**
     * How far the tracked player must move from the bot before FOLLOW
     * issues a new path (blocks).  Keeps FOLLOW calm when the player is
     * standing still or walking slowly.
     */
    private static final double FOLLOW_TRIGGER_DISTANCE = 5.0;

    /** Interval between stance correction checks (seconds). */
    private static final int CHECK_INTERVAL_SEC = 2;

    // -------------------------------------------------------------------------
    // Singleton scheduler shared across all bots
    // -------------------------------------------------------------------------

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> Thread.ofVirtual().name("stance-controller").unstarted(r));

    /** Tracks the last position from which a FOLLOW path was issued, per bot. */
    private static final Map<String, BlockPos> lastFollowPathOrigin = new ConcurrentHashMap<>();

    /** Whether the controller loop has been started. */
    private static volatile boolean started = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Start the global tick loop.  Safe to call multiple times — only the
     * first call actually starts the scheduler.
     */
    public static synchronized void start() {
        if (started) return;
        scheduler.scheduleAtFixedRate(
                StanceController::enqueueTick,
                CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
        started = true;
        LOGGER.info("[StanceController] Started (check interval={}s)", CHECK_INTERVAL_SEC);
    }

    /** Cancel all stance loops and flush any in-flight movement for the given bot. */
    public static void cancelStance(String botName) {
        BotStance.clearStance(botName);
        lastFollowPathOrigin.remove(botName);
        MinecraftServer server = AIPlayer.serverInstance;
        if (server != null) {
            ServerPlayer bot = server.getPlayerList().getPlayerByName(botName);
            if (bot != null) NavigationService.cancel(server, bot.getUUID(), "Stance cancelled");
        }
        LOGGER.info("[StanceController] Stance cancelled for bot '{}'", botName);
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private static void enqueueTick() {
        if (AIPlayer.serverInstance == null) return;
        MinecraftServer server = AIPlayer.serverInstance;
        server.execute(() -> tick(server));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer botEntity : server.getPlayerList().getPlayers()) {
            String botName = botEntity.getName().getString();
            BotStance.StanceState stance = BotStance.getStance(botName);

            switch (stance.mode()) {
                case STAY   -> tickStay(server, botEntity, botName, stance);
                case FOLLOW -> tickFollow(server, botEntity, botName, stance);
                case NONE   -> { /* nothing */ }
            }
        }
    }

    // -------------------------------------------------------------------------
    // STAY logic
    // -------------------------------------------------------------------------

    private static void tickStay(MinecraftServer server, ServerPlayer bot,
                                  String botName, BotStance.StanceState stance) {
        if (NavigationService.isNavigating(bot.getUUID())) {
            return;
        }

        BlockPos anchor = stance.anchorPos();
        if (anchor == null) return;

        BlockPos current = bot.blockPosition();
        double dist = Math.sqrt(current.distSqr(anchor));

        if (dist <= STAY_TRIGGER_DISTANCE) return;

        LOGGER.info("[StanceController] STAY correction for '{}': dist={} → pathing to anchor {}",
                botName, String.format("%.2f", dist), anchor);

        NavigationService.navigate(bot, anchor, NavigationOptions.of(false))
                .thenAccept(result -> LOGGER.debug("[StanceController] STAY navigation for '{}' ended: {}",
                        botName, result.status()));
    }

    // -------------------------------------------------------------------------
    // FOLLOW logic
    // -------------------------------------------------------------------------

    private static void tickFollow(MinecraftServer server, ServerPlayer bot,
                                    String botName, BotStance.StanceState stance) {
        if (NavigationService.isNavigating(bot.getUUID())) {
            return;
        }

        String targetName = stance.followTarget();
        if (targetName == null) return;

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            LOGGER.warn("[StanceController] FOLLOW target '{}' not found — stance kept", targetName);
            return;
        }

        BlockPos botPos    = bot.blockPosition();
        BlockPos targetPos = target.blockPosition();
        double distToTarget = Math.sqrt(botPos.distSqr(targetPos));

        if (distToTarget <= FOLLOW_TRIGGER_DISTANCE) return;

        BlockPos lastOrigin = lastFollowPathOrigin.get(botName);
        if (lastOrigin != null) {
            double originToTarget = Math.sqrt(lastOrigin.distSqr(targetPos));
            if (originToTarget < FOLLOW_TRIGGER_DISTANCE) return;
        }

        LOGGER.info("[StanceController] FOLLOW path for '{}' → target '{}' at {} (dist={})",
                botName, targetName, targetPos, String.format("%.2f", distToTarget));

        lastFollowPathOrigin.put(botName, targetPos);

        NavigationService.navigate(bot, targetPos, NavigationOptions.of(false))
                .thenAccept(result -> LOGGER.debug("[StanceController] FOLLOW navigation for '{}' ended: {}",
                        botName, result.status()));
    }
}
