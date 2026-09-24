package com.nedko.cineflow.mapper;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.dto.DeliveryStatusDto;

@Component
public class DeliveryStatusMapper {

    public DeliveryStatusDto toDto(final MovieDelivery delivery) {
        return new DeliveryStatusDto(
                delivery.getMovie().getId(),
                delivery.getStatus(),
                delivery.getAttempts(),
                delivery.getLastAttemptAt(),
                delivery.getLastError());
    }
}
