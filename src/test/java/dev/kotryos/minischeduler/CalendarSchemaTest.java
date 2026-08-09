package dev.kotryos.minischeduler;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CalendarSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void insertSlot_duplicateHourOfSameUser_isRejected() {
        // given
        long user = createUser();
        insertSlot(user, "09:00", "10:00");

        // when
        ThrowingCallable insert = () -> insertSlot(user, "09:00", "10:00");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_unique_hour");
    }

    @Test
    void insertSlot_sameHourOfAnotherUser_isAccepted() {
        // given
        long user = createUser();
        long otherUser = createUser();
        insertSlot(user, "09:00", "10:00");

        // when
        ThrowingCallable insert = () -> insertSlot(otherUser, "09:00", "10:00");

        // then
        assertThatCode(insert).doesNotThrowAnyException();
    }

    @Test
    void insertSlot_startNotAlignedToWholeHour_isRejected() {
        // given
        long user = createUser();

        // when
        ThrowingCallable insert = () -> insertSlot(user, "09:30", "10:30");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_hour_aligned");
    }

    @Test
    void insertSlot_lastingLongerThanOneHour_isRejected() {
        // given
        long user = createUser();

        // when
        ThrowingCallable insert = () -> insertSlot(user, "09:00", "11:00");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("time_slot_one_hour_long");
    }

    private long createUser() {
        return requireNonNull(jdbc.queryForObject(
                "INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", "Test User"));
    }

    private void insertSlot(long userId, String startTime, String endTime) {
        jdbc.update("INSERT INTO time_slot (user_id, start_at, end_at, status) VALUES (?, ?, ?, 'FREE')",
                userId, at(startTime), at(endTime));
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-09-01T" + time + ":00Z");
    }
}
