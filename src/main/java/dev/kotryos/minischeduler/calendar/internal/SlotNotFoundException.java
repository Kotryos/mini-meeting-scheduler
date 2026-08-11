package dev.kotryos.minischeduler.calendar.internal;

class SlotNotFoundException extends RuntimeException {

    SlotNotFoundException(long slotId) {
        super("No slot %d belonging to the current user".formatted(slotId));
    }
}
