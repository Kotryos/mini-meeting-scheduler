package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.HourNotFreeException;
import dev.kotryos.minischeduler.calendar.SlotBooking;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long CAROL = 3;
    private static final long MEETING = 500;

    @Mock
    private MeetingRepository meetings;

    @Mock
    private SlotBooking slots;

    @Captor
    private ArgumentCaptor<Set<Long>> booked;

    private final MeterRegistry meters = new SimpleMeterRegistry();

    private MeetingService service;

    @BeforeEach
    void setUp() {
        service = new MeetingService(meetings, slots, meters);
    }

    @Test
    void schedule_participantsGiven_booksTheHourForThemAndTheOrganiser() {
        // given
        given(meetings.save(any())).willReturn(storedMeeting());

        // when
        MeetingView scheduled = service.schedule(ALICE, "Standup", "Daily", nine(), Set.of(BOB));

        // then
        verify(slots).book(anyLong(), any(), booked.capture());
        assertThat(booked.getValue()).containsExactly(ALICE, BOB);
        assertThat(scheduled.participantIds()).containsExactly(ALICE, BOB);
        assertThat(scheduled.startAt()).isEqualTo(at("09:00"));
        assertThat(scheduled.endAt()).isEqualTo(at("10:00"));
    }

    @Test
    void schedule_organiserAlsoListedAsParticipant_isNotCountedTwice() {
        // given
        given(meetings.save(any())).willReturn(storedMeeting());

        // when
        service.schedule(ALICE, "Standup", null, nine(), Set.of(ALICE, BOB));

        // then
        verify(slots).book(anyLong(), any(), booked.capture());
        assertThat(booked.getValue()).containsExactly(ALICE, BOB);
    }

    @Test
    void schedule_someoneIsNotFree_failsAndTheMeetingIsNotReturned() {
        // given
        given(meetings.save(any())).willReturn(storedMeeting());
        willThrow(new HourNotFreeException("taken")).given(slots).book(anyLong(), any(), any());

        // when / then
        assertThatThrownBy(() -> service.schedule(ALICE, "Standup", null, nine(), Set.of(BOB)))
                .isInstanceOf(HourNotFreeException.class);
    }

    @Test
    void listFor_userWithMeetings_returnsEachWithItsParticipants() {
        // given
        given(slots.meetingsOf(BOB)).willReturn(List.of(MEETING));
        given(slots.participantsOf(List.of(MEETING))).willReturn(Map.of(MEETING, List.of(ALICE, BOB)));
        given(meetings.findAllByIds(List.of(MEETING))).willReturn(List.of(storedMeeting()));

        // when
        List<MeetingView> found = service.listFor(BOB);

        // then
        assertThat(found).singleElement().satisfies(meeting -> {
            assertThat(meeting.id()).isEqualTo(MEETING);
            assertThat(meeting.participantIds()).containsExactly(ALICE, BOB);
        });
    }

    @Test
    void listFor_userInNoMeetings_isEmptyWithoutLoadingAnything() {
        // given
        given(slots.meetingsOf(CAROL)).willReturn(List.of());

        // when / then
        assertThat(service.listFor(CAROL)).isEmpty();
        verify(meetings, never()).findAllByIds(any());
    }

    @Test
    void find_callerIsAParticipant_returnsTheMeeting() {
        // given
        given(meetings.findById(MEETING)).willReturn(Optional.of(storedMeeting()));
        given(slots.participantsOf(List.of(MEETING))).willReturn(Map.of(MEETING, List.of(ALICE, BOB)));

        // when
        MeetingView found = service.find(BOB, MEETING);

        // then
        assertThat(found.title()).isEqualTo("Standup");
        assertThat(found.participantIds()).containsExactly(ALICE, BOB);
    }

    @Test
    void find_callerIsNotAParticipant_isReportedAsNotFound() {
        // given
        given(meetings.findById(MEETING)).willReturn(Optional.of(storedMeeting()));
        given(slots.participantsOf(List.of(MEETING))).willReturn(Map.of(MEETING, List.of(ALICE, BOB)));

        // when / then
        assertThatThrownBy(() -> service.find(CAROL, MEETING))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void find_noSuchMeeting_isReportedAsNotFound() {
        // given
        given(meetings.findById(MEETING)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.find(ALICE, MEETING))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void cancel_calledByTheOrganiser_freesTheSlotsThenRemovesTheMeeting() {
        // given
        var meeting = storedMeeting();
        given(meetings.findById(MEETING)).willReturn(Optional.of(meeting));

        // when
        service.cancel(ALICE, MEETING);

        // then
        var order = inOrder(slots, meetings);
        order.verify(slots).release(MEETING);
        order.verify(meetings).delete(meeting);
    }

    @Test
    void cancel_calledBySomeoneOtherThanTheOrganiser_changesNothing() {
        // given
        given(meetings.findById(MEETING)).willReturn(Optional.of(storedMeeting()));

        // when / then
        assertThatThrownBy(() -> service.cancel(BOB, MEETING))
                .isInstanceOf(MeetingNotFoundException.class);
        verify(slots, never()).release(anyLong());
    }

    @Test
    void cancel_noSuchMeeting_isReportedAsNotFound() {
        // given
        given(meetings.findById(MEETING)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.cancel(ALICE, MEETING))
                .isInstanceOf(MeetingNotFoundException.class);
    }

    @Test
    void schedule_booked_countsTheBookingAndRecordsTheHeadcount() {
        // given
        given(meetings.save(any())).willReturn(storedMeeting());

        // when
        service.schedule(ALICE, "Standup", null, nine(), Set.of(BOB));

        // then
        assertThat(meters.get("meetings.requested").tag("outcome", "booked").counter().count()).isOne();
        assertThat(meters.get("meetings.participants").summary().max()).isEqualTo(2);
    }

    @Test
    void schedule_refused_countsItAsAConflictAndNotAsABooking() {
        // given
        given(meetings.save(any())).willReturn(storedMeeting());
        willThrow(new HourNotFreeException("taken")).given(slots).book(anyLong(), any(), any());

        // when
        assertThatThrownBy(() -> service.schedule(ALICE, "Standup", null, nine(), Set.of(BOB)))
                .isInstanceOf(HourNotFreeException.class);

        // then
        assertThat(meters.get("meetings.requested").tag("outcome", "conflict").counter().count()).isOne();
        assertThat(meters.get("meetings.requested").tag("outcome", "booked").counter().count()).isZero();
    }

    private static Meeting storedMeeting() {
        return Meeting.stored(MEETING, ALICE, "Standup", "Daily", nine());
    }

    private static Hour nine() {
        return new Hour(at("09:00"));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
