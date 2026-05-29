package su.kidoz.axiomj.engine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

final class JsonSupport {
    private JsonSupport() {}

    static String quote(String value) {
        var json = new StringBuilder();
        quote(json, value == null ? "" : value);
        return json.toString();
    }

    static StringBuilder quote(StringBuilder json, String value) {
        json.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    case '\b' -> json.append("\\b");
                    case '\f' -> json.append("\\f");
                    default -> {
                        if (c < 0x20) json.append("\\u%04x".formatted((int) c));
                        else json.append(c);
                    }
                }
            }
        }
        json.append('"');
        return json;
    }

    static StringBuilder field(StringBuilder json, String name, String value) {
        quote(json, name).append(": ");
        quote(json, value);
        return json;
    }

    static StringBuilder value(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map<?, ?> map) {
            object(json, map);
        } else if (value instanceof Collection<?> collection) {
            array(json, collection);
        } else {
            quote(json, String.valueOf(value));
        }
        return json;
    }

    static StringBuilder object(StringBuilder json, Map<?, ?> values) {
        json.append("{");
        int i = 0;
        for (var entry : values.entrySet()) {
            quote(json, String.valueOf(entry.getKey())).append(": ");
            value(json, entry.getValue());
            if (++i < values.size()) json.append(", ");
        }
        json.append("}");
        return json;
    }

    static StringBuilder array(StringBuilder json, Collection<?> values) {
        json.append("[");
        int i = 0;
        for (var item : values) {
            value(json, item);
            if (++i < values.size()) json.append(", ");
        }
        json.append("]");
        return json;
    }

    static String stackTrace(Throwable error) {
        var sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
