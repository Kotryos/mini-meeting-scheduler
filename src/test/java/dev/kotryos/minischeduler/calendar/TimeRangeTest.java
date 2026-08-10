package dev.kotryos.minischeduler.calendar;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRangeTest {

    @Test
    void constructor_rangeEndingBeforeItStarts_isRejected() {
        // given
        Instant from = Instant.parse("2026-09-01T10:00:00Z");
        Instant to = Instant.parse("2026-09-01T09:00:00Z");

        // when / then
        assertThatThrownBy(() -> new TimeRange(from, to))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toHours_rangeAlignedToWholeHours_returnsEveryHourItCovers() {
        // given
        TimeRange range = new TimeRange(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T12:00:00Z"));

        // when
        List<Hour> hours = range.toHours();

        // then
        assertThat(hours).extracting(Hour::start).containsExactly(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"));
    }

    @Test
    void toHours_rangeWithMinutes_dropsThePartialHoursAtBothEnds() {
        // given
        TimeRange range = new TimeRange(
                Instant.parse("2026-09-01T09:15:00Z"),
                Instant.parse("2026-09-01T11:45:00Z"));

        // when
        List<Hour> hours = range.toHours();

        // then
        assertThat(hours).extracting(Hour::start).containsExactly(
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-01T10:00:00Z"));
    }

    @Test
    void toHours_rangeContainedWithinASingleHour_returnsNothing() {
        // given
        TimeRange range = new TimeRange(
                Instant.parse("2026-09-01T09:15:00Z"),
                Instant.parse("2026-09-01T09:45:00Z"));

        // when
        List<Hour> hours = range.toHours();

        // then
        assertThat(hours).isEmpty();
    }
}
