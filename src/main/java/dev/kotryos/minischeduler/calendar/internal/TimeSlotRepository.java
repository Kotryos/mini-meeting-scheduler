package dev.kotryos.minischeduler.calendar.internal;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TimeSlotRepository extends Repository<TimeSlot, Long> {

    List<TimeSlot> saveAll(Iterable<TimeSlot> slots);

    @Query("""
            select s from TimeSlot s
            where s.userId = :userId and s.startAt >= :from and s.startAt < :to
            order by s.startAt
            """)
    List<TimeSlot> findInWindow(@Param("userId") long userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
