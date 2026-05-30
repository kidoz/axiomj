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
                        builder.append(errorToString(error));
                        builder.append("]]>\n");
                    }
                    builder.append("      </failure>\n");
                }

                Object log = result.metadata().get("log");
                if (log != null && !log.toString().isBlank()) {
                    builder.append("      <system-out><![CDATA[\n");
                    builder.append(log.toString());
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
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String errorToString(Throwable t) {
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString().replace("]]>", "]]]]><![CDATA[>");
    }
}
