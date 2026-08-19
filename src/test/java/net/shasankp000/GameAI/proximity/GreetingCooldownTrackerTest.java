package net.shasankp000.GameAI.proximity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreetingCooldownTrackerTest {

    @AfterEach
    void clearGreetingHistory() {
        GreetingCooldownTracker.clear();
    }

    @Test
    void suppressesRepeatGreetingUntilCooldownExpires() {
        assertTrue(GreetingCooldownTracker.tryAcquire("Bot", "Player", 1_000L));
        assertFalse(GreetingCooldownTracker.tryAcquire("Bot", "Player", 1_001L));
        assertTrue(GreetingCooldownTracker.tryAcquire(
                "Bot", "Player", 1_000L + GreetingCooldownTracker.COOLDOWN_MS));
    }

    @Test
    void tracksEachBotAndPlayerPairIndependentlyAndCaseInsensitively() {
        assertTrue(GreetingCooldownTracker.tryAcquire("BotOne", "Alex", 1_000L));
        assertFalse(GreetingCooldownTracker.tryAcquire("botone", "alex", 1_001L));
        assertTrue(GreetingCooldownTracker.tryAcquire("BotTwo", "Alex", 1_001L));
        assertTrue(GreetingCooldownTracker.tryAcquire("BotOne", "Steve", 1_001L));
    }

    @Test
    void clearingOneBotDoesNotClearAnotherBotsHistory() {
        assertTrue(GreetingCooldownTracker.tryAcquire("BotOne", "Alex", 1_000L));
        assertTrue(GreetingCooldownTracker.tryAcquire("BotTwo", "Alex", 1_000L));

        GreetingCooldownTracker.clearBot("BOTONE");

        assertTrue(GreetingCooldownTracker.tryAcquire("BotOne", "Alex", 1_001L));
        assertFalse(GreetingCooldownTracker.tryAcquire("BotTwo", "Alex", 1_001L));
    }
}
