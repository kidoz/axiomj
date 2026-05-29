package su.kidoz.axiomj.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

final class JsonReport {
    private JsonReport() {}

    static String render(List<TestResult> results, RunSummary summary) {
        var json = new StringBuilder();
        json.append("{\n");
        json.append("  \"framework\": \"AxiomJ\",\n");
        json.append("  \"schemaVersion\": \"0.3\",\n");
        json.append("  \"summary\": {");
        json.append("\"total\": ").append(summary.total()).append(", ");
        json.append("\"passed\": ").append(summary.passed()).append(", ");
        json.append("\"failed\": ").append(summary.failed()).append(", ");
        json.append("\"skipped\": ").append(summary.skipped()).append(", ");
        json.append("\"durationMillis\": ").append(summary.durationMillis());
        json.append("},\n");
        json.append("  \"tests\": [\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            json.append("    {");
            field(json, "id", r.id()).append(", ");
            field(json, "className", r.className()).append(", ");
            field(json, "methodName", r.methodName()).append(", ");
            field(json, "displayName", r.displayName()).append(", ");
            field(json, "status", r.status().name()).append(", ");
            json.append("\"startMillis\": ").append(r.startMillis()).append(", ");
            json.append("\"stopMillis\": ").append(r.stopMillis()).append(", ");
            json.append("\"durationMillis\": ").append(r.durationMillis()).append(", ");
            json.append("\"source\": {");
            field(json, "file", r.source().file()).append(", ");
            json.append("\"line\": ").append(r.source().line()).append(", ");
            json.append("\"methodStartLine\": ")
                    .append(r.source().methodStartLine())
                    .append(", ");
            json.append("\"methodEndLine\": ").append(r.source().methodEndLine());
            json.append("}, ");
            json.append("\"feature\": {");
            field(json, "area", r.productArea()).append(", ");
            field(json, "id", r.featureId()).append(", ");
            field(json, "name", r.featureName()).append(", ");
            field(json, "owner", r.owner()).append(", ");
            field(json, "scenario", r.scenario());
            json.append("}, ");
            json.append("\"requirements\": ");
            stringArray(json, r.requirements()).append(", ");
            json.append("\"dependsOn\": ");
            stringArray(json, r.dependsOn()).append(", ");
            json.append("\"tags\": ");
            stringArray(json, r.tags()).append(", ");
            json.append("\"metadata\": ");
            value(json, r.metadata()).append(", ");
            json.append("\"error\": ");
            if (r.error() == null) {
                json.append("null");
            } else {
                json.append("{");
                field(json, "type", r.error().getClass().getName()).append(", ");
                field(json, "message", String.valueOf(r.error().getMessage())).append(", ");
                field(json, "stackTrace", stackTrace(r.error()));
                json.append("}");
            }
            json.append("}");
            if (i < results.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static StringBuilder value(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map<?, ?> map) {
            json.append("{");
            int i = 0;
            for (var entry : map.entrySet()) {
                field(json, String.valueOf(entry.getKey()), entry.getValue());
                if (++i < map.size()) json.append(", ");
            }
            json.append("}");
        } else if (value instanceof Iterable<?> iterable) {
            json.append("[");
            int i = 0;
            for (var element : iterable) {
                if (i++ > 0) json.append(", ");
                value(json, element);
            }
            json.append("]");
        } else {
            quote(json, String.valueOf(value));
        }
        return json;
    }

    private static StringBuilder stringArray(StringBuilder json, List<String> values) {
        json.append("[");
        for (int i = 0; i < values.size(); i++) {
            quote(json, values.get(i));
            if (i < values.size() - 1) json.append(", ");
        }
        json.append("]");
        return json;
    }

    private static StringBuilder field(StringBuilder json, String name, Object value) {
        quote(json, name).append(": ");
        value(json, value);
        return json;
    }

    private static StringBuilder quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) json.append("\\u%04x".formatted((int) c));
                    else json.append(c);
                }
            }
        }
        json.append('"');
        return json;
    }

    private static String stackTrace(Throwable error) {
        var sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
