package com.tokenpilot.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBudgetManagerTest {

    @Test
    void normalUsageTracksInputAndOutput() {
        TokenBudgetManager manager = new TokenBudgetManager(10_000);

        manager.addUsage(1_200, 800);

        assertEquals(10_000, manager.getMaxBudget());
        assertEquals(2_000, manager.getTotalUsedTokens());
        assertEquals(8_000, manager.getRemainingBudget());
        assertTrue(manager.canFit(8_000));
        assertFalse(manager.canFit(8_001));
    }

    @Test
    void budgetExhaustionLeavesNoRoom() {
        TokenBudgetManager manager = new TokenBudgetManager(1_000);

        manager.addUsage(600, 400);

        assertEquals(0, manager.getRemainingBudget());
        assertTrue(manager.canFit(0));
        assertFalse(manager.canFit(1));
    }

    @Test
    void exceedingBudgetYieldsNegativeRemaining() {
        TokenBudgetManager manager = new TokenBudgetManager(500);

        manager.addUsage(300, 250);

        assertEquals(550, manager.getTotalUsedTokens());
        assertEquals(-50, manager.getRemainingBudget());
        assertFalse(manager.canFit(1));
    }

    @Test
    void zeroBudgetAllowsOnlyZeroEstimate() {
        TokenBudgetManager manager = new TokenBudgetManager(0);

        assertEquals(0, manager.getRemainingBudget());
        assertTrue(manager.canFit(0));
        assertFalse(manager.canFit(1));

        manager.addUsage(0, 0);
        assertEquals(0, manager.getTotalUsedTokens());
    }

    @Test
    void multipleRequestsAccumulateUsage() {
        TokenBudgetManager manager = new TokenBudgetManager(5_000);

        manager.addUsage(100, 50);
        manager.addUsage(200, 100);
        manager.addUsage(50, 50);

        assertEquals(550, manager.getTotalUsedTokens());
        assertEquals(4_450, manager.getRemainingBudget());
        assertTrue(manager.canFit(4_450));
    }

    @Test
    void estimatedTaskLargerThanRemainingDoesNotFit() {
        TokenBudgetManager manager = new TokenBudgetManager(1_000);
        manager.addUsage(700, 0);

        assertEquals(300, manager.getRemainingBudget());
        assertFalse(manager.canFit(301));
        assertTrue(manager.canFit(300));
    }

    @Test
    void resetClearsUsageButKeepsMaxBudget() {
        TokenBudgetManager manager = new TokenBudgetManager(2_000);
        manager.addUsage(900, 100);

        manager.reset();

        assertEquals(2_000, manager.getMaxBudget());
        assertEquals(0, manager.getTotalUsedTokens());
        assertEquals(2_000, manager.getRemainingBudget());
        assertTrue(manager.canFit(2_000));
    }

    @Test
    void rejectsNegativeMaxBudget() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBudgetManager(-1));
    }

    @Test
    void rejectsNegativeUsage() {
        TokenBudgetManager manager = new TokenBudgetManager(100);
        assertThrows(IllegalArgumentException.class, () -> manager.addUsage(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> manager.addUsage(0, -1));
    }
}
