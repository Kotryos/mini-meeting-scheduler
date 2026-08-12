package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "meeting")
class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    protected Meeting() {
    }

    private Meeting(Long organizerId, String title, String description, Hour hour) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.startAt = hour.start();
    }

    static Meeting scheduled(long organizerId, String title, String description, Hour hour) {
        return new Meeting(organizerId, title, description, hour);
    }

    static Meeting stored(Long id, long organizerId, String title, String description, Hour hour) {
        var meeting = new Meeting(organizerId, title, description, hour);
        meeting.id = id;
        return meeting;
    }

    Long id() {
        return id;
    }

    long organizerId() {
        return organizerId;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    Hour hour() {
        return new Hour(startAt);
    }
}
