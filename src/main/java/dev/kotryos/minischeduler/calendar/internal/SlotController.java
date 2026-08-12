package dev.kotryos.minischeduler.calendar.internal;

import dev.kotryos.minischeduler.calendar.SlotStatus;
import dev.kotryos.minischeduler.calendar.TimeRange;
import dev.kotryos.minischeduler.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/slots")
class SlotController {

    private final SlotService slots;

    SlotController(SlotService slots) {
        this.slots = slots;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    List<SlotView> publish(@AuthenticationPrincipal CurrentUser user,
                           @Valid @RequestBody PublishRequest request) {
        return slots.publish(user.id(), new TimeRange(request.from(), request.to()));
    }

    @GetMapping
    List<SlotView> list(@AuthenticationPrincipal CurrentUser user,
                        @RequestParam Instant from,
                        @RequestParam Instant to,
                        @RequestParam(required = false) SlotStatus status) {
        return slots.list(user.id(), new TimeRange(from, to), status);
    }

    @GetMapping("/summary")
    List<AggregatedSlotView> summary(@AuthenticationPrincipal CurrentUser user,
                                 @RequestParam Instant from,
                                 @RequestParam Instant to) {
        return slots.summary(user.id(), new TimeRange(from, to));
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeStatus(@AuthenticationPrincipal CurrentUser user,
                      @PathVariable long id,
                      @Valid @RequestBody StatusRequest request) {
        slots.changeStatus(user.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        slots.delete(user.id(), id);
    }

    record PublishRequest(@NotNull Instant from, @NotNull Instant to) {
    }

    record StatusRequest(@NotNull SlotStatus status) {
    }
}
