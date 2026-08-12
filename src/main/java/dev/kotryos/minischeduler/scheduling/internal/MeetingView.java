package dev.kotryos.minischeduler.scheduling.internal;

import java.time.Instant;
import java.util.List;

record MeetingView(long id,
                   String title,
                   String description,
                   Instant startAt,
                   Instant endAt,
                   List<Long> participantIds) {
}
