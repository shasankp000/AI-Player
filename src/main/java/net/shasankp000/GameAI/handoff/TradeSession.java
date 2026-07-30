package net.shasankp000.GameAI.handoff;

import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * Holds the pending trade state for one player.
 *
 * Lifecycle
 * ─────────
 * 1. Player throws item at bot while sneaking  → session created (OFFERED)
 * 2. Bot evaluates and announces a counter-offer in chat
 * 3. Player throws another item at bot          → DELIVERED / FAILED
 * 4. Bot throws back its counter-offer item     → session removed
 *
 * Sessions expire after EXPIRY_TICKS server ticks (~30 s at 20 tps).
 */
public class TradeSession {

    /** How long a pending session lives before it is silently discarded. */
    public static final int EXPIRY_TICKS = 600; // 30 seconds

    public enum Phase { OFFERED, AWAITING_DELIVERY }

    /** UUID of the human player who initiated the trade. */
    public final UUID playerUuid;

    /** A copy of the first item the player threw (the offer). */
    public final ItemStack offeredItem;

    /** The item the bot is willing to give in return (computed by TradeEvaluator). */
    public final ItemStack counterOfferItem;

    /** Server tick at which this session was created (used for expiry). */
    public final long createdAtTick;

    /** Current phase of this session. */
    public Phase phase;

    public TradeSession(UUID playerUuid,
                        ItemStack offeredItem,
                        ItemStack counterOfferItem,
                        long createdAtTick) {
        this.playerUuid      = playerUuid;
        this.offeredItem     = offeredItem.copy();
        this.counterOfferItem = counterOfferItem.copy();
        this.createdAtTick   = createdAtTick;
        this.phase           = Phase.OFFERED;
    }

    /** Returns true if this session has lived longer than {@link #EXPIRY_TICKS}. */
    public boolean isExpired(long currentTick) {
        return (currentTick - createdAtTick) > EXPIRY_TICKS;
    }
}
