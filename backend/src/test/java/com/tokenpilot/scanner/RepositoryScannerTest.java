package com.tokenpilot.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansJavaFileWithStructureHeuristics() throws IOException {
        Path javaFile = tempDir.resolve("src/Main.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, """
                package com.example;

                import java.util.List;
                import static java.util.Collections.emptyList;

                public class Main {
                    public void greet(String name) {
                        System.out.println(name);
                    }

                    private int countLines() {
                        return 0;
                    }
                }
                """);

        RepositoryScanner scanner = new RepositoryScanner();
        List<SourceFileMetadata> files = scanner.scan(tempDir);

        assertEquals(1, files.size());
        SourceFileMetadata meta = files.get(0);
        assertEquals("src/Main.java", meta.filePath());
        assertEquals(".java", meta.extension());
        assertTrue(meta.fileSizeBytes() > 0);
        assertEquals(14, meta.lineCount());
        assertTrue(meta.estimatedTokenCount() > 0);
        assertTrue(meta.imports().contains("java.util.List"));
        assertTrue(meta.imports().contains("java.util.Collections.emptyList"));
        assertEquals(List.of("Main"), meta.classes());
        assertTrue(meta.methods().contains("greet"));
        assertTrue(meta.methods().contains("countLines"));
    }

    @Test
    void filtersByExtensionAndSkipsExcludedDirectories() throws IOException {
        Files.writeString(tempDir.resolve("App.java"), "public class App {}");
        Files.writeString(tempDir.resolve("readme.txt"), "not scanned");
        Path nodeModules = tempDir.resolve("node_modules/pkg/index.js");
        Files.createDirectories(nodeModules.getParent());
        Files.writeString(nodeModules, "export function hidden() {}");

        RepositoryScanner scanner = new RepositoryScanner();
        List<SourceFileMetadata> files = scanner.scan(tempDir);

        assertEquals(1, files.size());
        assertEquals("App.java", files.get(0).filePath());
    }

    @Test
    void scansPythonFile() throws IOException {
        Files.writeString(tempDir.resolve("service.py"), """
                import os
                from pathlib import Path

                class Service:
                    def run(self):
                        pass
                """);

        RepositoryScanner scanner = new RepositoryScanner(Set.of(".py"), Set.of());
        List<SourceFileMetadata> files = scanner.scan(tempDir);

        assertEquals(1, files.size());
        SourceFileMetadata meta = files.get(0);
        assertEquals(".py", meta.extension());
        assertEquals(6, meta.lineCount());
        assertFalse(meta.imports().isEmpty());
        assertEquals(List.of("Service"), meta.classes());
        assertEquals(List.of("run"), meta.methods());
    }

    @Test
    void returnsSortedPathsForMultipleFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("b"));
        Files.createDirectories(tempDir.resolve("a"));
        Files.writeString(tempDir.resolve("b/B.java"), "class B {}");
        Files.writeString(tempDir.resolve("a/A.java"), "class A {}");

        RepositoryScanner scanner = new RepositoryScanner();
        List<SourceFileMetadata> files = scanner.scan(tempDir);

        assertEquals(2, files.size());
        assertEquals("a/A.java", files.get(0).filePath());
        assertEquals("b/B.java", files.get(1).filePath());
    }

    @Test
    void emptySourceFileHasZeroLinesAndTokens() throws IOException {
        Files.writeString(tempDir.resolve("Empty.java"), "");

        RepositoryScanner scanner = new RepositoryScanner();
        SourceFileMetadata meta = scanner.scan(tempDir).get(0);

        assertEquals(0, meta.lineCount());
        assertEquals(0, meta.estimatedTokenCount());
        assertTrue(meta.imports().isEmpty());
        assertTrue(meta.classes().isEmpty());
        assertTrue(meta.methods().isEmpty());
    }

    @Test
    void rejectsNonDirectoryRoot() {
        RepositoryScanner scanner = new RepositoryScanner();
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(tempDir.resolve("missing")));
    }
}
