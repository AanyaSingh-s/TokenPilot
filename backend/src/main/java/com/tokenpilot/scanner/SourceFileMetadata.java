package com.tokenpilot.scanner;

import java.util.List;

/**
 * Metadata collected for a single source file under a repository root.
 */
public record SourceFileMetadata(
        String filePath,
        String extension,
        long fileSizeBytes,
        long lineCount,
        long estimatedTokenCount,
        List<String> imports,
        List<String> classes,
        List<String> methods
) {}
