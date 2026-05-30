package su.kidoz.axiomj.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public final class HtmlReport {

    private HtmlReport() {}

    public static String render(List<TestResult> results, RunSummary summary, RunConfig config) {
        var builder = new StringBuilder();
        builder.append("<!DOCTYPE html>\n");
        builder.append("<html lang=\"en\">\n");
        builder.append("<head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>AxiomJ Test Report</title>\n");
        builder.append("    <style>\n");
        builder.append(
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 2rem; color: #333; }\n");
        builder.append("        h1 { border-bottom: 2px solid #eee; padding-bottom: 0.5rem; }\n");
        builder.append(
                "        .summary { display: flex; gap: 2rem; margin-bottom: 2rem; padding: 1rem; background: #f8f9fa; border-radius: 8px; }\n");
        builder.append("        .stat { display: flex; flex-direction: column; align-items: center; }\n");
        builder.append("        .stat-value { font-size: 2rem; font-weight: bold; }\n");
        builder.append("        .passed { color: #28a745; }\n");
        builder.append("        .failed { color: #dc3545; }\n");
        builder.append("        .skipped { color: #ffc107; }\n");
        builder.append("        table { width: 100%; border-collapse: collapse; margin-top: 1rem; }\n");
        builder.append("        th, td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #dee2e6; }\n");
        builder.append("        th { background-color: #f8f9fa; }\n");
        builder.append(
                "        .badge { padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.875rem; font-weight: bold; color: white; }\n");
        builder.append("        .bg-passed { background-color: #28a745; }\n");
        builder.append("        .bg-failed { background-color: #dc3545; }\n");
        builder.append("        .bg-skipped { background-color: #ffc107; }\n");
        builder.append("        details { margin-top: 0.5rem; }\n");
        builder.append(
                "        pre { background: #f1f3f5; padding: 1rem; border-radius: 4px; overflow-x: auto; font-size: 0.875rem; }\n");
        builder.append("    </style>\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("    <h1>AxiomJ Test Report</h1>\n");

        builder.append("    <div class=\"summary\">\n");
        builder.append("        <div class=\"stat\"><span class=\"stat-value\">")
                .append(summary.total())
                .append("</span><span>Total</span></div>\n");
        builder.append("        <div class=\"stat passed\"><span class=\"stat-value\">")
                .append(summary.passed())
                .append("</span><span>Passed</span></div>\n");
        builder.append("        <div class=\"stat failed\"><span class=\"stat-value\">")
                .append(summary.failed())
                .append("</span><span>Failed</span></div>\n");
        builder.append("        <div class=\"stat skipped\"><span class=\"stat-value\">")
                .append(summary.skipped())
                .append("</span><span>Skipped</span></div>\n");
        builder.append("        <div class=\"stat\"><span class=\"stat-value\">")
                .append(summary.durationMillis())
                .append("ms</span><span>Duration</span></div>\n");
        builder.append("    </div>\n");

        builder.append("    <table>\n");
        builder.append("        <thead>\n");
        builder.append("            <tr>\n");
        builder.append("                <th>Status</th>\n");
        builder.append("                <th>Test</th>\n");
        builder.append("                <th>Duration</th>\n");
        builder.append("            </tr>\n");
        builder.append("        </thead>\n");
        builder.append("        <tbody>\n");

        for (TestResult result : results) {
            builder.append("            <tr>\n");

            String statusClass = "bg-" + result.status().name().toLowerCase();
            builder.append("                <td><span class=\"badge ")
                    .append(statusClass)
                    .append("\">")
                    .append(result.status())
                    .append("</span></td>\n");

            builder.append("                <td>\n");
            builder.append("                    <strong>")
                    .append(escapeHtml(result.displayName()))
                    .append("</strong><br/>\n");
            builder.append("                    <small class=\"text-muted\">")
                    .append(escapeHtml(result.className()))
                    .append(".")
                    .append(escapeHtml(result.methodName()))
                    .append("</small>\n");

            if (result.failed() && result.error() != null) {
                builder.append("                    <details>\n");
                builder.append("                        <summary>Failure Details</summary>\n");
                builder.append("                        <pre>")
                        .append(escapeHtml(errorToString(result.error())))
                        .append("</pre>\n");
                builder.append("                    </details>\n");
            } else if (result.skipped()) {
                builder.append(
                                "                    <div style=\"margin-top: 0.5rem; font-style: italic; color: #6c757d;\">Reason: ")
                        .append(escapeHtml(result.skipReason()))
                        .append("</div>\n");
            }

            Object log = result.metadata().get("log");
            if (log != null && !log.toString().isBlank()) {
                builder.append("                    <details>\n");
                builder.append("                        <summary>System Out</summary>\n");
                builder.append("                        <pre>")
                        .append(escapeHtml(log.toString()))
                        .append("</pre>\n");
                builder.append("                    </details>\n");
            }

            builder.append("                </td>\n");
            builder.append("                <td>")
                    .append(result.durationMillis())
                    .append("ms</td>\n");
            builder.append("            </tr>\n");
        }

        builder.append("        </tbody>\n");
        builder.append("    </table>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");

        return builder.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String errorToString(Throwable t) {
        var sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
