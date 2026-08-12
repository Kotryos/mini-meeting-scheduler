package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.Hour;
import dev.kotryos.minischeduler.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/meetings")
class MeetingController {

    private final MeetingService meetings;

    MeetingController(MeetingService meetings) {
        this.meetings = meetings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MeetingView schedule(@AuthenticationPrincipal CurrentUser user,
                         @Valid @RequestBody ScheduleRequest request) {
        return meetings.schedule(user.id(),
                request.title(),
                request.description(),
                new Hour(request.startAt()),
                request.participantIds() == null ? Set.of() : request.participantIds());
    }

    @GetMapping
    List<MeetingView> list(@AuthenticationPrincipal CurrentUser user) {
        return meetings.listFor(user.id());
    }

    @GetMapping("/{id}")
    MeetingView find(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return meetings.find(user.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        meetings.cancel(user.id(), id);
    }

    record ScheduleRequest(@NotBlank @Size(max = 200) String title,
                           @Size(max = 2000) String description,
                           @NotNull Instant startAt,
                           Set<Long> participantIds) {
    }
}
