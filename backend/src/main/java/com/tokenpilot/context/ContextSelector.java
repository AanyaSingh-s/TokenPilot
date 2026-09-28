package com.tokenpilot.context;

import com.tokenpilot.budget.TokenBudgetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic greedy selector for candidate source files under a context token budget.
 *
 * <p>V1 uses a greedy approach:
 * <ol>
 *   <li>Candidates are sorted according to a {@link SelectionStrategy} (default: {@link SelectionStrategy#BY_RELEVANCE}).</li>
 *   <li>In descending order, each candidate is accepted if its token cost fits within the remaining budget.</li>
 *   <li>Candidates exceeding remaining capacity are skipped, allowing downstream smaller candidates a chance to fit.</li>
 * </ol>
 * No LLM or external model is involved.
 */
public class ContextSelector {

    private final SelectionStrategy defaultStrategy;

    public ContextSelector() {
        this(SelectionStrategy.BY_RELEVANCE);
    }

    public ContextSelector(SelectionStrategy defaultStrategy) {
        this.defaultStrategy = Objects.requireNonNull(defaultStrategy, "defaultStrategy must not be null");
    }

    /**
     * Selects files using the default strategy and a fixed maximum token budget.
     */
    public ContextSelectionResult select(List<CandidateFile> candidates, long maxBudget) {
        return select(candidates, maxBudget, this.defaultStrategy);
    }

    /**
     * Selects files using the specified strategy and a fixed maximum token budget.
     */
    public ContextSelectionResult select(List<CandidateFile> candidates, long maxBudget, SelectionStrategy strategy) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (maxBudget < 0) {
            throw new IllegalArgumentException("maxBudget must be non-negative: " + maxBudget);
        }
        Objects.requireNonNull(strategy, "strategy must not be null");

        if (candidates.isEmpty()) {
            return ContextSelectionResult.empty(maxBudget);
        }

        List<CandidateFile> sorted = new ArrayList<>(candidates);
        sorted.sort(strategy.comparator());

        List<CandidateFile> selected = new ArrayList<>();
        List<CandidateFile> skipped = new ArrayList<>();

        long currentUsedTokens = 0;
        double currentTotalRelevance = 0.0;
        long remaining = maxBudget;

        for (CandidateFile candidate : sorted) {
            Objects.requireNonNull(candidate, "candidate file in list must not be null");
            if (candidate.tokenCost() <= remaining) {
                selected.add(candidate);
                remaining -= candidate.tokenCost();
                currentUsedTokens += candidate.tokenCost();
                currentTotalRelevance += candidate.relevanceScore();
            } else {
                skipped.add(candidate);
            }
        }

        return new ContextSelectionResult(
                selected,
                skipped,
                currentUsedTokens,
                currentTotalRelevance,
                maxBudget,
                remaining
        );
    }

    /**
     * Selects files against the remaining budget of an existing {@link TokenBudgetManager}.
     */
    public ContextSelectionResult select(List<CandidateFile> candidates, TokenBudgetManager budgetManager) {
        return select(candidates, budgetManager, this.defaultStrategy);
    }

    /**
     * Selects files against the remaining budget of an existing {@link TokenBudgetManager}
     * with the specified strategy.
     */
    public ContextSelectionResult select(List<CandidateFile> candidates, TokenBudgetManager budgetManager, SelectionStrategy strategy) {
        if (budgetManager == null) {
            throw new IllegalArgumentException("budgetManager must not be null");
        }
        long available = Math.max(0, budgetManager.getRemainingBudget());
        return select(candidates, available, strategy);
    }

    public SelectionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }
}
