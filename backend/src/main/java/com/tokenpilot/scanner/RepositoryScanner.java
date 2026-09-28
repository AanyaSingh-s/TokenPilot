package com.tokenpilot.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Walks a local repository and collects lightweight metadata for source files.
 */
public class RepositoryScanner {

    private static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".jsx", ".ts", ".tsx", ".kt", ".go"
    );

    private static final Set<String> DEFAULT_EXCLUDED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist",
            "out", "vendor", "__pycache__", ".gradle"
    );

    private final Set<String> extensions;
    private final Set<String> excludedDirectoryNames;

    public RepositoryScanner() {
        this(DEFAULT_EXTENSIONS, DEFAULT_EXCLUDED_DIRS);
    }

    public RepositoryScanner(Set<String> extensions, Set<String> excludedDirectoryNames) {
        if (extensions == null || extensions.isEmpty()) {
            throw new IllegalArgumentException("extensions must not be empty");
        }
        this.extensions = normalizeExtensions(extensions);
        this.excludedDirectoryNames = excludedDirectoryNames == null
                ? DEFAULT_EXCLUDED_DIRS
                : Set.copyOf(excludedDirectoryNames);
    }

    public List<SourceFileMetadata> scan(Path repositoryRoot) throws IOException {
        if (repositoryRoot == null) {
            throw new IllegalArgumentException("repositoryRoot must not be null");
        }
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("repositoryRoot must be a directory: " + root);
        }

        List<SourceFileMetadata> results = new ArrayList<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path name = dir.getFileName();
                if (name != null && excludedDirectoryNames.contains(name.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String extension = extensionOf(file);
                if (!extensions.contains(extension)) {
                    return FileVisitResult.CONTINUE;
                }
                results.add(analyzeFile(root, file, attrs.size()));
                return FileVisitResult.CONTINUE;
            }
        });

        results.sort((a, b) -> a.filePath().compareTo(b.filePath()));
        return Collections.unmodifiableList(results);
    }

    private SourceFileMetadata analyzeFile(Path root, Path file, long fileSizeBytes) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        List<String> lines = content.lines().toList();
        long lineCount = lines.size();
        String extension = extensionOf(file);
        BasicStructureExtractor.ExtractedStructure structure =
                BasicStructureExtractor.extract(extension, lines);

        String relativePath = root.relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');

        return new SourceFileMetadata(
                relativePath,
                extension,
                fileSizeBytes,
                lineCount,
                BasicStructureExtractor.estimateTokens(content),
                structure.imports(),
                structure.classes(),
                structure.methods()
        );
    }

    private static String extensionOf(Path file) {
        Path name = file.getFileName();
        if (name == null) {
            return "";
        }
        String fileName = name.toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeExtensions(Set<String> raw) {
        return raw.stream()
                .map(ext -> ext.startsWith(".") ? ext.toLowerCase(Locale.ROOT) : "." + ext.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
