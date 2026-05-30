package su.kidoz.axiomj.engine;

import java.util.List;

public final class SarifReport {

    private SarifReport() {}

    public static String render(List<TestResult> results, RunConfig config) {
        var builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"version\": \"2.1.0\",\n");
        builder.append("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n");
        builder.append("  \"runs\": [\n");
        builder.append("    {\n");
        builder.append("      \"tool\": {\n");
        builder.append("        \"driver\": {\n");
        builder.append("          \"name\": \"AxiomJ Test Runner\",\n");
        builder.append("          \"informationUri\": \"https://github.com/axiomj\",\n");
        builder.append("          \"rules\": [\n");
        builder.append("            {\n");
        builder.append("              \"id\": \"AXIOMJ-TEST-FAILURE\",\n");
        builder.append("              \"name\": \"TestFailure\",\n");
        builder.append("              \"shortDescription\": { \"text\": \"A test failed.\" },\n");
        builder.append("              \"defaultConfiguration\": { \"level\": \"error\" }\n");
        builder.append("            }\n");
        builder.append("          ]\n");
        builder.append("        }\n");
        builder.append("      },\n");
        builder.append("      \"results\": [\n");

        boolean first = true;
        for (TestResult result : results) {
            if (!result.failed()) continue;

            if (!first) builder.append(",\n");
            first = false;

            String className = result.className();
            // Reuse the source path already resolved by the runner (configurable via -Daxiomj.sourceRoots),
            // falling back to the conventional Java test layout only if it is unavailable.
            String fileUri = result.sourceFile();
            if (fileUri == null || fileUri.isBlank()) {
                fileUri = "src/test/java/" + className.replace('.', '/') + ".java";
            }
            // In a full implementation, we'd parse the stack trace to find the exact line.
            // For now, we fall back to line 1 of the test class file.
            int line = 1;
            if (result.error() != null && result.error().getStackTrace() != null) {
                for (StackTraceElement elem : result.error().getStackTrace()) {
                    if (elem.getClassName().equals(className)) {
                        line = elem.getLineNumber() > 0 ? elem.getLineNumber() : 1;
                        break;
                    }
                }
            }

            String message = result.error() != null ? escapeJson(result.error().getMessage()) : "Unknown failure";

            builder.append("        {\n");
            builder.append("          \"ruleId\": \"AXIOMJ-TEST-FAILURE\",\n");
            builder.append("          \"message\": { \"text\": \"")
                    .append(message)
                    .append("\" },\n");
            builder.append("          \"locations\": [\n");
            builder.append("            {\n");
            builder.append("              \"physicalLocation\": {\n");
            builder.append("                \"artifactLocation\": {\n");
            builder.append("                  \"uri\": \"")
                    .append(escapeJson(fileUri))
                    .append("\",\n");
            builder.append("                  \"uriBaseId\": \"%SRCROOT%\"\n");
            builder.append("                },\n");
            builder.append("                \"region\": {\n");
            builder.append("                  \"startLine\": ").append(line).append("\n");
            builder.append("                }\n");
            builder.append("              }\n");
            builder.append("            }\n");
            builder.append("          ]\n");
            builder.append("        }");
        }

        builder.append("\n      ]\n");
        builder.append("    }\n");
        builder.append("  ]\n");
        builder.append("}\n");

        return builder.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append("\\u%04x".formatted((int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
