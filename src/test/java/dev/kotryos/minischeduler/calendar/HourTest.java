package dev.kotryos.minischeduler.calendar;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HourTest {

    @Test
    void constructor_instantNotOnWholeHour_isRejected() {
        // given
        var notOnTheHour = Instant.parse("2026-09-01T09:30:00Z");

        // when / then
        assertThatThrownBy(() -> new Hour(notOnTheHour))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containing_instantInsideAnHour_returnsThatHour() {
        // given
        var middleOfTheHour = Instant.parse("2026-09-01T09:47:13Z");

        // when
        var hour = Hour.containing(middleOfTheHour);

        // then
        assertThat(hour.start()).isEqualTo(Instant.parse("2026-09-01T09:00:00Z"));
    }

    @Test
    void end_anyHour_isOneHourAfterItsStart() {
        // given
        var hour = new Hour(Instant.parse("2026-09-01T09:00:00Z"));

        // when
        Instant end = hour.end();

        // then
        assertThat(end).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
    }
}
