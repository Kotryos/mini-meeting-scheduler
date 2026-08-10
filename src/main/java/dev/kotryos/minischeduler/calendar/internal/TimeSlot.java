package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "time_slot")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SlotStatus status;

    protected TimeSlot() {
    }

    private TimeSlot(Long userId, Hour hour, SlotStatus status) {
        this.userId = userId;
        this.startAt = hour.start();
        this.endAt = hour.end();
        this.status = status;
    }

    public static TimeSlot free(Long userId, Hour hour) {
        return new TimeSlot(userId, hour, SlotStatus.FREE);
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public Hour hour() {
        return new Hour(startAt);
    }

    public SlotStatus status() {
        return status;
    }
}
