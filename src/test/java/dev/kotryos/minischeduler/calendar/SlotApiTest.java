package dev.kotryos.minischeduler.calendar;

import dev.kotryos.minischeduler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlotApiTest {

    private static final String HEADER = "X-API-Key";
    private static final String ALICE_KEY = "alice-demo-key";
    private static final String BOB_KEY = "bob-demo-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publishSlots_rangeSpanningWholeHours_storesOneSlotPerHour() throws Exception {
        // given
        long alice = userId("alice@example.com");

        // when
        publish(ALICE_KEY, "2026-11-01T09:00:00Z", "2026-11-01T12:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3));

        // then
        assertThat(storedSlotsOn(alice, "2026-11-01"))
                .containsExactly(
                        "2026-11-01T09:00:00Z FREE",
                        "2026-11-01T10:00:00Z FREE",
                        "2026-11-01T11:00:00Z FREE");
    }

    @Test
    void publishSlots_withoutApiKey_isUnauthorized() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-11-02T09:00:00Z","to":"2026-11-02T10:00:00Z"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishSlots_rangeShorterThanAnHour_isRejected() throws Exception {
        // when / then
        publish(ALICE_KEY, "2026-11-03T09:15:00Z", "2026-11-03T09:45:00Z")
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishSlots_missingRangeBoundary_reportsAProblemDetail() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .header(HEADER, ALICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"2026-11-04T10:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publishSlots_hourAlreadyPublished_isRejected() throws Exception {
        // given
        long alice = userId("alice@example.com");
        insertFreeSlot(alice, "2026-11-05T09:00:00Z");

        // when
        publish(ALICE_KEY, "2026-11-05T09:00:00Z", "2026-11-05T11:00:00Z")
                .andExpect(status().isConflict());

        // then
        assertThat(storedSlotsOn(alice, "2026-11-05")).hasSize(1);
    }

    @Test
    void listSlots_windowWithSlotsOfSeveralUsers_returnsOnlyTheCallersOwn() throws Exception {
        // given
        insertFreeSlot(userId("alice@example.com"), "2026-11-06T09:00:00Z");
        insertFreeSlot(userId("bob@example.com"), "2026-11-06T09:00:00Z");
        insertFreeSlot(userId("bob@example.com"), "2026-11-06T10:00:00Z");

        // when / then
        mockMvc.perform(get("/api/v1/slots")
                        .header(HEADER, ALICE_KEY)
                        .param("from", "2026-11-06T00:00:00Z")
                        .param("to", "2026-11-07T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value("2026-11-06T09:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-11-06T10:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("FREE"));
    }

    @Test
    void changeStatus_ownFreeSlot_marksItBusy() throws Exception {
        // given
        long alice = userId("alice@example.com");
        long slotId = insertFreeSlot(alice, "2026-11-07T09:00:00Z");

        // when
        mockMvc.perform(patch("/api/v1/slots/{id}", slotId)
                        .header(HEADER, ALICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BUSY"}"""))
                .andExpect(status().isNoContent());

        // then
        assertThat(storedSlotsOn(alice, "2026-11-07"))
                .containsExactly("2026-11-07T09:00:00Z BUSY");
    }

    @Test
    void deleteSlot_ownSlot_removesIt() throws Exception {
        // given
        long alice = userId("alice@example.com");
        long slotId = insertFreeSlot(alice, "2026-11-08T09:00:00Z");

        // when
        mockMvc.perform(delete("/api/v1/slots/{id}", slotId).header(HEADER, ALICE_KEY))
                .andExpect(status().isNoContent());

        // then
        assertThat(storedSlotsOn(alice, "2026-11-08")).isEmpty();
    }

    @Test
    void deleteSlot_belongingToAnotherUser_isNotFoundAndLeavesItUntouched() throws Exception {
        // given
        long alice = userId("alice@example.com");
        long slotId = insertFreeSlot(alice, "2026-11-09T09:00:00Z");

        // when
        mockMvc.perform(delete("/api/v1/slots/{id}", slotId).header(HEADER, BOB_KEY))
                .andExpect(status().isNotFound());

        // then
        assertThat(storedSlotsOn(alice, "2026-11-09")).hasSize(1);
    }

    private ResultActions publish(String apiKey, String from, String to) throws Exception {
        return mockMvc.perform(post("/api/v1/slots")
                .header(HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"from":"%s","to":"%s"}""".formatted(from, to)));
    }

    private long userId(String email) {
        return requireNonNull(jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email));
    }

    private long insertFreeSlot(long userId, String startAt) {
        var start = Instant.parse(startAt);
        return requireNonNull(jdbc.queryForObject("""
                        INSERT INTO time_slot (user_id, start_at, end_at, status)
                        VALUES (?, ?, ?, 'FREE') RETURNING id
                        """,
                Long.class, userId, utc(start), utc(start.plus(1, ChronoUnit.HOURS))));
    }

    private List<String> storedSlotsOn(long userId, String day) {
        return jdbc.query("""
                        SELECT start_at, status FROM time_slot
                        WHERE user_id = ? AND start_at >= ? AND start_at < ?
                        ORDER BY start_at
                        """,
                (rs, rowNum) -> rs.getObject("start_at", OffsetDateTime.class).toInstant()
                        + " " + rs.getString("status"),
                userId, utc(Instant.parse(day + "T00:00:00Z")),
                utc(Instant.parse(day + "T00:00:00Z").plus(1, ChronoUnit.DAYS)));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
