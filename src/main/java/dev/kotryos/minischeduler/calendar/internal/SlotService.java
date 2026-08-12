package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.HourNotFreeException;
import dev.kotryos.minischeduler.calendar.SlotBooking;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import dev.kotryos.minischeduler.calendar.TimeRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
class SlotService implements SlotBooking {

    private final TimeSlotRepository slots;

    SlotService(TimeSlotRepository slots) {
        this.slots = slots;
    }

    @Transactional
    List<SlotView> publish(long userId, TimeRange range) {
        var hours = range.toHours();
        if (hours.isEmpty()) {
            throw new IllegalArgumentException("Range does not cover a whole hour: " + range);
        }

        var taken = slots.findTakenHours(userId, hours.stream().map(Hour::start).toList());
        if (!taken.isEmpty()) {
            throw new SlotConflictException("Slots already published for " + taken);
        }

        return slots.saveAll(hours.stream().map(hour -> TimeSlot.free(userId, hour)).toList())
                .stream()
                .map(SlotService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    List<SlotView> list(long userId, TimeRange window, SlotStatus status) {
        var found = status == null
                ? slots.findInWindow(userId, window.from(), window.to())
                : slots.findInWindowWithStatus(userId, window.from(), window.to(), status);
        return found.stream().map(SlotService::view).toList();
    }

    @Transactional(readOnly = true)
    List<AggregatedSlotView> summary(long userId, TimeRange window) {
        var blocks = new ArrayList<AggregatedSlotView>();
        for (var slot : slots.findInWindow(userId, window.from(), window.to())) {
            var hour = slot.hour();
            var open = blocks.isEmpty() ? null : blocks.getLast();
            if (open != null && open.status() == slot.status() && open.endAt().equals(hour.start())) {
                blocks.set(blocks.size() - 1, new AggregatedSlotView(open.startAt(), hour.end(), open.status()));
            } else {
                blocks.add(new AggregatedSlotView(hour.start(), hour.end(), slot.status()));
            }
        }
        return List.copyOf(blocks);
    }

    @Override
    @Transactional
    public void book(long meetingId, Hour hour, Set<Long> userIds) {
        var booked = slots.book(meetingId, hour.start(), userIds);
        if (booked != userIds.size()) {
            throw new HourNotFreeException("Not everyone has a free slot at " + hour.start());
        }
    }

    @Override
    @Transactional
    public void release(long meetingId) {
        slots.release(meetingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> meetingsOf(long userId) {
        return slots.findMeetingIds(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> participantsOf(Collection<Long> meetingIds) {
        var byMeeting = new LinkedHashMap<Long, List<Long>>();
        for (var slot : slots.findByMeetings(meetingIds)) {
            byMeeting.computeIfAbsent(slot.meetingId(), id -> new ArrayList<>()).add(slot.userId());
        }
        return byMeeting;
    }

    @Transactional
    void changeStatus(long userId, long slotId, SlotStatus status) {
        if (slots.updateStatus(slotId, userId, status) == 0) {
            throw missing(userId, slotId);
        }
    }

    @Transactional
    void delete(long userId, long slotId) {
        if (slots.deleteOwned(slotId, userId) == 0) {
            throw missing(userId, slotId);
        }
    }

    private RuntimeException missing(long userId, long slotId) {
        return slots.isBooked(slotId, userId)
                ? new SlotConflictException("Slot " + slotId + " belongs to a meeting; cancel it first")
                : new SlotNotFoundException(slotId);
    }

    private static SlotView view(TimeSlot slot) {
        return new SlotView(slot.id(), slot.hour().start(), slot.hour().end(), slot.status());
    }
}
