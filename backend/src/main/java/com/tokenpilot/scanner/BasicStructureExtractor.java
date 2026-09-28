package com.tokenpilot.scanner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight line-based heuristics for imports and type/member names (no AST).
 */
final class BasicStructureExtractor {

    private static final Set<String> JAVA_NOT_METHODS = Set.of(
            "if", "for", "while", "switch", "catch", "return", "new", "throw",
            "class", "interface", "enum", "record", "synchronized"
    );

    private static final Pattern JAVA_IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+);\\s*$");
    private static final Pattern JAVA_TYPE =
            Pattern.compile("^\\s*(?:public|private|protected|static|final|abstract|sealed|non-sealed|\\s)*"
                    + "(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern JAVA_METHOD =
            Pattern.compile("^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*"
                    + "(?:(?:public|private|protected|static|final|native|synchronized|abstract|\\s)+)"
                    + "[\\w<>,\\[\\]?\\.\\s]+\\s+(\\w+)\\s*\\(");

    private static final Pattern PYTHON_IMPORT =
            Pattern.compile("^\\s*(?:from\\s+\\S+\\s+import|import\\s+\\S+)");
    private static final Pattern PYTHON_CLASS =
            Pattern.compile("^\\s*class\\s+(\\w+)\\s*(?:\\(|:)");
    private static final Pattern PYTHON_METHOD =
            Pattern.compile("^\\s*def\\s+(\\w+)\\s*\\(");

    private static final Pattern JS_IMPORT =
            Pattern.compile("^\\s*import\\s.+");
    private static final Pattern JS_CLASS =
            Pattern.compile("^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+(\\w+)");
    private static final Pattern JS_FUNCTION =
            Pattern.compile("^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_METHOD =
            Pattern.compile("^\\s*(?:async\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");

    private BasicStructureExtractor() {}

    static ExtractedStructure extract(String extension, List<String> lines) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case ".java" -> extractJava(lines);
            case ".py" -> extractPython(lines);
            case ".js", ".jsx", ".ts", ".tsx" -> extractJavaScriptLike(lines);
            default -> ExtractedStructure.empty();
        };
    }

    static long estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return Math.max(1, (content.length() + 3) / 4);
    }

    private static ExtractedStructure extractJava(List<String> lines) {
        LinkedHashSet<String> imports = new LinkedHashSet<>();
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        LinkedHashSet<String> methods = new LinkedHashSet<>();

        for (String line : lines) {
            Matcher importMatcher = JAVA_IMPORT.matcher(line);
            if (importMatcher.matches()) {
                imports.add(importMatcher.group(1).trim());
                continue;
            }
            Matcher typeMatcher = JAVA_TYPE.matcher(line);
            if (typeMatcher.find()) {
                classes.add(typeMatcher.group(1));
                continue;
            }
            Matcher methodMatcher = JAVA_METHOD.matcher(line);
            if (methodMatcher.find()) {
                String name = methodMatcher.group(1);
                if (!JAVA_NOT_METHODS.contains(name)) {
                    methods.add(name);
                }
            }
        }
        return new ExtractedStructure(toList(imports), toList(classes), toList(methods));
    }

    private static ExtractedStructure extractPython(List<String> lines) {
        LinkedHashSet<String> imports = new LinkedHashSet<>();
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        LinkedHashSet<String> methods = new LinkedHashSet<>();

        for (String line : lines) {
            if (PYTHON_IMPORT.matcher(line).find()) {
                imports.add(line.strip());
            }
            Matcher classMatcher = PYTHON_CLASS.matcher(line);
            if (classMatcher.find()) {
                classes.add(classMatcher.group(1));
            }
            Matcher methodMatcher = PYTHON_METHOD.matcher(line);
            if (methodMatcher.find()) {
                methods.add(methodMatcher.group(1));
            }
        }
        return new ExtractedStructure(toList(imports), toList(classes), toList(methods));
    }

    private static ExtractedStructure extractJavaScriptLike(List<String> lines) {
        LinkedHashSet<String> imports = new LinkedHashSet<>();
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        LinkedHashSet<String> methods = new LinkedHashSet<>();
        Set<String> jsSkipMethods = Set.of("if", "for", "while", "switch", "catch", "function", "class");

        for (String line : lines) {
            if (JS_IMPORT.matcher(line).find()) {
                imports.add(line.strip());
            }
            Matcher classMatcher = JS_CLASS.matcher(line);
            if (classMatcher.find()) {
                classes.add(classMatcher.group(1));
            }
            Matcher fnMatcher = JS_FUNCTION.matcher(line);
            if (fnMatcher.find()) {
                methods.add(fnMatcher.group(1));
                continue;
            }
            Matcher methodMatcher = JS_METHOD.matcher(line);
            if (methodMatcher.find()) {
                String name = methodMatcher.group(1);
                if (!jsSkipMethods.contains(name)) {
                    methods.add(name);
                }
            }
        }
        return new ExtractedStructure(toList(imports), toList(classes), toList(methods));
    }

    private static List<String> toList(LinkedHashSet<String> set) {
        return List.copyOf(set);
    }

    record ExtractedStructure(List<String> imports, List<String> classes, List<String> methods) {
        static ExtractedStructure empty() {
            return new ExtractedStructure(List.of(), List.of(), List.of());
        }
    }
}
