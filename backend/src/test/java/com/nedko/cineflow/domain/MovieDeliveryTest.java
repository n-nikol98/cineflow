package com.nedko.cineflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import com.nedko.cineflow.domain.MovieDelivery.Status;

class MovieDeliveryTest {

    @Test
    void resetReturnsFailedDeliveryToPendingState() {
        final Movie movie = new Movie(new ImportTask(), "Example", 
            new Director("nm-dir", "Director"), 2020);
        final MovieDelivery delivery = movie.getOrCreateDelivery();
        delivery.markAttempt(Instant.now());
        delivery.markFailed("destination unavailable");

        assertEquals(Status.FAILED, delivery.getStatus());

        delivery.reset();

        assertEquals(Status.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
        assertNull(delivery.getLastError());
    }
}
