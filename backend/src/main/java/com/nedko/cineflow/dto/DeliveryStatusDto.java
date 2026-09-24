package com.nedko.cineflow.dto;

import java.time.Instant;

import com.nedko.cineflow.domain.MovieDelivery.Status;

public record DeliveryStatusDto(
        Long movieId,
        Status status,
        int attempts,
        Instant lastAttemptAt,
        String lastError) {
}
