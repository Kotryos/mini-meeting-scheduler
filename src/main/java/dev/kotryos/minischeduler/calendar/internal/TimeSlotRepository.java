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

    @Query("""
            select s from TimeSlot s
            where s.userId = :userId and s.startAt >= :from and s.startAt < :to and s.status = :status
            order by s.startAt
            """)
    List<TimeSlot> findInWindowWithStatus(@Param("userId") long userId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to,
                                          @Param("status") SlotStatus status);

    @Query("select s.startAt from TimeSlot s where s.userId = :userId and s.startAt in :starts")
    List<Instant> findTakenHours(@Param("userId") long userId,
                                 @Param("starts") Collection<Instant> starts);

    @Modifying
    @Query("""
            update TimeSlot s set s.status = SlotStatus.BUSY, s.meetingId = :meetingId
            where s.startAt = :startAt and s.userId in :userIds
              and s.status = SlotStatus.FREE and s.meetingId is null
            """)
    int book(@Param("meetingId") long meetingId,
             @Param("startAt") Instant startAt,
             @Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("""
            update TimeSlot s set s.status = SlotStatus.FREE, s.meetingId = null
            where s.meetingId = :meetingId
            """)
    int release(@Param("meetingId") long meetingId);

    @Query("""
            select distinct s.meetingId from TimeSlot s
            where s.userId = :userId and s.meetingId is not null
            """)
    List<Long> findMeetingIds(@Param("userId") long userId);

    @Query("""
            select s from TimeSlot s
            where s.meetingId in :meetingIds
            order by s.meetingId, s.userId
            """)
    List<TimeSlot> findByMeetings(@Param("meetingIds") Collection<Long> meetingIds);

    @Query("""
            select count(s) > 0 from TimeSlot s
            where s.id = :id and s.userId = :userId and s.meetingId is not null
            """)
    boolean isBooked(@Param("id") long id, @Param("userId") long userId);

    @Modifying
    @Query("""
            update TimeSlot s set s.status = :status
            where s.id = :id and s.userId = :userId and s.meetingId is null
            """)
    int updateStatus(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") SlotStatus status);

    @Modifying
    @Query("delete from TimeSlot s where s.id = :id and s.userId = :userId and s.meetingId is null")
    int deleteOwned(@Param("id") long id, @Param("userId") long userId);
}
