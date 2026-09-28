package com.tokenpilot.context;

import com.tokenpilot.scanner.SourceFileMetadata;

/**
 * Represents a candidate source file under evaluation for context inclusion,
 * pairing file identity and token cost with an assigned relevance score.
 */
public record CandidateFile(
        String filePath,
        long tokenCost,
        double relevanceScore
) {

    public CandidateFile {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be null or blank");
        }
        if (tokenCost < 0) {
            throw new IllegalArgumentException("tokenCost must be non-negative: " + tokenCost);
        }
        if (Double.isNaN(relevanceScore) || relevanceScore < 0.0) {
            throw new IllegalArgumentException("relevanceScore must be non-negative and not NaN: " + relevanceScore);
        }
    }

    /**
     * Creates a CandidateFile from repository scanner metadata and an assigned relevance score.
     */
    public static CandidateFile fromMetadata(SourceFileMetadata metadata, double relevanceScore) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        return new CandidateFile(metadata.filePath(), metadata.estimatedTokenCount(), relevanceScore);
    }
}
