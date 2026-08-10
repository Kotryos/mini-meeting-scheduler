package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.TestcontainersConfiguration;
import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimeSlotRepositoryTest {

    private record StoredSlot(Instant startAt, Instant endAt, String status) {
    }

    @Autowired
    private TimeSlotRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findInWindow_slotsInsideAndOutsideTheWindow_returnsOnlyThoseInside() {
        // given
        long user = createUser();
        insertSlot(user, "08:00");
        insertSlot(user, "09:00");
        insertSlot(user, "10:00");
        insertSlot(user, "11:00");

        // when
        List<TimeSlot> found = repository.findInWindow(user, at("09:00"), at("11:00"));

        // then
        assertThat(found).extracting(slot -> slot.hour().start())
                .containsExactly(at("09:00"), at("10:00"));
        assertThat(found).allMatch(slot -> slot.status() == SlotStatus.FREE);
    }

    @Test
    void saveAll_freeSlot_storesAnHourLongFreeRow() {
        // given
        long user = createUser();

        // when
        repository.saveAll(List.of(TimeSlot.free(user, new Hour(at("09:00")))));

        // then
        StoredSlot stored = storedSlotOf(user);
        assertThat(stored.startAt()).isEqualTo(at("09:00"));
        assertThat(stored.endAt()).isEqualTo(at("10:00"));
        assertThat(stored.status()).isEqualTo("FREE");
    }

    private long createUser() {
        return requireNonNull(jdbc.queryForObject(
                "INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", "Test User"));
    }

    private void insertSlot(long userId, String startTime) {
        jdbc.update("INSERT INTO time_slot (user_id, start_at, end_at, status) VALUES (?, ?, ?, 'FREE')",
                userId, offset(at(startTime)), offset(at(startTime).plus(1, ChronoUnit.HOURS)));
    }

    private StoredSlot storedSlotOf(long userId) {
        return requireNonNull(jdbc.queryForObject(
                "SELECT start_at, end_at, status FROM time_slot WHERE user_id = ?",
                (rs, rowNum) -> new StoredSlot(
                        rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("end_at", OffsetDateTime.class).toInstant(),
                        rs.getString("status")),
                userId));
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-01T" + time + ":00Z");
    }
}
