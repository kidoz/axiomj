package su.kidoz.axiomj.mock;

/** Matches a single actual argument during stubbing and verification. */
@FunctionalInterface
interface ArgMatcher {
    boolean matches(Object actual);
}
