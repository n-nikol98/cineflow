package com.nedko.cineflow.exception;

/**
 * Thrown when a manual delivery retry loses an optimistic-locking race against a
 * concurrent update to the same {@code MovieDelivery}, typically an in-flight
 * scheduled delivery attempt for the same movie committing at the same time.
 */
public class DeliveryConflictException extends RuntimeException {

    public DeliveryConflictException(final String message) {
        super(message);
    }
}
