package su.kidoz.axiomj.api;

public interface TestLogger {
    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable cause);

    String getOutput();
}
