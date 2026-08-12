package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotBooking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
class MeetingService {

    private final MeetingRepository meetings;
    private final SlotBooking slots;

    MeetingService(MeetingRepository meetings, SlotBooking slots) {
        this.meetings = meetings;
        this.slots = slots;
    }

    @Transactional
    MeetingView schedule(long organizerId, String title, String description, Hour hour, Set<Long> invited) {
        var participants = new LinkedHashSet<Long>();
        participants.add(organizerId);
        participants.addAll(invited);

        var meeting = meetings.save(Meeting.scheduled(organizerId, title, description, hour));
        slots.book(meeting.id(), hour, participants);

        return view(meeting, List.copyOf(participants));
    }

    @Transactional(readOnly = true)
    MeetingView find(long callerId, long meetingId) {
        var meeting = meetings.findById(meetingId).orElseThrow(() -> new MeetingNotFoundException(meetingId));
        var participants = slots.participantsOf(List.of(meetingId)).getOrDefault(meetingId, List.of());
        if (!participants.contains(callerId)) {
            throw new MeetingNotFoundException(meetingId);
        }
        return view(meeting, participants);
    }

    @Transactional(readOnly = true)
    List<MeetingView> listFor(long callerId) {
        var ids = slots.meetingsOf(callerId);
        if (ids.isEmpty()) {
            return List.of();
        }

        var participants = slots.participantsOf(ids);
        return meetings.findAllByIds(ids).stream()
                .map(meeting -> view(meeting, participants.getOrDefault(meeting.id(), List.of())))
                .toList();
    }

    @Transactional
    void cancel(long callerId, long meetingId) {
        var meeting = meetings.findById(meetingId).orElseThrow(() -> new MeetingNotFoundException(meetingId));
        if (meeting.organizerId() != callerId) {
            throw new MeetingNotFoundException(meetingId);
        }
        slots.release(meetingId);
        meetings.delete(meeting);
    }

    private static MeetingView view(Meeting meeting, List<Long> participants) {
        return new MeetingView(meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.hour().start(),
                meeting.hour().end(),
                participants);
    }
}
