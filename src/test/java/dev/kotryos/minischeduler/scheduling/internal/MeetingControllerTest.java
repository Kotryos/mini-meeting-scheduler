package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.calendar.HourNotFreeException;
import dev.kotryos.minischeduler.identity.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    private static final long ALICE = 1;
    private static final long BOB = 2;
    private static final long MEETING = 500;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetingService meetings;

    @Test
    void schedule_validRequest_forwardsItToTheServiceAndReturnsCreated() throws Exception {
        // given
        given(meetings.schedule(anyLong(), anyString(), any(), any(), any())).willReturn(view());

        // when
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Standup","description":"Daily",
                                 "startAt":"2026-11-01T09:00:00Z","participantIds":[2]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MEETING))
                .andExpect(jsonPath("$.title").value("Standup"))
                .andExpect(jsonPath("$.startAt").value("2026-11-01T09:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-11-01T10:00:00Z"))
                .andExpect(jsonPath("$.participantIds[1]").value(BOB));

        // then
        verify(meetings).schedule(ALICE, "Standup", "Daily", new Hour(at("09:00")), Set.of(BOB));
    }

    @Test
    void schedule_noParticipantsGiven_bookstheHourForTheOrganiserAlone() throws Exception {
        // given
        given(meetings.schedule(anyLong(), anyString(), any(), any(), any())).willReturn(view());

        // when
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Focus time","startAt":"2026-11-01T09:00:00Z"}"""))
                .andExpect(status().isCreated());

        // then
        verify(meetings).schedule(ALICE, "Focus time", null, new Hour(at("09:00")), Set.of());
    }

    @Test
    void schedule_startNotOnAWholeHour_isRejectedAsAProblemDetail() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Standup","startAt":"2026-11-01T09:30:00Z","participantIds":[2]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void schedule_blankTitle_isRejectedAsAProblemDetail() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","startAt":"2026-11-01T09:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void schedule_titleLongerThanTheColumn_isRejectedBeforeItReachesTheDatabase() throws Exception {
        // when / then
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","startAt":"2026-11-01T09:00:00Z"}"""
                                .formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void schedule_someoneIsNotFree_isTranslatedToConflict() throws Exception {
        // given
        willThrow(new HourNotFreeException("not everyone is free"))
                .given(meetings).schedule(anyLong(), anyString(), any(), any(), any());

        // when / then
        mockMvc.perform(post("/api/v1/meetings")
                        .with(authentication(alice())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Standup","startAt":"2026-11-01T09:00:00Z","participantIds":[2]}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void list_callerInMeetings_returnsThem() throws Exception {
        // given
        given(meetings.listFor(ALICE)).willReturn(List.of(view()));

        // when / then
        mockMvc.perform(get("/api/v1/meetings").with(authentication(alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(MEETING))
                .andExpect(jsonPath("$[0].title").value("Standup"));
    }

    @Test
    void find_existingMeeting_returnsIt() throws Exception {
        // given
        given(meetings.find(ALICE, MEETING)).willReturn(view());

        // when / then
        mockMvc.perform(get("/api/v1/meetings/{id}", MEETING).with(authentication(alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Standup"));
    }

    @Test
    void find_meetingTheCallerCannotSee_isTranslatedToNotFound() throws Exception {
        // given
        willThrow(new MeetingNotFoundException(MEETING)).given(meetings).find(ALICE, MEETING);

        // when / then
        mockMvc.perform(get("/api/v1/meetings/{id}", MEETING).with(authentication(alice())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancel_ownMeeting_forwardsItToTheService() throws Exception {
        // when
        mockMvc.perform(delete("/api/v1/meetings/{id}", MEETING)
                        .with(authentication(alice())).with(csrf()))
                .andExpect(status().isNoContent());

        // then
        verify(meetings).cancel(ALICE, MEETING);
    }

    private static MeetingView view() {
        return new MeetingView(MEETING, "Standup", "Daily", at("09:00"), at("10:00"), List.of(ALICE, BOB));
    }

    private static TestingAuthenticationToken alice() {
        return new TestingAuthenticationToken(new CurrentUser(ALICE), null, "ROLE_USER");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-11-01T" + time + ":00Z");
    }
}
