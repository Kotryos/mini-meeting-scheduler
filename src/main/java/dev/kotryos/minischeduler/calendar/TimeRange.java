package dev.kotryos.minischeduler.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record TimeRange(Instant from, Instant to) {

    public TimeRange {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("A range must end after it starts, was: " + from + " to " + to);
        }
    }

    public List<Hour> toHours() {
        List<Hour> hours = new ArrayList<>();
        Hour last = Hour.containing(to);
        for (Hour hour = Hour.containing(from); hour.start().isBefore(last.start()); hour = hour.next()) {
            hours.add(hour);
        }
        return List.copyOf(hours);
    }
}
