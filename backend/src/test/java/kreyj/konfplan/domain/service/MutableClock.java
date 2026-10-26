package kreyj.konfplan.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Von Hand vorstellbare Uhr fuer Lizenztests (Ablauf, Grace-Period, Zeit-Rollback). */
final class MutableClock extends Clock {
    private Instant jetzt;

    MutableClock(Instant start) {
        this.jetzt = start;
    }

    void vor(Duration d) {
        jetzt = jetzt.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return jetzt;
    }
}
