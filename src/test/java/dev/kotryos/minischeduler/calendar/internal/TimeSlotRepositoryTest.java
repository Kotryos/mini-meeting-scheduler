package dev.kotryos.minischeduler.calendar.internal;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.core.api.dataset.ExpectedDataSet;
import com.github.database.rider.junit5.api.DBRider;
import dev.kotryos.minischeduler.TestcontainersConfiguration;
import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DBRider
class TimeSlotRepositoryTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long ALICE_SLOT = 1000;
    private static final long BOB_SLOT = 1001;
    private static final long MEETING = 500;

    @Autowired
    private TimeSlotRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DataSet("datasets/time-slot-repository/alice-four-consecutive-hours.yml")
    void findInWindow_slotsInsideAndOutsideTheWindow_returnsOnlyThoseInside() {
        // when
        List<TimeSlot> found = repository.findInWindow(ALICE, at("09:00"), at("11:00"));

        // then
        assertThat(found).extracting(slot -> slot.hour().start())
                .containsExactly(at("09:00"), at("10:00"));
        assertThat(found).allMatch(slot -> slot.status() == SlotStatus.FREE);
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice.yml")
    @ExpectedDataSet(value = "datasets/time-slot-repository/expected-alice-free-at-nine.yml", ignoreCols = "id")
    void saveAll_freeSlot_storesAnHourLongFreeRow() {
        // when
        repository.saveAll(List.of(TimeSlot.free(ALICE, new Hour(at("09:00")))));
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-free-and-busy.yml")
    void findInWindowWithStatus_windowHoldingBothStatuses_returnsOnlyTheAskedFor() {
        // when
        List<TimeSlot> found = repository.findInWindowWithStatus(
                ALICE, at("09:00"), at("12:00"), SlotStatus.BUSY);

        // then
        assertThat(found).extracting(slot -> slot.hour().start()).containsExactly(at("10:00"));
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    void findTakenHours_someOfTheHoursAlreadyPublished_returnsOnlyThose() {
        // when
        List<Instant> taken = repository.findTakenHours(ALICE, List.of(at("09:00"), at("10:00")));

        // then
        assertThat(taken).containsExactly(at("09:00"));
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-with-alice-and-bob-free.yml")
    @ExpectedDataSet("datasets/time-slot-repository/meeting-booked-for-both.yml")
    void book_everyoneFree_marksThemAllBusyForTheMeeting() {
        // when
        int booked = inTransaction(() -> repository.book(MEETING, at("09:00"), List.of(ALICE, BOB)));

        // then
        assertThat(booked).isEqualTo(2);
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-with-bob-already-busy.yml")
    @ExpectedDataSet("datasets/time-slot-repository/meeting-booked-for-alice-only.yml")
    void book_someoneAlreadyBusy_leavesThemAloneAndReportsTheShortfall() {
        // when
        int booked = inTransaction(() -> repository.book(MEETING, at("09:00"), List.of(ALICE, BOB)));

        // then
        assertThat(booked).isOne();
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-booked-for-both-in-full.yml")
    @ExpectedDataSet("datasets/time-slot-repository/expected-alice-and-bob-free-at-nine.yml")
    void release_meetingId_freesEverySlotItHolds() {
        // when
        int freed = inTransaction(() -> repository.release(MEETING));

        // then
        assertThat(freed).isEqualTo(2);
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-booked-for-both-in-full.yml")
    void deleteOwned_slotBookedIntoAMeeting_changesNothing() {
        // when
        int affected = inTransaction(() -> repository.deleteOwned(ALICE_SLOT, ALICE));

        // then
        assertThat(affected).isZero();
        assertThat(repository.isBooked(ALICE_SLOT, ALICE)).isTrue();
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    void isBooked_freeSlot_isFalse() {
        // when / then
        assertThat(repository.isBooked(ALICE_SLOT, ALICE)).isFalse();
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-booked-for-both-in-full.yml")
    void findByMeetings_slotsPointingAtTheMeeting_returnsThemOwnerByOwner() {
        // when
        List<TimeSlot> held = repository.findByMeetings(List.of(MEETING));

        // then
        assertThat(held).extracting(TimeSlot::userId).containsExactly(ALICE, BOB);
    }

    @Test
    @DataSet("datasets/time-slot-repository/meeting-booked-for-both-in-full.yml")
    void findMeetingIds_userHoldingABookedSlot_returnsItsMeeting() {
        // when
        List<Long> ids = repository.findMeetingIds(ALICE);

        // then
        assertThat(ids).containsExactly(MEETING);
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    void findMeetingIds_userWithOnlyFreeSlots_returnsNothing() {
        // when / then
        assertThat(repository.findMeetingIds(ALICE)).isEmpty();
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    @ExpectedDataSet("datasets/time-slot-repository/expected-alice-busy-bob-free.yml")
    void updateStatus_slotBelongingToTheCaller_marksItBusy() {
        // when
        int affected = inTransaction(() -> repository.updateStatus(ALICE_SLOT, ALICE, SlotStatus.BUSY));

        // then
        assertThat(affected).isOne();
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    @ExpectedDataSet("datasets/time-slot-repository/expected-alice-and-bob-free-at-nine.yml")
    void updateStatus_slotBelongingToAnotherUser_changesNothing() {
        // when
        int affected = inTransaction(() -> repository.updateStatus(BOB_SLOT, ALICE, SlotStatus.BUSY));

        // then
        assertThat(affected).isZero();
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    @ExpectedDataSet("datasets/time-slot-repository/expected-only-bob-free-at-nine.yml")
    void deleteOwned_slotBelongingToTheCaller_removesIt() {
        // when
        int affected = inTransaction(() -> repository.deleteOwned(ALICE_SLOT, ALICE));

        // then
        assertThat(affected).isOne();
    }

    @Test
    @DataSet("datasets/time-slot-repository/alice-and-bob-free-at-nine.yml")
    @ExpectedDataSet("datasets/time-slot-repository/expected-alice-and-bob-free-at-nine.yml")
    void deleteOwned_slotBelongingToAnotherUser_changesNothing() {
        // when
        int affected = inTransaction(() -> repository.deleteOwned(BOB_SLOT, ALICE));

        // then
        assertThat(affected).isZero();
    }

    private int inTransaction(Supplier<Integer> statement) {
        return transactions.execute(status -> statement.get());
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
