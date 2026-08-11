package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.SlotStatus;
import dev.kotryos.minischeduler.calendar.TimeRange;
import dev.kotryos.minischeduler.identity.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlotController.class)
class SlotControllerTest {

    private static final long ALICE = 1;
    private static final long ALICE_SLOT = 1000;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlotService slots;

    @Test
    void publish_validRange_forwardsItToTheServiceAndReturnsCreated() throws Exception {
        // given
        var published = TimeSlot.stored(ALICE_SLOT, ALICE, new Hour(at("09:00")), SlotStatus.FREE);
        given(slots.publish(anyLong(), any())).willReturn(List.of(published));

        // when
        mockMvc.perform(post("/api/v1/slots")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-11-01T09:00:00Z","to":"2026-11-01T12:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(ALICE_SLOT))
                .andExpect(jsonPath("$[0].startAt").value("2026-11-01T09:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-11-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("FREE"));

        // then
        verify(slots).publish(ALICE, new TimeRange(at("09:00"), at("12:00")));
    }

    @Test
    void publish_missingRangeBoundary_isRejectedAsAProblemDetail() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"2026-11-01T10:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publish_serviceReportsAConflict_isTranslatedToConflict() throws Exception {
        // given
        willThrow(new SlotConflictException("already published"))
                .given(slots).publish(anyLong(), any());

        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-11-01T09:00:00Z","to":"2026-11-01T10:00:00Z"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publish_serviceRejectsTheRange_isTranslatedToBadRequest() throws Exception {
        // given
        willThrow(new IllegalArgumentException("range does not cover a whole hour"))
                .given(slots).publish(anyLong(), any());

        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-11-01T09:15:00Z","to":"2026-11-01T09:45:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publish_databaseRejectsADuplicate_isTranslatedToConflict() throws Exception {
        // given
        willThrow(new DataIntegrityViolationException("duplicate key"))
                .given(slots).publish(anyLong(), any());

        // when / then
        mockMvc.perform(post("/api/v1/slots")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"2026-11-01T09:00:00Z","to":"2026-11-01T10:00:00Z"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void list_windowGivenAsParameters_forwardsThemToTheService() throws Exception {
        // given
        given(slots.list(anyLong(), any(), any())).willReturn(List.of());

        // when
        mockMvc.perform(get("/api/v1/slots")
                        .with(authentication(alice()))
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-02T00:00:00Z"))
                .andExpect(status().isOk());

        // then
        verify(slots).list(ALICE, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"));
    }

    @Test
    void changeStatus_statusGivenInBody_forwardsItToTheService() throws Exception {
        // when
        mockMvc.perform(patch("/api/v1/slots/{id}", 42)
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"BUSY"}"""))
                .andExpect(status().isNoContent());

        // then
        verify(slots).changeStatus(ALICE, 42, SlotStatus.BUSY);
    }

    @Test
    void delete_slotMissingForTheCaller_isTranslatedToNotFound() throws Exception {
        // given
        willThrow(new SlotNotFoundException(42)).given(slots).delete(ALICE, 42);

        // when / then
        mockMvc.perform(delete("/api/v1/slots/{id}", 42).with(authentication(alice())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static TestingAuthenticationToken alice() {
        return new TestingAuthenticationToken(new CurrentUser(ALICE), null, "ROLE_USER");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
