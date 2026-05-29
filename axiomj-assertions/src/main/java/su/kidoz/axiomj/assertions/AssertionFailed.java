package su.kidoz.axiomj.assertions;

public final class AssertionFailed extends AssertionError {
    public AssertionFailed(String message) {
        super(message);
    }

    public AssertionFailed(String message, Throwable cause) {
        super(message, cause);
    }
}
