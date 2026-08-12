package dev.kotryos.minischeduler.scheduling.internal;

class MeetingNotFoundException extends RuntimeException {

    MeetingNotFoundException(long meetingId) {
        super("No meeting " + meetingId);
    }
}
