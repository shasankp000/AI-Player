package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningToolTest {

    @Test
    void rejectsNonPositiveAndNonFiniteDestroyProgress() {
        assertEquals(-1, MiningTool.adaptiveDeadlineTicks(0.0F));
        assertEquals(-1, MiningTool.adaptiveDeadlineTicks(-1.0F));
        assertEquals(-1, MiningTool.adaptiveDeadlineTicks(Float.NaN));
        assertEquals(-1, MiningTool.adaptiveDeadlineTicks(Float.POSITIVE_INFINITY));
    }

    @Test
    void adaptiveDeadlineHasFiveSecondFloor() {
        assertEquals(100, MiningTool.adaptiveDeadlineTicks(1.0F));
        assertEquals(100, MiningTool.adaptiveDeadlineTicks(0.25F));
    }

    @Test
    void adaptiveDeadlineAllowsSlowMiningWithSafetyMargin() {
        float progressPerTick = 0.01F;
        int expectedTicks = (int) Math.ceil(1.0D / progressPerTick);
        assertEquals(expectedTicks * 2 + 40,
                MiningTool.adaptiveDeadlineTicks(progressPerTick));
    }

    @Test
    void adaptiveDeadlineHasTwoMinuteCeiling() {
        assertEquals(2_400, MiningTool.adaptiveDeadlineTicks(0.00001F));
    }

    @Test
    void generationGuardRejectsStaleCancellation() {
        assertTrue(MiningTool.generationMatches(42L, 42L));
        assertFalse(MiningTool.generationMatches(43L, 42L));
    }

    @Test
    void foodPauseDoesNotConsumeTheMiningDeadline() {
        assertEquals(75, MiningTool.advanceActiveTicks(75, true));
        assertEquals(76, MiningTool.advanceActiveTicks(75, false));
        assertFalse(MiningTool.deadlineExceeded(100, 100));
        assertTrue(MiningTool.deadlineExceeded(101, 100));
    }

    @Test
    void miningResultProvidesStableFunctionCallerMessages() {
        BlockPos target = new BlockPos(1, 64, 2);
        MiningResult success = MiningResult.success(target);
        MiningResult timeout = MiningResult.failure(
                MiningResult.Status.TIMED_OUT, target, "adaptive deadline reached");

        assertTrue(success.succeeded());
        assertEquals("Mining complete!", success.functionMessage());
        assertFalse(timeout.succeeded());
        assertEquals("⚠️ Failed to mine block: adaptive deadline reached", timeout.functionMessage());
    }
}
