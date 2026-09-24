package com.nedko.cineflow.service.outbound;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.mapper.MovieMapper;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.service.outbound.model.DeliveryCandidate;
import lombok.RequiredArgsConstructor;

/**
 * Loads movies eligible for outbound delivery and maps them to {@link DeliveryCandidate}s.
 *
 * <p>Extracted into its own bean (rather than being a method on {@link DeliveryScheduler})
 * so its {@code @Transactional} boundary can be scoped tightly around just the query and
 * the mapping: a plain {@code private}/self-invoked method on {@code DeliveryScheduler}
 * would bypass Spring's transactional proxy entirely, and a {@code public} one would still
 * force the transaction to wrap whatever the caller does next unless it returns
 * immediately. Routing through this separate bean means {@link DeliveryScheduler} calls it
 * once, gets back plain, already-mapped {@link DeliveryCandidate}s, and the transaction
 * (and its DB connection) is released the moment this method returns — well before any
 * outbound HTTP call, retry backoff, or {@code CompletableFuture} join happens.
 */
@Service
@RequiredArgsConstructor
class DeliveryLoader {

    private final MovieRepository repository;
    private final MovieMapper mapper;

    /**
     * Queries and maps all movies currently eligible for outbound delivery.
     *
     * @return the eligible movies, each paired with its already-mapped payload
     */
    @Transactional(readOnly = true)
    List<DeliveryCandidate> findEligible() {
        return repository.findAllEligibleForDelivery(Status.PENDING, Instant.now()).stream()
                .map(movie -> new DeliveryCandidate(movie, mapper.toDto(movie)))
                .toList();
    }
}
