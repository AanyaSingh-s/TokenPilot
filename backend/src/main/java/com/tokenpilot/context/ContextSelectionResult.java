package com.tokenpilot.context;

import java.util.Collections;
import java.util.List;

/**
 * Result of executing context selection over a set of candidate files against a token budget.
 */
public record ContextSelectionResult(
        List<CandidateFile> selectedFiles,
        List<CandidateFile> skippedFiles,
        long totalTokenCost,
        double totalRelevanceScore,
        long maxBudget,
        long remainingBudget
) {

    public ContextSelectionResult {
        selectedFiles = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        skippedFiles = skippedFiles == null ? List.of() : List.copyOf(skippedFiles);
    }

    /**
     * Returns an unmodifiable list of paths of all selected files.
     */
    public List<String> selectedPaths() {
        return selectedFiles.stream()
                .map(CandidateFile::filePath)
                .toList();
    }

    /**
     * Indicates whether at least one file was selected.
     */
    public boolean hasSelected() {
        return !selectedFiles.isEmpty();
    }

    /**
     * Creates an empty result when no candidates fit or candidates list is empty.
     */
    public static ContextSelectionResult empty(long maxBudget) {
        return new ContextSelectionResult(
                Collections.emptyList(),
                Collections.emptyList(),
                0L,
                0.0,
                maxBudget,
                maxBudget
        );
    }
}
