package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.SlotStatus;

import java.time.Instant;

record AggregatedSlotView(Instant startAt, Instant endAt, SlotStatus status) {
}
