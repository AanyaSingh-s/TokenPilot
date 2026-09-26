package com.tokenpilot.budget;

/**
 * Tracks consumption against a fixed token budget and checks whether planned work still fits.
 */
public class TokenBudgetManager {

    private final long maxBudget;
    private long totalUsedTokens;

    public TokenBudgetManager(long maxBudget) {
        if (maxBudget < 0) {
            throw new IllegalArgumentException("maxBudget must be non-negative");
        }
        this.maxBudget = maxBudget;
        this.totalUsedTokens = 0;
    }

    public long getMaxBudget() {
        return maxBudget;
    }

    public long getTotalUsedTokens() {
        return totalUsedTokens;
    }

    /** Tokens still available before hitting {@link #getMaxBudget()}; may be negative if usage exceeded the cap. */
    public long getRemainingBudget() {
        return maxBudget - totalUsedTokens;
    }

    /**
     * Records tokens consumed by a completed request.
     */
    public void addUsage(long inputTokens, long outputTokens) {
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        totalUsedTokens += inputTokens + outputTokens;
    }

    /**
     * Returns whether {@code estimatedTokens} for a future task fits in the remaining budget.
     */
    public boolean canFit(long estimatedTokens) {
        requireNonNegative(estimatedTokens, "estimatedTokens");
        return estimatedTokens <= getRemainingBudget();
    }

    /** Clears recorded usage; {@link #getMaxBudget()} is unchanged. */
    public void reset() {
        totalUsedTokens = 0;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
