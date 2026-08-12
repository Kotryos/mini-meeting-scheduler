package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import dev.kotryos.minischeduler.calendar.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    private static final long ALICE = 1;
    private static final long ANY_ID = 1000;

    @Mock
    private TimeSlotRepository slots;

    @InjectMocks
    private SlotService service;

    @Captor
    private ArgumentCaptor<Iterable<TimeSlot>> savedSlots;

    @Test
    void publish_rangeSpanningWholeHours_savesOneFreeSlotPerHour() {
        // given
        given(slots.findTakenHours(anyLong(), any())).willReturn(List.of());

        // when
        service.publish(ALICE, new TimeRange(at("09:00"), at("12:00")));

        // then
        verify(slots).saveAll(savedSlots.capture());
        assertThat(savedSlots.getValue())
                .extracting(slot -> slot.hour().start())
                .containsExactly(at("09:00"), at("10:00"), at("11:00"));
        assertThat(savedSlots.getValue()).allMatch(slot -> slot.status() == SlotStatus.FREE);
    }

    @Test
    void publish_rangeCoveringNoWholeHour_isRejected() {
        // when / then
        assertThatThrownBy(() -> service.publish(ALICE, new TimeRange(at("09:15"), at("09:45"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(slots, never()).saveAll(any());
    }

    @Test
    void publish_hourAlreadyTaken_isRejectedAndNothingIsSaved() {
        // given
        given(slots.findTakenHours(anyLong(), any())).willReturn(List.of(at("09:00")));

        // when / then
        assertThatThrownBy(() -> service.publish(ALICE, new TimeRange(at("09:00"), at("11:00"))))
                .isInstanceOf(SlotConflictException.class);
        verify(slots, never()).saveAll(any());
    }

    @Test
    void list_noStatusAsked_readsTheWholeWindow() {
        // when
        service.list(ALICE, new TimeRange(at("09:00"), at("17:00")), null);

        // then
        verify(slots).findInWindow(ALICE, at("09:00"), at("17:00"));
    }

    @Test
    void list_slotsFound_areReturnedAsViewsCarryingTheirIdAndBothEnds() {
        // given
        given(slots.findInWindow(ALICE, at("09:00"), at("17:00")))
                .willReturn(List.of(slot("09:00", SlotStatus.BUSY)));

        // when
        List<SlotView> views = service.list(ALICE, new TimeRange(at("09:00"), at("17:00")), null);

        // then
        assertThat(views).containsExactly(
                new SlotView(ANY_ID, at("09:00"), at("10:00"), SlotStatus.BUSY));
    }

    @Test
    void list_statusAsked_readsOnlySlotsOfThatStatus() {
        // when
        service.list(ALICE, new TimeRange(at("09:00"), at("17:00")), SlotStatus.FREE);

        // then
        verify(slots).findInWindowWithStatus(ALICE, at("09:00"), at("17:00"), SlotStatus.FREE);
    }

    @Test
    void summary_consecutiveHoursOfTheSameStatus_areMergedIntoOneBlock() {
        // given
        given(slots.findInWindow(ALICE, at("09:00"), at("12:00"))).willReturn(List.of(
                slot("09:00", SlotStatus.FREE),
                slot("10:00", SlotStatus.FREE),
                slot("11:00", SlotStatus.FREE)));

        // when
        List<AggregatedSlotView> blocks = service.summary(ALICE, new TimeRange(at("09:00"), at("12:00")));

        // then
        assertThat(blocks).containsExactly(
                new AggregatedSlotView(at("09:00"), at("12:00"), SlotStatus.FREE));
    }

    @Test
    void summary_statusChangingMidway_startsANewBlock() {
        // given
        given(slots.findInWindow(ALICE, at("09:00"), at("12:00"))).willReturn(List.of(
                slot("09:00", SlotStatus.FREE),
                slot("10:00", SlotStatus.BUSY),
                slot("11:00", SlotStatus.BUSY)));

        // when
        List<AggregatedSlotView> blocks = service.summary(ALICE, new TimeRange(at("09:00"), at("12:00")));

        // then
        assertThat(blocks).containsExactly(
                new AggregatedSlotView(at("09:00"), at("10:00"), SlotStatus.FREE),
                new AggregatedSlotView(at("10:00"), at("12:00"), SlotStatus.BUSY));
    }

    @Test
    void summary_undeclaredHourBetweenTwoSlots_breaksTheBlock() {
        // given
        given(slots.findInWindow(ALICE, at("09:00"), at("12:00"))).willReturn(List.of(
                slot("09:00", SlotStatus.FREE),
                slot("11:00", SlotStatus.FREE)));

        // when
        List<AggregatedSlotView> blocks = service.summary(ALICE, new TimeRange(at("09:00"), at("12:00")));

        // then
        assertThat(blocks).containsExactly(
                new AggregatedSlotView(at("09:00"), at("10:00"), SlotStatus.FREE),
                new AggregatedSlotView(at("11:00"), at("12:00"), SlotStatus.FREE));
    }

    @Test
    void summary_noSlotsInTheWindow_isEmpty() {
        // given
        given(slots.findInWindow(ALICE, at("09:00"), at("12:00"))).willReturn(List.of());

        // when
        List<AggregatedSlotView> blocks = service.summary(ALICE, new TimeRange(at("09:00"), at("12:00")));

        // then
        assertThat(blocks).isEmpty();
    }

    @Test
    void changeStatus_statementAffectsNoRow_isReportedAsNotFound() {
        // given
        given(slots.updateStatus(42, ALICE, SlotStatus.BUSY)).willReturn(0);

        // when / then
        assertThatThrownBy(() -> service.changeStatus(ALICE, 42, SlotStatus.BUSY))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void changeStatus_statementAffectsTheRow_completes() {
        // given
        given(slots.updateStatus(42, ALICE, SlotStatus.BUSY)).willReturn(1);

        // when / then
        assertThatCode(() -> service.changeStatus(ALICE, 42, SlotStatus.BUSY))
                .doesNotThrowAnyException();
    }

    @Test
    void delete_statementAffectsTheRow_completes() {
        // given
        given(slots.deleteOwned(42, ALICE)).willReturn(1);

        // when / then
        assertThatCode(() -> service.delete(ALICE, 42)).doesNotThrowAnyException();
    }

    @Test
    void delete_statementAffectsNoRow_isReportedAsNotFound() {
        // given
        given(slots.deleteOwned(42, ALICE)).willReturn(0);

        // when / then
        assertThatThrownBy(() -> service.delete(ALICE, 42))
                .isInstanceOf(SlotNotFoundException.class);
    }

    private static TimeSlot slot(String time, SlotStatus status) {
        return TimeSlot.stored(ANY_ID, ALICE, new Hour(at(time)), status);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
