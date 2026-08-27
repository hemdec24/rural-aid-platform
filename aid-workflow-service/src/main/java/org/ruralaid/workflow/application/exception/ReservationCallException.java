package org.ruralaid.workflow.application.exception;

public final class ReservationCallException extends RuntimeException {

    public ReservationCallException(String message) {
        super(message);
    }

    public ReservationCallException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}