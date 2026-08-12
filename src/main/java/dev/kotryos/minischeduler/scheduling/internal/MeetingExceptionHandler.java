package dev.kotryos.minischeduler.scheduling.internal;

import dev.kotryos.minischeduler.calendar.HourNotFreeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MeetingController.class)
class MeetingExceptionHandler {

    @ExceptionHandler(MeetingNotFoundException.class)
    ProblemDetail meetingNotFound(MeetingNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(HourNotFreeException.class)
    ProblemDetail hourNotFree(HourNotFreeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidHour(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
