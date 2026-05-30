package su.kidoz.axiomj.engine;

import java.util.ArrayList;
import java.util.List;
import su.kidoz.axiomj.api.TestLogger;

public final class DefaultTestLogger implements TestLogger {
    private final List<String> lines = new ArrayList<>();

    @Override
    public synchronized void info(String message) {
        lines.add("[INFO] " + message);
    }

    @Override
    public synchronized void warn(String message) {
        lines.add("[WARN] " + message);
    }

    @Override
    public synchronized void error(String message) {
        lines.add("[ERROR] " + message);
    }

    @Override
    public synchronized void error(String message, Throwable cause) {
        lines.add("[ERROR] " + message);
        lines.add(cause.toString());
        for (StackTraceElement element : cause.getStackTrace()) {
            lines.add("  at " + element);
        }
    }

    @Override
    public synchronized String getOutput() {
        return String.join("\n", lines);
    }
}
