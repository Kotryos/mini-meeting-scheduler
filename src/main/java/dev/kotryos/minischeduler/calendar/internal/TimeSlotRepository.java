package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.SlotStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface TimeSlotRepository extends Repository<TimeSlot, Long> {

    List<TimeSlot> saveAll(Iterable<TimeSlot> slots);

    @Query("""
            select s from TimeSlot s
            where s.userId = :userId and s.startAt >= :from and s.startAt < :to
            order by s.startAt
            """)
    List<TimeSlot> findInWindow(@Param("userId") long userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    @Query("select s.startAt from TimeSlot s where s.userId = :userId and s.startAt in :starts")
    List<Instant> findTakenHours(@Param("userId") long userId,
                                 @Param("starts") Collection<Instant> starts);

    @Modifying
    @Query("update TimeSlot s set s.status = :status where s.id = :id and s.userId = :userId")
    int updateStatus(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") SlotStatus status);

    @Modifying
    @Query("delete from TimeSlot s where s.id = :id and s.userId = :userId")
    int deleteOwned(@Param("id") long id, @Param("userId") long userId);
}
