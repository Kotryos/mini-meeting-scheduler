package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import dev.kotryos.minischeduler.calendar.TimeRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class SlotService {

    private final TimeSlotRepository slots;

    SlotService(TimeSlotRepository slots) {
        this.slots = slots;
    }

    @Transactional
    List<TimeSlot> publish(long userId, TimeRange range) {
        var hours = range.toHours();
        if (hours.isEmpty()) {
            throw new IllegalArgumentException("Range does not cover a whole hour: " + range);
        }

        var taken = slots.findTakenHours(userId, hours.stream().map(Hour::start).toList());
        if (!taken.isEmpty()) {
            throw new SlotConflictException("Slots already published for " + taken);
        }

        return slots.saveAll(hours.stream().map(hour -> TimeSlot.free(userId, hour)).toList());
    }

    @Transactional(readOnly = true)
    List<TimeSlot> list(long userId, Instant from, Instant to) {
        return slots.findInWindow(userId, from, to);
    }

    @Transactional
    void changeStatus(long userId, long slotId, SlotStatus status) {
        if (slots.updateStatus(slotId, userId, status) == 0) {
            throw new SlotNotFoundException(slotId);
        }
    }

    @Transactional
    void delete(long userId, long slotId) {
        if (slots.deleteOwned(slotId, userId) == 0) {
            throw new SlotNotFoundException(slotId);
        }
    }
}
