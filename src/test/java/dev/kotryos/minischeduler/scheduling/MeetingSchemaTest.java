package dev.kotryos.minischeduler.scheduling;

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
class MeetingSchemaTest {

    private static final long ALICE = 1;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DataSet("datasets/meeting-schema/alice.yml")
    void insertMeeting_startNotAlignedToWholeHour_isRejected() {
        // when
        ThrowingCallable insert = () -> insertMeeting("09:30");

        // then
        assertThatThrownBy(insert)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("meeting_hour_aligned");
    }

    @Test
    @DataSet("datasets/meeting-schema/alice.yml")
    void insertMeeting_startOnAWholeHour_isAccepted() {
        // when
        ThrowingCallable insert = () -> insertMeeting("09:00");

        // then
        assertThatCode(insert).doesNotThrowAnyException();
    }

    private void insertMeeting(String time) {
        jdbc.update("INSERT INTO meeting (organizer_id, title, start_at) VALUES (?, ?, ?)",
                ALICE, "Standup", OffsetDateTime.parse("2026-11-01T" + time + ":00Z"));
    }
}
