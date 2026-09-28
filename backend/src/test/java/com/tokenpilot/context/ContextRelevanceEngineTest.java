package com.tokenpilot.context;

import com.tokenpilot.budget.TokenBudgetManager;
import com.tokenpilot.scanner.RepositoryScanner;
import com.tokenpilot.scanner.SourceFileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRelevanceEngineTest {

    private ContextRelevanceEngine relevanceEngine;
    private ContextSelector selector;

    @BeforeEach
    void setUp() {
        relevanceEngine = new ContextRelevanceEngine();
        selector = new ContextSelector();
    }

    @Test
    void scoresHigherForMatchingClassesAndMethods() {
        SourceFileMetadata authService = new SourceFileMetadata(
                "src/auth/AuthService.java",
                ".java",
                1200,
                40,
                300,
                List.of("java.security.Principal", "com.tokenpilot.auth.Token"),
                List.of("AuthService", "AuthSession"),
                List.of("authenticate", "validateToken", "logout")
        );

        SourceFileMetadata invoiceService = new SourceFileMetadata(
                "src/billing/InvoiceService.java",
                ".java",
                900,
                30,
                220,
                List.of("java.math.BigDecimal"),
                List.of("InvoiceService"),
                List.of("generateInvoice", "calculateTax")
        );

        double authScore = relevanceEngine.score(authService, "authenticate user token");
        double invoiceScore = relevanceEngine.score(invoiceService, "authenticate user token");

        assertTrue(authScore > invoiceScore, "AuthService should score higher than InvoiceService for auth query");
        assertTrue(authScore > 0.0);
    }

    @Test
    void emptyOrNullQueryReturnsZeroScore() {
        SourceFileMetadata file = new SourceFileMetadata(
                "src/Main.java",
                ".java",
                500,
                20,
                100,
                List.of(),
                List.of("Main"),
                List.of("run")
        );

        assertEquals(0.0, relevanceEngine.score(file, ""));
        assertEquals(0.0, relevanceEngine.score(file, null));
        assertEquals(0.0, relevanceEngine.score(file, "   "));
    }

    @Test
    void endToEndPipelineFromScannerToSelector(@TempDir Path tempDir) throws IOException {
        Path authFile = tempDir.resolve("src/AuthService.java");
        Path logFile = tempDir.resolve("src/LoggerUtil.java");
        Files.createDirectories(authFile.getParent());

        Files.writeString(authFile, """
                package com.example;
                public class AuthService {
                    public boolean login(String user, String password) {
                        return true;
                    }
                }
                """);

        Files.writeString(logFile, """
                package com.example;
                public class LoggerUtil {
                    public void log(String msg) {}
                }
                """);

        // 1. Scan local repo
        RepositoryScanner scanner = new RepositoryScanner();
        List<SourceFileMetadata> scannedFiles = scanner.scan(tempDir);
        assertEquals(2, scannedFiles.size());

        // 2. Score relevance against task query
        List<CandidateFile> candidates = relevanceEngine.evaluateAll(scannedFiles, "login authentication");
        assertEquals(2, candidates.size());
        assertEquals("src/AuthService.java", candidates.get(0).filePath());
        assertTrue(candidates.get(0).relevanceScore() > candidates.get(1).relevanceScore());

        // 3. Select context with TokenBudgetManager
        TokenBudgetManager budgetManager = new TokenBudgetManager(1000);
        ContextSelectionResult selection = selector.select(candidates, budgetManager);

        assertTrue(selection.hasSelected());
        assertEquals("src/AuthService.java", selection.selectedFiles().get(0).filePath());
        assertTrue(selection.totalTokenCost() <= 1000);
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(IllegalArgumentException.class, () -> relevanceEngine.score(null, "query"));
        assertThrows(IllegalArgumentException.class, () -> relevanceEngine.evaluate(null, "query"));
        assertThrows(IllegalArgumentException.class, () -> relevanceEngine.evaluateAll(null, "query"));
    }
}
