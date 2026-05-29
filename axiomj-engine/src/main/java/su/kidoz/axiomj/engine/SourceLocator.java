package su.kidoz.axiomj.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class SourceLocator {
    private SourceLocator() {}

    private static final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    static SourceLocation locate(String sourceFile, String methodName) {
        if (sourceFile == null || sourceFile.isBlank()) return new SourceLocation("", 0, 0, 0);
        Path path = Path.of(sourceFile);
        if (!Files.isRegularFile(path)) return new SourceLocation(sourceFile, 0, 0, 0);

        try {
            var lines = cache.computeIfAbsent(sourceFile, k -> {
                try {
                    return Files.readAllLines(path);
                } catch (Exception e) {
                    return List.of();
                }
            });
            if (lines.isEmpty()) return new SourceLocation(sourceFile, 0, 0, 0);

            var pattern = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    int start = i + 1; // 1-based
                    int end = start;
                    int braces = 0;
                    boolean foundBrace = false;
                    for (int j = i; j < lines.size(); j++) {
                        String l = lines.get(j);
                        for (char c : l.toCharArray()) {
                            if (c == '{') {
                                braces++;
                                foundBrace = true;
                            } else if (c == '}') braces--;
                        }
                        if (foundBrace && braces == 0) {
                            end = j + 1;
                            break;
                        }
                    }
                    return new SourceLocation(sourceFile, start + 1 <= end ? start + 1 : start, start, end);
                }
            }
        } catch (Exception ignored) {
        }
        return new SourceLocation(sourceFile, 0, 0, 0);
    }
}
