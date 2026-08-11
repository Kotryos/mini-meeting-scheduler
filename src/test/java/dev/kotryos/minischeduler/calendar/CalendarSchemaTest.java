package dev.kotryos.minischeduler.calendar;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.api.DBRider;
import dev.kotryos.minischeduler.TestcontainersConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DBRider
class CalendarSchemaTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DataSet("datasets/calendar-schema/alice-free-at-nine.yml")
    void insertSlot_duplicateHourOfSameUser_isRejected() {
        // when
        ThrowingCallable insert = () -> insertSlot(ALICE, "09:00", "10:00");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_unique_hour");
    }

    @Test
    @DataSet("datasets/calendar-schema/alice-free-at-nine.yml")
    void insertSlot_sameHourOfAnotherUser_isAccepted() {
        // when
        ThrowingCallable insert = () -> insertSlot(BOB, "09:00", "10:00");

        // then
        assertThatCode(insert).doesNotThrowAnyException();
    }

    @Test
    @DataSet("datasets/calendar-schema/users.yml")
    void insertSlot_startNotAlignedToWholeHour_isRejected() {
        // when
        ThrowingCallable insert = () -> insertSlot(ALICE, "09:30", "10:30");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_hour_aligned");
    }

    @Test
    @DataSet("datasets/calendar-schema/users.yml")
    void insertSlot_lastingLongerThanOneHour_isRejected() {
        // when
        ThrowingCallable insert = () -> insertSlot(ALICE, "09:00", "11:00");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_one_hour_long");
    }

    private void insertSlot(long userId, String startTime, String endTime) {
        jdbc.update("INSERT INTO time_slot (user_id, start_at, end_at, status) VALUES (?, ?, ?, 'FREE')",
                userId, at(startTime), at(endTime));
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-11-01T" + time + ":00Z");
    }
}
