package su.kidoz.axiomj.api;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A deterministic test clock that can be injected into tests. This extends {@code java.time.Clock} to allow seamless
 * injection where application code expects a standard clock, while providing {@code advance()} methods for test
 * control.
 */
public class TestClock extends java.time.Clock {

    private Instant current;
    private final ZoneId zone;

    public TestClock() {
        this(Instant.parse("2026-05-30T10:00:00Z"), ZoneId.of("UTC"));
    }

    public TestClock(Instant initial, ZoneId zone) {
        this.current = initial;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public java.time.Clock withZone(ZoneId zone) {
        return new TestClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }

    public void set(Instant instant) {
        this.current = instant;
    }
}
