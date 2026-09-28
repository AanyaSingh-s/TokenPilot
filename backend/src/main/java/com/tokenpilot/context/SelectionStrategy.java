package com.tokenpilot.context;

import java.util.Comparator;

/**
 * Strategy used by the {@link ContextSelector} to order candidates before greedy packing.
 */
public enum SelectionStrategy {

    /**
     * Orders candidates primarily by relevance score descending.
     * Ties are broken by lower token cost (efficiency), then by file path for deterministic output.
     */
    BY_RELEVANCE {
        @Override
        public Comparator<CandidateFile> comparator() {
            return Comparator
                    .comparingDouble(CandidateFile::relevanceScore).reversed()
                    .thenComparingLong(CandidateFile::tokenCost)
                    .thenComparing(CandidateFile::filePath);
        }
    },

    /**
     * Orders candidates primarily by relevance-per-token density (relevance / tokenCost) descending.
     * Ties are broken by higher relevance score, then by file path for deterministic output.
     */
    BY_DENSITY {
        @Override
        public Comparator<CandidateFile> comparator() {
            return (a, b) -> {
                double densityA = a.tokenCost() == 0 ? Double.POSITIVE_INFINITY : a.relevanceScore() / a.tokenCost();
                double densityB = b.tokenCost() == 0 ? Double.POSITIVE_INFINITY : b.relevanceScore() / b.tokenCost();
                int cmp = Double.compare(densityB, densityA);
                if (cmp != 0) {
                    return cmp;
                }
                cmp = Double.compare(b.relevanceScore(), a.relevanceScore());
                if (cmp != 0) {
                    return cmp;
                }
                return a.filePath().compareTo(b.filePath());
            };
        }
    };

    /**
     * Returns the candidate comparator for this strategy.
     */
    public abstract Comparator<CandidateFile> comparator();
}
