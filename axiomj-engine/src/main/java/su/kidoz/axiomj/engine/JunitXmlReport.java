package su.kidoz.axiomj.engine;

import java.util.List;

public final class JunitXmlReport {

    private JunitXmlReport() {}

    public static String render(List<TestResult> results, RunSummary summary) {
        var builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append(String.format(
                "<testsuites name=\"AxiomJ\" time=\"%.3f\" tests=\"%d\" failures=\"%d\" skipped=\"%d\">\n",
                summary.durationMillis() / 1000.0, summary.total(), summary.failed(), summary.skipped()));

        var testsByClass = results.stream().collect(java.util.stream.Collectors.groupingBy(TestResult::className));

        for (var entry : testsByClass.entrySet()) {
            String className = entry.getKey();
            List<TestResult> classResults = entry.getValue();

            int classTotal = classResults.size();
            int classFailures =
                    (int) classResults.stream().filter(TestResult::failed).count();
            int classSkipped =
                    (int) classResults.stream().filter(TestResult::skipped).count();
            double classTime =
                    classResults.stream().mapToLong(TestResult::durationMillis).sum() / 1000.0;

            builder.append(String.format(
                    "  <testsuite name=\"%s\" time=\"%.3f\" tests=\"%d\" failures=\"%d\" skipped=\"%d\">\n",
                    escape(className), classTime, classTotal, classFailures, classSkipped));

            for (TestResult result : classResults) {
                double time = result.durationMillis() / 1000.0;
                builder.append(String.format(
                        "    <testcase classname=\"%s\" name=\"%s\" time=\"%.3f\">\n",
                        escape(className), escape(result.methodName()), time));

                if (result.skipped()) {
                    builder.append(String.format("      <skipped message=\"%s\" />\n", escape(result.skipReason())));
                } else if (result.failed()) {
                    Throwable error = result.error();
                    String type = error != null ? escape(error.getClass().getName()) : "Unknown";
                    String message = error != null && error.getMessage() != null ? escape(error.getMessage()) : "";
                    builder.append(String.format("      <failure type=\"%s\" message=\"%s\">\n", type, message));
                    if (error != null) {
                        builder.append("<![CDATA[\n");
                        builder.append(cdata(stackTrace(error)));
                        builder.append("]]>\n");
                    }
                    builder.append("      </failure>\n");
                }

                Object log = result.metadata().get("log");
                if (log != null && !log.toString().isBlank()) {
                    builder.append("      <system-out><![CDATA[\n");
                    builder.append(cdata(log.toString()));
                    builder.append("\n]]></system-out>\n");
                }

                builder.append("    </testcase>\n");
            }
            builder.append("  </testsuite>\n");
        }

        builder.append("</testsuites>\n");
        return builder.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return stripInvalidXml(s)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // Makes text safe to embed in a CDATA section: drops characters XML 1.0 forbids (which are
    // illegal even inside CDATA) and splits any "]]>" so it cannot terminate the section early.
    private static String cdata(String s) {
        if (s == null) return "";
        return stripInvalidXml(s).replace("]]>", "]]]]><![CDATA[>");
    }

    // Removes characters that are not legal in an XML 1.0 document. Tab, newline and carriage
    // return are kept; surrogate code units are kept so supplementary characters survive.
    private static String stripInvalidXml(String s) {
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xFFFD)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable t) {
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
