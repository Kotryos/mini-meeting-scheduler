package dev.kotryos.minischeduler.calendar.internal;

class SlotConflictException extends RuntimeException {

    SlotConflictException(String message) {
        super(message);
    }
}
