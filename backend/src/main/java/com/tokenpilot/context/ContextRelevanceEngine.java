package com.tokenpilot.context;

import com.tokenpilot.scanner.SourceFileMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lightweight heuristic engine that scores {@link SourceFileMetadata} against a task query
 * to produce {@link CandidateFile} instances for context selection.
 *
 * <p>V1 implementation is fully rule-based and deterministic (no AST, no embeddings, no LLM):
 * weights are attributed to token overlaps in file paths, declared classes, methods, and imports.
 */
public class ContextRelevanceEngine {

    private static final double WEIGHT_CLASS = 0.40;
    private static final double WEIGHT_METHOD = 0.30;
    private static final double WEIGHT_PATH = 0.20;
    private static final double WEIGHT_IMPORT = 0.10;

    /**
     * Calculates a relevance score in the range [0.0, 1.0] for a single file given a task query.
     */
    public double score(SourceFileMetadata file, String query) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (query == null || query.isBlank()) {
            return 0.0;
        }

        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }

        double classMatch = calculateMatch(file.classes(), queryTokens);
        double methodMatch = calculateMatch(file.methods(), queryTokens);
        double pathMatch = calculateTextMatch(file.filePath(), queryTokens);
        double importMatch = calculateMatch(file.imports(), queryTokens);

        double rawScore = (classMatch * WEIGHT_CLASS)
                + (methodMatch * WEIGHT_METHOD)
                + (pathMatch * WEIGHT_PATH)
                + (importMatch * WEIGHT_IMPORT);

        // Clamp to [0.0, 1.0] and round to 4 decimal places for clean representation
        return Math.min(1.0, Math.max(0.0, Math.round(rawScore * 10000.0) / 10000.0));
    }

    /**
     * Evaluates a single file and converts it into a {@link CandidateFile}.
     */
    public CandidateFile evaluate(SourceFileMetadata file, String query) {
        double relevance = score(file, query);
        return CandidateFile.fromMetadata(file, relevance);
    }

    /**
     * Evaluates a list of files against a query and returns candidates sorted descending by relevance.
     */
    public List<CandidateFile> evaluateAll(List<SourceFileMetadata> files, String query) {
        if (files == null) {
            throw new IllegalArgumentException("files must not be null");
        }
        List<CandidateFile> candidates = new ArrayList<>();
        for (SourceFileMetadata file : files) {
            candidates.add(evaluate(file, query));
        }
        candidates.sort(SelectionStrategy.BY_RELEVANCE.comparator());
        return Collections.unmodifiableList(candidates);
    }

    private static double calculateMatch(List<String> symbols, Set<String> queryTokens) {
        if (symbols == null || symbols.isEmpty()) {
            return 0.0;
        }
        long matched = symbols.stream()
                .flatMap(s -> tokenize(s).stream())
                .filter(queryTokens::contains)
                .distinct()
                .count();

        return Math.min(1.0, (double) matched / (double) queryTokens.size());
    }

    private static double calculateTextMatch(String text, Set<String> queryTokens) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        Set<String> textTokens = tokenize(text);
        long matched = textTokens.stream()
                .filter(queryTokens::contains)
                .count();

        return Math.min(1.0, (double) matched / (double) queryTokens.size());
    }

    private static Set<String> tokenize(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptySet();
        }
        // Split on camelCase, underscores, dots, slashes, and standard whitespace
        return Arrays.stream(input.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9]+"))
                .filter(s -> !s.isBlank() && s.length() > 1)
                .collect(Collectors.toUnmodifiableSet());
    }
}
