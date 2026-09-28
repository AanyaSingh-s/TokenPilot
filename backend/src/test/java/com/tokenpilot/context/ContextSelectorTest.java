package com.tokenpilot.context;

import com.tokenpilot.budget.TokenBudgetManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextSelectorTest {

    private ContextSelector selector;

    @BeforeEach
    void setUp() {
        selector = new ContextSelector();
    }

    /**
     * Verifies the exact scenario described in the specification:
     * File A: 2000 tokens, relevance 0.95
     * File B: 3000 tokens, relevance 0.90
     * File C: 1500 tokens, relevance 0.70
     * File D: 4000 tokens, relevance 0.85
     * Budget: 6000 tokens.
     *
     * Expected selection: File A and File B (total tokens: 5000 <= 6000).
     */
    @Test
    void specificationExampleSelectsFileAAndFileB() {
        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);
        CandidateFile fileC = new CandidateFile("File C", 1500, 0.70);
        CandidateFile fileD = new CandidateFile("File D", 4000, 0.85);

        List<CandidateFile> candidates = List.of(fileA, fileB, fileC, fileD);
        ContextSelectionResult result = selector.select(candidates, 6000);

        assertEquals(List.of(fileA, fileB), result.selectedFiles());
        assertEquals(List.of("File A", "File B"), result.selectedPaths());
        assertEquals(List.of(fileD, fileC), result.skippedFiles());
        assertEquals(5000, result.totalTokenCost());
        assertEquals(1.85, result.totalRelevanceScore(), 0.0001);
        assertEquals(6000, result.maxBudget());
        assertEquals(1000, result.remainingBudget());
        assertTrue(result.hasSelected());
    }

    @Test
    void tightBudgetSelectsOnlyTopRelevanceCandidate() {
        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);
        CandidateFile fileC = new CandidateFile("File C", 1500, 0.70);
        CandidateFile fileD = new CandidateFile("File D", 4000, 0.85);

        ContextSelectionResult result = selector.select(List.of(fileA, fileB, fileC, fileD), 2000);

        assertEquals(List.of(fileA), result.selectedFiles());
        assertEquals(2000, result.totalTokenCost());
        assertEquals(0, result.remainingBudget());
        assertEquals(List.of(fileB, fileD, fileC), result.skippedFiles());
    }

    @Test
    void budgetTooSmallForAnyCandidateSelectsNone() {
        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);

        ContextSelectionResult result = selector.select(List.of(fileA, fileB), 1000);

        assertTrue(result.selectedFiles().isEmpty());
        assertFalse(result.hasSelected());
        assertEquals(0, result.totalTokenCost());
        assertEquals(1000, result.remainingBudget());
        assertEquals(2, result.skippedFiles().size());
    }

    @Test
    void ampleBudgetSelectsAllCandidatesInRankedOrder() {
        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);
        CandidateFile fileC = new CandidateFile("File C", 1500, 0.70);
        CandidateFile fileD = new CandidateFile("File D", 4000, 0.85);

        ContextSelectionResult result = selector.select(List.of(fileA, fileB, fileC, fileD), 15000);

        assertEquals(List.of(fileA, fileB, fileD, fileC), result.selectedFiles());
        assertTrue(result.skippedFiles().isEmpty());
        assertEquals(10500, result.totalTokenCost());
        assertEquals(4500, result.remainingBudget());
    }

    @Test
    void exactBudgetFitsSelectedCandidatesWithZeroRemaining() {
        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);

        ContextSelectionResult result = selector.select(List.of(fileA, fileB), 5000);

        assertEquals(List.of(fileA, fileB), result.selectedFiles());
        assertEquals(5000, result.totalTokenCost());
        assertEquals(0, result.remainingBudget());
    }

    @Test
    void skipsLargeCandidateAndPacksSmallerDownstreamCandidate() {
        // High relevance large file, medium relevance large file that doesn't fit remaining,
        // and lower relevance small file that DOES fit remaining.
        CandidateFile file1 = new CandidateFile("Top.java", 4500, 0.98);
        CandidateFile file2 = new CandidateFile("Medium.java", 3000, 0.90);
        CandidateFile file3 = new CandidateFile("SmallHelper.java", 1200, 0.80);

        ContextSelectionResult result = selector.select(List.of(file1, file2, file3), 6000);

        // Budget = 6000. Top.java takes 4500 (1500 left). Medium (3000) skipped. SmallHelper (1200) fits!
        assertEquals(List.of(file1, file3), result.selectedFiles());
        assertEquals(List.of(file2), result.skippedFiles());
        assertEquals(5700, result.totalTokenCost());
        assertEquals(300, result.remainingBudget());
    }

    @Test
    void zeroCostCandidatesAlwaysFitEvenInZeroBudget() {
        CandidateFile freeCandidate = new CandidateFile("Constants.java", 0, 0.60);
        CandidateFile paidCandidate = new CandidateFile("Service.java", 500, 0.90);

        ContextSelectionResult result = selector.select(List.of(freeCandidate, paidCandidate), 0);

        assertEquals(List.of(freeCandidate), result.selectedFiles());
        assertEquals(0, result.totalTokenCost());
        assertEquals(0, result.remainingBudget());
    }

    @Test
    void breaksTiesByLowerTokenCostThenAlphabeticalPath() {
        CandidateFile cheap = new CandidateFile("B.java", 100, 0.80);
        CandidateFile expensive = new CandidateFile("A.java", 500, 0.80);
        CandidateFile sameCostSameScoreAlphaA = new CandidateFile("alpha/A.java", 100, 0.50);
        CandidateFile sameCostSameScoreAlphaB = new CandidateFile("alpha/B.java", 100, 0.50);

        ContextSelectionResult result = selector.select(
                List.of(expensive, cheap, sameCostSameScoreAlphaB, sameCostSameScoreAlphaA),
                200
        );

        // cheap (100) picked first over expensive (500) due to lower cost tie-breaker.
        // remaining budget is 100, then alpha/A.java picked over alpha/B.java due to path.
        assertEquals(List.of(cheap, sameCostSameScoreAlphaA), result.selectedFiles());
    }

    @Test
    void integratesWithTokenBudgetManager() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(10000);
        budgetManager.addUsage(2500, 1500); // 4000 used, 6000 remaining

        CandidateFile fileA = new CandidateFile("File A", 2000, 0.95);
        CandidateFile fileB = new CandidateFile("File B", 3000, 0.90);
        CandidateFile fileC = new CandidateFile("File C", 1500, 0.70);

        ContextSelectionResult result = selector.select(List.of(fileA, fileB, fileC), budgetManager);

        assertEquals(List.of(fileA, fileB), result.selectedFiles());
        assertEquals(5000, result.totalTokenCost());
        assertEquals(6000, result.maxBudget());
        assertEquals(1000, result.remainingBudget());
    }

    @Test
    void budgetManagerWithExhaustedCapacitySelectsNothing() {
        TokenBudgetManager budgetManager = new TokenBudgetManager(1000);
        budgetManager.addUsage(800, 400); // Exceeded: 1200 used, -200 remaining

        CandidateFile file = new CandidateFile("File.java", 100, 0.90);
        ContextSelectionResult result = selector.select(List.of(file), budgetManager);

        assertTrue(result.selectedFiles().isEmpty());
        assertEquals(0, result.maxBudget());
    }

    @Test
    void supportsDensitySelectionStrategy() {
        // fileX has high relevance but huge token cost (density = 0.90 / 4500 = 0.0002)
        CandidateFile fileX = new CandidateFile("Heavy.java", 4500, 0.90);
        // fileY and fileZ have lower relevance but much higher density
        // fileY: 0.80 / 1000 = 0.0008
        // fileZ: 0.75 / 1000 = 0.00075
        CandidateFile fileY = new CandidateFile("Compact1.java", 1000, 0.80);
        CandidateFile fileZ = new CandidateFile("Compact2.java", 1000, 0.75);

        ContextSelectionResult byDensity = selector.select(
                List.of(fileX, fileY, fileZ),
                2500,
                SelectionStrategy.BY_DENSITY
        );

        // Density strategy prefers Compact1 and Compact2 over Heavy
        assertEquals(List.of(fileY, fileZ), byDensity.selectedFiles());
        assertEquals(2000, byDensity.totalTokenCost());
    }

    @Test
    void emptyCandidatesReturnsEmptyResult() {
        ContextSelectionResult result = selector.select(List.of(), 5000);
        assertFalse(result.hasSelected());
        assertEquals(0, result.totalTokenCost());
        assertEquals(5000, result.remainingBudget());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> selector.select(null, 1000));
        assertThrows(IllegalArgumentException.class, () -> selector.select(List.of(), -1));
        assertThrows(NullPointerException.class, () -> selector.select(List.of(), 1000, null));
        assertThrows(IllegalArgumentException.class, () -> selector.select(List.of(), (TokenBudgetManager) null));
    }

    @Test
    void rejectsInvalidCandidateFile() {
        assertThrows(IllegalArgumentException.class, () -> new CandidateFile(null, 100, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CandidateFile("", 100, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CandidateFile("a.java", -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CandidateFile("a.java", 100, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new CandidateFile("a.java", 100, Double.NaN));
    }
}
