package net.shasankp000.GameAI.handoff;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shasankp000.GameAI.mood.MoodEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handles the "Smart Item Handoff" feature (Feature 4.1–4.2).
 *
 * <p>When a player throws an item that the bot picks up this class:
 * <ol>
 *   <li>Classifies the item into an {@link ItemTier}.</li>
 *   <li><b>Need-aware override</b>: if the raw tier is {@link ItemTier#JUNK} but the
 *       bot currently holds fewer than {@value #NEED_THRESHOLD} of that item, it is
 *       reclassified to {@link ItemTier#COMMON} — the bot actually wanted it.</li>
 *   <li>Applies a valence + arousal delta to the bot's {@link MoodEngine}.</li>
 *   <li>Sends a context-aware chat reaction from the bot back to the server.</li>
 * </ol>
 *
 * <h3>Mood impulse table</h3>
 * <pre>
 *  LEGENDARY  Δv=+0.40  Δa=+0.35   (bot becomes ELATED)
 *  EPIC       Δv=+0.25  Δa=+0.25   (bot becomes EXCITED/CONTENT)
 *  RARE       Δv=+0.12  Δa=+0.10   (gentle uplift)
 *  COMMON     Δv=+0.04  Δa=+0.02   (tiny positive tick)
 *  JUNK       Δv=-0.10  Δa=+0.05   (mild irritation / AGITATED lean)
 * </pre>
 *
 * <p>The JUNK impulse is only applied when the bot has {@value #NEED_THRESHOLD} or
 * more of that item already, meaning it genuinely doesn't need it.
 */
public final class ItemHandoffHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("item-handoff");
    private static final Random RNG = new Random();

    /**
     * If the bot's inventory contains fewer than this many of a JUNK-tier item,
     * the item is treated as COMMON (the bot actually needs it).
     * Set to 16 — half a stack — as a reasonable "running low" threshold.
     */
    private static final int NEED_THRESHOLD = 16;

    private ItemHandoffHandler() { /* static API only */ }

    // -----------------------------------------------------------------------
    // Mood impulse deltas per tier
    // -----------------------------------------------------------------------

    private static final Map<ItemTier, float[]> IMPULSE = Map.of(
        ItemTier.LEGENDARY, new float[]{+0.40f, +0.35f},
        ItemTier.EPIC,      new float[]{+0.25f, +0.25f},
        ItemTier.RARE,      new float[]{+0.12f, +0.10f},
        ItemTier.COMMON,    new float[]{+0.04f, +0.02f},
        ItemTier.JUNK,      new float[]{-0.10f, +0.05f}
    );

    // -----------------------------------------------------------------------
    // Chat reaction pools
    // -----------------------------------------------------------------------

    private static final Map<ItemTier, List<String>> REACTIONS = Map.of(
        ItemTier.LEGENDARY, List.of(
            "Oh wow — %s just gave me a %s! I... I can't believe it! 🤩",
            "Wait, is this actually a %s?! %s you absolute legend!! 🎉",
            "A %s?! From %s?! My day just got SO much better! ✨"
        ),
        ItemTier.EPIC, List.of(
            "%s gave me a %s — nice one! Really appreciate that 😄",
            "Oh, a %s! Thanks %s, this will come in very handy!",
            "Ooh, %s dropped a %s for me! That's pretty epic 🔥"
        ),
        ItemTier.RARE, List.of(
            "Thanks %s, I'll make good use of that %s!",
            "%s tossed me a %s — cheers! 👍",
            "A %s, nice. Appreciated, %s!"
        ),
        ItemTier.COMMON, List.of(
            "Got a %s from %s, cool.",
            "%s gave me a %s. I'll hold onto it.",
            "Thanks for the %s, %s."
        ),
        // Junk reactions — only shown when the bot truly doesn't need the item
        ItemTier.JUNK, List.of(
            "%s threw a %s at me... really? 😒",
            "Ugh, %s — a %s? You couldn't find anything better?",
            "A %s from %s. Gee, thanks for the... thoughtful gift. 🙄"
        )
    );

    /**
     * Reactions used when a JUNK item is reclassified to COMMON because the
     * bot actually needed it.  More grateful than standard COMMON but not as
     * enthusiastic as RARE.
     */
    private static final List<String> NEEDED_JUNK_REACTIONS = List.of(
        "Oh, %s gave me some %s — actually needed that, thanks!",
        "Thanks %s! I was running low on %s, perfect timing.",
        "%s came through with %s just when I needed it. Appreciate it!"
    );

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    /**
     * Called when the bot picks up an item that was thrown by a player.
     *
     * @param bot     the bot that picked up the item
     * @param thrower the player who threw the item ({@code null} if unknown)
     * @param stack   the item stack that was picked up
     */
    public static void onBotPickedUpItem(
            ServerPlayerEntity bot,
            ServerPlayerEntity thrower,
            ItemStack stack) {

        if (bot == null || stack == null || stack.isEmpty()) return;

        String botName     = bot.getName().getString();
        String itemName    = stack.getName().getString();
        String throwerName = (thrower != null) ? thrower.getName().getString() : "someone";

        ItemTier rawTier = ItemTier.of(stack);

        // ------------------------------------------------------------------
        // Need-aware override: reclassify JUNK → COMMON when the bot's
        // inventory has fewer than NEED_THRESHOLD of this item.
        // ------------------------------------------------------------------
        boolean neededJunk = false;
        ItemTier effectiveTier = rawTier;
        if (rawTier == ItemTier.JUNK) {
            int held = countItemInInventory(bot, stack);
            if (held < NEED_THRESHOLD) {
                effectiveTier = ItemTier.COMMON;
                neededJunk = true;
                LOGGER.info("[handoff] JUNK → COMMON override for '{}': bot only has {} (threshold {})",
                        itemName, held, NEED_THRESHOLD);
            }
        }

        // 1. Apply mood impulse (uses effective tier)
        float[] delta = IMPULSE.getOrDefault(effectiveTier, new float[]{0f, 0f});
        MoodEngine.applyDelta(botName, delta[0], delta[1]);

        LOGGER.info("[handoff] {} picked up {} (raw={} effective={}) from {} — mood Δv={} Δa={}",
                botName, itemName, rawTier, effectiveTier, throwerName, delta[0], delta[1]);

        // 2. Send chat reaction (only when thrown by a real player)
        if (thrower != null) {
            String reaction = pickReaction(effectiveTier, neededJunk, throwerName, itemName);
            if (bot.getServer() != null) {
                bot.getServer().execute(() -> {
                    try {
                        bot.getServer().getCommandManager().executeWithPrefix(
                            bot.getCommandSource().withSilent().withMaxLevel(4),
                            "/say " + reaction
                        );
                    } catch (Exception e) {
                        LOGGER.warn("[handoff] Failed to send chat reaction: {}", e.getMessage());
                    }
                });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Counts how many items matching {@code reference}'s item type the bot
     * currently holds across the entire inventory (hotbar + main + offhand).
     * This check runs <em>before</em> the picked-up stack is merged in, so it
     * reflects what the bot already had.
     */
    private static int countItemInInventory(ServerPlayerEntity bot, ItemStack reference) {
        Identifier refId = Registries.ITEM.getId(reference.getItem());
        int total = 0;
        for (int i = 0; i < bot.getInventory().size(); i++) {
            ItemStack slot = bot.getInventory().getStack(i);
            if (!slot.isEmpty() && Registries.ITEM.getId(slot.getItem()).equals(refId)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    /**
     * Picks a reaction template and formats it.
     *
     * @param effectiveTier the tier after need-aware override
     * @param neededJunk    {@code true} if raw tier was JUNK but reclassified
     * @param throwerName   name of the player who threw the item
     * @param itemName      display name of the item
     */
    private static String pickReaction(
            ItemTier effectiveTier,
            boolean neededJunk,
            String throwerName,
            String itemName) {

        List<String> pool;
        if (neededJunk) {
            // Use the specialised "needed junk" pool so the reaction is
            // clearly grateful rather than just generic COMMON indifference.
            pool = NEEDED_JUNK_REACTIONS;
        } else {
            pool = REACTIONS.getOrDefault(effectiveTier, REACTIONS.get(ItemTier.COMMON));
        }

        String template = pool.get(RNG.nextInt(pool.size()));
        try {
            return String.format(template, throwerName, itemName);
        } catch (Exception e) {
            return throwerName + " gave me " + itemName + ".";
        }
    }
}
