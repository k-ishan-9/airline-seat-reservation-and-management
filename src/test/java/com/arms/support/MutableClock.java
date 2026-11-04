package com.arms.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A clock tests can move forward, to check hold expiry, lockouts and refund windows without waiting. */
public final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(LocalDateTime start, ZoneId zone) {
        this.instant = start.atZone(zone).toInstant();
        this.zone = zone;
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(LocalDateTime.ofInstant(instant, this.zone), zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
