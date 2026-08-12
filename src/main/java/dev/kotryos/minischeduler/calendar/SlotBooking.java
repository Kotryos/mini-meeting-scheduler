package dev.kotryos.minischeduler.calendar;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SlotBooking {

    /**
     * Reserves the hour for everyone named, or reserves it for nobody. A set, because the
     * booking succeeds only when it changes exactly as many rows as there are users.
     */
    void book(long meetingId, Hour hour, Set<Long> userIds);

    void release(long meetingId);

    List<Long> meetingsOf(long userId);

    Map<Long, List<Long>> participantsOf(Collection<Long> meetingIds);
}
