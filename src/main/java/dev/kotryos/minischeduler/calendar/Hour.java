package dev.kotryos.minischeduler.calendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record Hour(Instant start) {

    public Hour {
        if (!start.equals(start.truncatedTo(ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("An hour must start on a whole hour, was: " + start);
        }
    }

    public static Hour containing(Instant instant) {
        return new Hour(instant.truncatedTo(ChronoUnit.HOURS));
    }

    public Instant end() {
        return start.plus(1, ChronoUnit.HOURS);
    }

    public Hour next() {
        return new Hour(end());
    }
}
