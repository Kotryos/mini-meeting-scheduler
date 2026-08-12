package dev.kotryos.minischeduler.calendar;

public class HourNotFreeException extends RuntimeException {

    public HourNotFreeException(String message) {
        super(message);
    }
}
