package net.shasankp000.GameAI.autonomous;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;
import net.shasankp000.GameAI.companion.BotStance;
import net.shasankp000.GameAI.companion.CompanionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the raw server chat stream for socially significant events and
 * injects conversational goal strings into the {@link AutonomousGoalEngine}.
 *
 * <p>This class is purely a <em>social</em> reactor -- it never injects
 * survival or motor goals.  Those remain the responsibility of the RL system.
 *
 * <p>Additionally, it parses <em>stance trigger phrases</em> from player
 * chat directed at the bot (e.g. {@code "follow me"}, {@code "stay here"})
 * and delegates stance changes to {@link CompanionController}.
 *
 * <h3>Usage</h3>
 * Call {@link #process(String)} from {@code BotEventHandler} every time a
 * raw chat/system message arrives from the server.  If the message matches a
 * known pattern the listener injects an appropriate conversational goal via
 * {@link AutonomousGoalEngine#injectUrgentGoal(String)}.
 *
 * <h3>Pattern table (social)</h3>
 * <pre>
 * Server message pattern                      -&gt; Injected goal
 * -------------------------------------------------------------------------
 * &lt;player&gt; has made the advancement [X]      -&gt; congratulate &lt;player&gt; on getting [X] in chat
 * &lt;player&gt; was slain by / died               -&gt; express sympathy to &lt;player&gt; in chat
 * &lt;player&gt; joined the game                   -&gt; greet &lt;player&gt; in chat
 * &lt;player&gt; left the game                    -&gt; say goodbye to &lt;player&gt; in chat
 * &lt;player&gt; found [diamond/netherite/...item] -&gt; react to &lt;player&gt; finding [item] in chat
 * &lt;player&gt; entered the Nether               -&gt; wish &lt;player&gt; luck in the Nether in chat
 * &lt;player&gt; has reached the goal [X]         -&gt; cheer on &lt;player&gt; in chat
 * </pre>
 *
 * <h3>Stance trigger phrases (chat-driven)</h3>
 * <pre>
 * "follow me" / "follow &lt;player&gt;"  -&gt; FOLLOW stance
 * "stay here" / "stop moving"      -&gt; STAY stance
 * "wander" / "go explore" / ...    -&gt; WANDER stance
 * </pre>
 */
public class WorldEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("world-event-listener");

    // -------------------------------------------------------------------------
    // Event pattern descriptors
    // -------------------------------------------------------------------------

    private record EventPattern(Pattern pattern, GoalBuilder builder) {
        @FunctionalInterface
        interface GoalBuilder {
            String build(Matcher m);
        }
    }

    private static final List<EventPattern> PATTERNS = List.of(

        // Advancement
        new EventPattern(
            Pattern.compile("^(\\S+) has made the advancement \\[(.+?)\\]"),
            m -> "congratulate " + m.group(1) + " on getting the advancement '" + m.group(2) + "' in chat"
        ),

        // Goal (end-screen advancement)
        new EventPattern(
            Pattern.compile("^(\\S+) has reached the goal \\[(.+?)\\]"),
            m -> "cheer on " + m.group(1) + " for reaching the goal '" + m.group(2) + "' in chat"
        ),

        // Challenge advancement
        new EventPattern(
            Pattern.compile("^(\\S+) has completed the challenge \\[(.+?)\\]"),
            m -> "congratulate " + m.group(1) + " on completing the challenge '" + m.group(2) + "' in chat"
        ),

        // Death -- "was slain by"
        new EventPattern(
            Pattern.compile("^(\\S+) was slain by (.+)"),
            m -> "express sympathy to " + m.group(1) + " for being slain by " + m.group(2) + " in chat"
        ),

        // Death -- generic variants (drowned, fell, blew up, burned, etc.)
        new EventPattern(
            Pattern.compile("^(\\S+) (drowned|fell|blew up|burned|froze|starved|suffocated|was shot|was pummeled|was killed|went up in flames|tried to swim in lava|died)"),
            m -> "express sympathy to " + m.group(1) + " who " + m.group(2) + " in chat"
        ),

        // Player joined
        new EventPattern(
            Pattern.compile("^(\\S+) joined the game"),
            m -> "greet " + m.group(1) + " in chat"
        ),

        // Player left
        new EventPattern(
            Pattern.compile("^(\\S+) left the game"),
            m -> "say goodbye to " + m.group(1) + " in chat"
        ),

        // Entered the Nether
        new EventPattern(
            Pattern.compile("^(\\S+) entered the Nether"),
            m -> "wish " + m.group(1) + " luck in the Nether in chat"
        ),

        // Found notable item (diamond, netherite, etc.) -- typical server plugin announcement
        new EventPattern(
            Pattern.compile("^(\\S+) (?:found|mined|discovered) (?:a |an |some )?(diamond|netherite|ancient debris|emerald)",
                            Pattern.CASE_INSENSITIVE),
            m -> "react excitedly to " + m.group(1) + " finding " + m.group(2) + " in chat"
        )
    );

    // -------------------------------------------------------------------------
    // Stance trigger patterns  (Feature 1.6)
    // -------------------------------------------------------------------------

    /**
     * A stance trigger record pairs a compiled pattern with the {@link BotStance}
     * it maps to.  The first capture group (if present) is the target player name
     * for {@code FOLLOW} -- used to record who to follow in
     * {@link CompanionController}.
     */
    private record StanceTrigger(Pattern pattern, BotStance stance) {}

    /**
     * Ordered list of stance trigger patterns.
     *
     * <p>Chat lines are stripped of the leading {@code <sender>} before matching,
     * so these patterns match only the <em>message body</em> (lower-cased).
     *
     * <p>Each pattern is tried in order; the first match wins.
     */
    private static final List<StanceTrigger> STANCE_TRIGGERS = List.of(

        // "follow me"  -- follow the message sender (resolved by applyStanceTrigger)
        new StanceTrigger(
            Pattern.compile("\\bfollow\\s+me\\b", Pattern.CASE_INSENSITIVE),
            BotStance.FOLLOW
        ),

        // "follow <player>"  -- follow a named player
        new StanceTrigger(
            Pattern.compile("\\bfollow\\s+(\\S+)", Pattern.CASE_INSENSITIVE),
            BotStance.FOLLOW
        ),

        // "stay here" / "stop moving" / "stay put" / "don't move"
        new StanceTrigger(
            Pattern.compile("\\b(stay\\s+here|stop\\s+moving|stay\\s+put|don'?t\\s+move)\\b",
                            Pattern.CASE_INSENSITIVE),
            BotStance.STAY
        ),

        // "wander" / "go explore" / "do your thing" / "roam around" / "be free"
        new StanceTrigger(
            Pattern.compile("\\b(wander|go\\s+explore|do\\s+your\\s+thing|roam\\s+around|be\\s+free)\\b",
                            Pattern.CASE_INSENSITIVE),
            BotStance.WANDER
        )
    );

    // -------------------------------------------------------------------------
    // Instance
    // -------------------------------------------------------------------------

    private final AutonomousGoalEngine engine;

    /** Name of the bot itself -- used to avoid the bot reacting to its own messages. */
    private final String botName;

    public WorldEventListener(AutonomousGoalEngine engine, String botName) {
        this.engine  = engine;
        this.botName = botName;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feed a raw server message into the listener.
     * Call this from {@code BotEventHandler} on every incoming chat/system message.
     *
     * @param rawMessage The full raw text of the server message.
     */
    public void process(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;

        // Strip colour codes and [Server] prefix
        String stripped = rawMessage
                .replaceAll("\u00a7.", "")           // strip Minecraft colour codes
                .replaceAll("^\\[Server]\\s*", "")    // strip [Server] prefix
                .trim();

        // Don't let the bot react to its own messages
        if (stripped.startsWith(botName + " ") || stripped.startsWith("[" + botName + "]")) {
            return;
        }

        // -- 1. Stance trigger check (chat-driven, Feature 1.6) ----------------
        // Vanilla chat format: "<PlayerName> message body"
        Matcher chatMatcher = Pattern.compile("^<(\\S+)>\\s+(.+)$").matcher(stripped);
        if (chatMatcher.find()) {
            String sender      = chatMatcher.group(1);
            String messageBody = chatMatcher.group(2);

            if (applyStanceTrigger(sender, messageBody)) {
                return; // stance handled -- skip social goal injection
            }
        }

        // -- 2. Social event goal injection ------------------------------------
        for (EventPattern ep : PATTERNS) {
            Matcher m = ep.pattern().matcher(stripped);
            if (m.find()) {
                String goal = ep.builder().build(m);
                LOGGER.info("[world-event] '{}' -> injecting goal: '{}'", stripped, goal);
                engine.injectUrgentGoal(goal);
                return; // one event -> one goal; don't stack multiple reactions
            }
        }
    }

    // -------------------------------------------------------------------------
    // Stance trigger helpers  (Feature 1.6)
    // -------------------------------------------------------------------------

    /**
     * Attempts to match {@code messageBody} against every {@link #STANCE_TRIGGERS}
     * entry.  On a match, resolves the target player name to a
     * {@link ServerPlayerEntity} (required by
     * {@link CompanionController#setStance(String, BotStance, ServerPlayerEntity)}),
     * delegates the stance change, and injects a brief acknowledgement goal.
     *
     * @param sender      The player who sent the chat message.
     * @param messageBody The body of the chat message (everything after {@code <sender> }).
     * @return {@code true} if a stance trigger was matched and applied; {@code false}
     *         if no trigger matched and normal social processing should continue.
     */
    private boolean applyStanceTrigger(String sender, String messageBody) {
        for (StanceTrigger trigger : STANCE_TRIGGERS) {
            Matcher m = trigger.pattern().matcher(messageBody);
            if (!m.find()) continue;

            BotStance newStance = trigger.stance();
            CompanionController companion = CompanionController.getInstance();

            switch (newStance) {
                case FOLLOW -> {
                    // Determine target name: explicit capture group(1) or fall back to sender
                    String targetName = sender;
                    try {
                        String captured = m.group(1);
                        if (captured != null && !captured.equalsIgnoreCase("me")) {
                            targetName = captured;
                        }
                    } catch (IndexOutOfBoundsException ignored) {
                        // pattern has no capture group -- targetName stays as sender
                    }

                    // Resolve String -> ServerPlayerEntity
                    ServerPlayerEntity targetPlayer = resolvePlayer(targetName);
                    if (targetPlayer == null) {
                        LOGGER.warn("[stance] FOLLOW requested but player '{}' is not online -- ignoring", targetName);
                        return true; // trigger matched, but player offline; suppress further processing
                    }

                    companion.setStance(botName, BotStance.FOLLOW, targetPlayer);
                    LOGGER.info("[stance] {} -> FOLLOW {}", botName, targetName);
                    engine.injectUrgentGoal("acknowledge that you will now follow " + targetName + " in chat");
                }
                case STAY -> {
                    companion.setStance(botName, BotStance.STAY, null);
                    LOGGER.info("[stance] {} -> STAY", botName);
                    engine.injectUrgentGoal("acknowledge that you are staying put in chat");
                }
                case WANDER -> {
                    companion.setStance(botName, BotStance.WANDER, null);
                    LOGGER.info("[stance] {} -> WANDER", botName);
                    engine.injectUrgentGoal("acknowledge that you will wander freely in chat");
                }
            }

            return true; // trigger matched -- stop processing
        }

        return false; // no stance trigger matched
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a player name to a live {@link ServerPlayerEntity}.
     * Returns {@code null} if the server is unavailable or the player is offline.
     */
    private static ServerPlayerEntity resolvePlayer(String playerName) {
        if (AIPlayer.serverInstance == null) return null;
        return AIPlayer.serverInstance.getPlayerManager().getPlayer(playerName);
    }
}
