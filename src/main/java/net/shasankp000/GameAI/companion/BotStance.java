package net.shasankp000.GameAI.companion;

/**
 * Represents the three high-level movement/behaviour stances a bot can be in.
 *
 * <ul>
 *   <li>{@link #FOLLOW} — bot tracks a specific player, staying within 5 blocks.</li>
 *   <li>{@link #STAY}   — bot holds its current position; autonomous goal loop is
 *                          paused via {@code AutonomousGoalEngine.setPlayerControlled(true)}.</li>
 *   <li>{@link #WANDER} — default; bot runs its normal autonomous goal loop freely.</li>
 * </ul>
 *
 * Changed via {@link CompanionController#setStance(String, BotStance, net.minecraft.server.level.ServerPlayer)}
 * or the {@code /bot stance} command.
 */
public enum BotStance {

    /**
     * Bot follows a target player, periodically injecting a high-priority
     * navigation goal if it falls more than 5 blocks behind.
     */
    FOLLOW,

    /**
     * Bot holds position at a recorded anchor {@link net.minecraft.core.BlockPos}.
     * The autonomous goal loop is paused while this stance is active.
     * If the bot drifts more than 2 blocks (e.g. knocked back) it automatically
     * injects a return-to-anchor goal.
     */
    STAY,

    /**
     * Default stance. Bot runs its autonomous goal loop without any positional
     * constraint imposed by the companion system.
     */
    WANDER
}
