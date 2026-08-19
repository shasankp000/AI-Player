package net.shasankp000.GameAI.proximity;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared greeting history for all greeting sources.
 *
 * <p>Entries are scoped to a bot/player pair so multiple bots can greet the
 * same player independently. Player names are used because join messages do
 * not contain UUIDs.
 */
public final class GreetingCooldownTracker {

    public static final long COOLDOWN_MS = 5 * 60_000L;

    private record GreetingKey(String botName, String playerName) {}

    private static final ConcurrentHashMap<GreetingKey, Long> LAST_GREETINGS =
            new ConcurrentHashMap<>();

    private GreetingCooldownTracker() {}

    /**
     * Records a greeting when the pair is not cooling down.
     *
     * @return {@code true} when the caller may greet, or {@code false} when a
     *         recent greeting already exists
     */
    public static boolean tryAcquire(String botName, String playerName) {
        return tryAcquire(botName, playerName, System.currentTimeMillis());
    }

    static boolean tryAcquire(String botName, String playerName, long now) {
        if (botName == null || botName.isBlank() || playerName == null || playerName.isBlank()) {
            return false;
        }

        GreetingKey key = new GreetingKey(normalize(botName), normalize(playerName));
        AtomicBoolean acquired = new AtomicBoolean(false);
        LAST_GREETINGS.compute(key, (ignored, lastGreeting) -> {
            if (lastGreeting == null || now - lastGreeting >= COOLDOWN_MS) {
                acquired.set(true);
                return now;
            }
            return lastGreeting;
        });
        return acquired.get();
    }

    /** Removes greeting history belonging to a bot when its lifecycle ends. */
    public static void clearBot(String botName) {
        if (botName == null || botName.isBlank()) return;
        String normalizedBotName = normalize(botName);
        LAST_GREETINGS.keySet().removeIf(key -> key.botName().equals(normalizedBotName));
    }

    /** Clears all greeting history, intended for server shutdown. */
    public static void clear() {
        LAST_GREETINGS.clear();
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
