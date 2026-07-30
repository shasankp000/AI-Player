package net.shasankp000.PathFinding;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active stance (NONE / STAY / FOLLOW) for each bot by name.
 *
 * <p>Stances are intentionally kept here, separate from PathTracer, so that
 * both the command layer and the StanceController can read/write them without
 * a circular dependency.</p>
 */
public class BotStance {

    public enum Mode { NONE, STAY, FOLLOW }

    /** Immutable snapshot of a bot's stance at a point in time. */
    public record StanceState(
            Mode   mode,
            @Nullable BlockPos  anchorPos,   // used by STAY
            @Nullable String    followTarget  // player name, used by FOLLOW
    ) {
        public static final StanceState NONE = new StanceState(Mode.NONE, null, null);
    }

    // One entry per bot (keyed by bot name)
    private static final Map<String, StanceState> stances = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------

    public static void setStay(String botName, BlockPos anchor) {
        stances.put(botName, new StanceState(Mode.STAY, anchor, null));
    }

    public static void setFollow(String botName, String targetPlayerName) {
        stances.put(botName, new StanceState(Mode.FOLLOW, null, targetPlayerName));
    }

    public static void clearStance(String botName) {
        stances.put(botName, StanceState.NONE);
    }

    public static StanceState getStance(String botName) {
        return stances.getOrDefault(botName, StanceState.NONE);
    }

    public static boolean hasActiveStance(String botName) {
        return getStance(botName).mode() != Mode.NONE;
    }
}
