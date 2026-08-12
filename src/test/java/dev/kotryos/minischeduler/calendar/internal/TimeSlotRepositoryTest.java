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
