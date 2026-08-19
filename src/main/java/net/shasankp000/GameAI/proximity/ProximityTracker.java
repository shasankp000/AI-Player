package net.shasankp000.GameAI.proximity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.GameAI.mood.AffectiveState;
import net.shasankp000.GameAI.mood.MoodEngine;
import net.shasankp000.GameAI.mood.MoodLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 5 — Player Proximity Awareness.
 */
public final class ProximityTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("proximity-tracker");
    private static final Random RNG = new Random();

    public static final double GREET_RADIUS = 6.0;
    public static final double LINGER_RADIUS = 4.0;
    public static final int LINGER_THRESHOLD_TICKS = 150;
    private static final int LINGER_THRESHOLD_SECONDS = 5;
    public static final long LINGER_COOLDOWN_MS = 120_000L;

    private static final float GREET_VALENCE_BOOST = 0.05f;
    private static final float GREET_AROUSAL_BOOST = 0.03f;

    private static final ConcurrentHashMap<UUID, Long>    lingerCooldowns = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> lingerTicks     = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Reaction pools  — every MoodLabel constant covered
    // -------------------------------------------------------------------------

    private static final Map<MoodLabel, List<String>> GREET_REACTIONS = Map.ofEntries(
        Map.entry(MoodLabel.ELATED, List.of(
            "Oh hey %s!! Great timing, I was just thinking about you! 😄",
            "%s! You're here! This just made my day even better! ✨",
            "%s! Perfect, I'm in such a good mood right now! What's up? 🎉"
        )),
        Map.entry(MoodLabel.EXCITED, List.of(
            "Hey %s! Good to see you!",
            "Oh, %s! What brings you over?",
            "Hey! %s is here! 👋"
        )),
        Map.entry(MoodLabel.CONTENT, List.of(
            "Hey %s.",
            "Oh, hi %s.",
            "'Sup %s."
        )),
        Map.entry(MoodLabel.SERENE, List.of(
            "Hello, %s.",
            "Oh, %s. Nice to see you.",
            "Hey %s, come to enjoy the quiet?"
        )),
        Map.entry(MoodLabel.NEUTRAL, List.of(
            "Hey %s.",
            "Hi %s.",
            "%s."
        )),
        Map.entry(MoodLabel.CALM, List.of(
            "Hi %s.",
            "Oh, %s. Everything okay?",
            "Hey %s."
        )),
        Map.entry(MoodLabel.BORED, List.of(
            "Oh... it's %s.",
            "Hey %s. Not really in the mood for much right now.",
            "%s. Hi."
        )),
        Map.entry(MoodLabel.AGITATED, List.of(
            "What do you want, %s?",
            "%s. Can I help you with something?",
            "Oh great, %s is here. What is it?"
        )),
        Map.entry(MoodLabel.DEPRESSED, List.of(
            "Not now, %s... I'm not okay.",
            "%s... could you maybe give me some space?",
            "Hey %s. Things are a bit rough right now."
        ))
    );

    private static final Map<MoodLabel, List<String>> LINGER_REACTIONS = Map.ofEntries(
        Map.entry(MoodLabel.ELATED, List.of(
            "Still here, %s? I love the company honestly! 😊",
            "You've been around for a bit, %s — not complaining at all!",
            "Staying close, %s? Honestly that's fine, I'm in a great mood!"
        )),
        Map.entry(MoodLabel.EXCITED, List.of(
            "You've been hanging around a while, %s. Something on your mind?",
            "Still here, %s? 👀",
            "Sticking close today huh, %s?"
        )),
        Map.entry(MoodLabel.CONTENT, List.of(
            "You're still here, %s. That's cool.",
            "Just standing around, %s?",
            "Quiet company is fine too, %s."
        )),
        Map.entry(MoodLabel.SERENE, List.of(
            "Still here, %s? It's peaceful, isn't it.",
            "You don't have to say anything, %s. Just nice to have company.",
            "Comfortable silence, %s."
        )),
        Map.entry(MoodLabel.NEUTRAL, List.of(
            "You've been standing there a while, %s.",
            "Everything okay, %s?",
            "You need something, %s?"
        )),
        Map.entry(MoodLabel.CALM, List.of(
            "Still around, %s? No worries.",
            "You've been close for a bit, %s. Everything alright?",
            "Quiet today, %s?"
        )),
        Map.entry(MoodLabel.BORED, List.of(
            "You've been close for a while now, %s... is something wrong?",
            "Not going anywhere, %s?",
            "Still around, %s. I'm a bit on edge, just so you know."
        )),
        Map.entry(MoodLabel.AGITATED, List.of(
            "Okay %s, do you actually need something or are you just hovering?",
            "You've been standing there for ages, %s. What do you want?",
            "Personal space, %s. Do you need something or not? 😤"
        )),
        Map.entry(MoodLabel.DEPRESSED, List.of(
            "%s please... I just need a moment.",
            "Still there, %s. I'm not great right now, just so you know.",
            "Could you give me a bit of room, %s? Not feeling my best."
        ))
    );

    private static final List<String> FALLBACK_GREET  = List.of("Hey %s.", "Hi %s.");
    private static final List<String> FALLBACK_LINGER = List.of("Still here, %s?", "Need something, %s?");

    private ProximityTracker() {}

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static void tick(ServerPlayerEntity bot, List<Entity> nearbyEntities) {
        if (bot == null || !bot.isAlive()) return;

        String botName = bot.getName().getString();
        long now = System.currentTimeMillis();

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof ServerPlayerEntity player)) continue;
            if (player.getUuid().equals(bot.getUuid())) continue;
            if (!player.networkHandler.isConnectionOpen()) continue;

            String playerName = player.getName().getString();
            UUID pid = player.getUuid();
            double dist = Math.sqrt(player.squaredDistanceTo(bot));

            // 1. Approach greeting
            if (dist <= GREET_RADIUS) {
                if (GreetingCooldownTracker.tryAcquire(botName, playerName)) {
                    fireReaction(bot, botName, playerName, GREET_REACTIONS, FALLBACK_GREET);
                    MoodEngine.applyDelta(botName, GREET_VALENCE_BOOST, GREET_AROUSAL_BOOST);
                    LOGGER.debug("[proximity] Greeted {} (dist={})", playerName, String.format("%.1f", dist));
                }
            }

            // 2. Linger tick / comment
            if (dist <= LINGER_RADIUS) {
                int ticks = lingerTicks.merge(pid, 1, Integer::sum);
                if (ticks >= LINGER_THRESHOLD_TICKS) {
                    long lastLinger = lingerCooldowns.getOrDefault(pid, 0L);
                    if (now - lastLinger >= LINGER_COOLDOWN_MS) {
                        lingerCooldowns.put(pid, now);
                        lingerTicks.put(pid, 0);
                        fireReaction(bot, botName, playerName, LINGER_REACTIONS, FALLBACK_LINGER);
                        LOGGER.debug("[proximity] Linger comment for {} ({}+ ticks)", playerName, LINGER_THRESHOLD_TICKS);
                    } else {
                        lingerTicks.put(pid, 0);
                    }
                }
            } else {
                lingerTicks.remove(pid);
            }
        }
    }

    public static void evict(UUID playerUuid) {
        lingerCooldowns.remove(playerUuid);
        lingerTicks.remove(playerUuid);
    }

    public static void clear() {
        GreetingCooldownTracker.clear();
        lingerCooldowns.clear();
        lingerTicks.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void fireReaction(
            ServerPlayerEntity bot,
            String botName,
            String playerName,
            Map<MoodLabel, List<String>> pool,
            List<String> fallback) {

        AffectiveState state = MoodEngine.get(botName);
        MoodLabel label = MoodLabel.from(state);

        List<String> reactions = pool.getOrDefault(label, fallback);
        String template = reactions.get(RNG.nextInt(reactions.size()));

        String message;
        try {
            message = String.format(template, playerName, botName);
        } catch (Exception e) {
            message = playerName + "!";
        }

        final String finalMessage = message;
        if (bot.getServer() != null) {
            bot.getServer().execute(() -> {
                try {
                    bot.getServer().getCommandManager().executeWithPrefix(
                        bot.getCommandSource().withSilent().withMaxLevel(4),
                        "/say " + finalMessage
                    );
                } catch (Exception e) {
                    LOGGER.warn("[proximity] Failed to send reaction: {}", e.getMessage());
                }
            });
        }
    }
}
