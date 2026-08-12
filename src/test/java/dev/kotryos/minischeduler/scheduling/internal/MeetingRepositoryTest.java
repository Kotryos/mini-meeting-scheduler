package dev.kotryos.minischeduler.scheduling.internal;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.api.dataset.ExpectedDataSet;
import com.github.database.rider.junit5.api.DBRider;
import dev.kotryos.minischeduler.TestcontainersConfiguration;
import dev.kotryos.minischeduler.calendar.Hour;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DBRider
class MeetingRepositoryTest {

    private static final long ALICE = 1;
    private static final long MEETING = 500;

    @Autowired
    private MeetingRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DataSet("datasets/meeting-repository/alice.yml")
    @ExpectedDataSet(value = "datasets/meeting-repository/expected-standup.yml",
            ignoreCols = {"id", "created_at"})
    void save_newMeeting_storesItsDetailsAndItsHour() {
        // when
        repository.save(Meeting.scheduled(ALICE, "Standup", "Daily sync", nine()));
    }

    @Test
    @DataSet("datasets/meeting-repository/alice-with-standup.yml")
    void findById_storedMeeting_readsBackItsTitleAndHour() {
        // when
        var found = repository.findById(MEETING);

        // then
        assertThat(found).get().satisfies(meeting -> {
            assertThat(meeting.title()).isEqualTo("Standup");
            assertThat(meeting.description()).isEqualTo("Daily sync");
            assertThat(meeting.organizerId()).isEqualTo(ALICE);
            assertThat(meeting.hour().start()).isEqualTo(at("09:00"));
        });
    }

    @Test
    @DataSet("datasets/meeting-repository/alice-with-standup.yml")
    void findAllByIds_idsGiven_readsOnlyThoseMeetings() {
        // when
        var found = repository.findAllByIds(List.of(MEETING, 999L));

        // then
        assertThat(found).extracting(Meeting::title).containsExactly("Standup");
    }

    @Test
    @DataSet("datasets/meeting-repository/alice-with-standup.yml")
    @ExpectedDataSet("datasets/meeting-repository/expected-no-meetings.yml")
    void delete_storedMeeting_removesIt() {
        // when
        transactions.executeWithoutResult(status ->
                repository.delete(Meeting.stored(MEETING, ALICE, "Standup", "Daily sync", nine())));
    }

    private static Hour nine() {
        return new Hour(at("09:00"));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
