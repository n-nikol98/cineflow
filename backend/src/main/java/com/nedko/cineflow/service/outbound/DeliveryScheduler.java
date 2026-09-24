package com.nedko.cineflow.service.outbound;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.config.AppProperties;
import com.nedko.cineflow.config.AppProperties.Delivery;
import com.nedko.cineflow.dto.DeliveryStatusDto;
import com.nedko.cineflow.exception.DeliveryConflictException;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.mapper.DeliveryStatusMapper;
import com.nedko.cineflow.repository.MovieDeliveryRepository;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.service.outbound.model.DeliveryCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically sends pending movies to the outbound endpoint and exposes their
 * delivery status.
 *
 * <p>{@link #scheduledSend} is the automatic entry point, driven by
 * {@code app.delivery.cron}; {@link #findFailed}, {@link #status}, and {@link #retry}
 * support inspecting and manually retrying deliveries outside that schedule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryScheduler {

    private final MovieRepository repository;
    private final MovieDeliveryRepository deliveryRepository;
    private final DeliveryLoader deliveryLoader;
    private final AppProperties properties;
    private final DeliveryClient client;
    private final DeliveryStatusMapper statusMapper;

    /**
     * Not transactional itself: {@code deliveryLoader.findEligible()} is a single
     * cross-bean call scoped to its own short, read-only transaction (see
     * {@link DeliveryLoader}), which returns already-mapped
     * {@link DeliveryCandidate}s — so no lazy association (genres, actors, director) is
     * ever touched here or in {@link DeliveryClient}. Wrapping this method itself would
     * hold a DB connection open for the whole scheduled run, including every outbound
     * HTTP call and retry backoff across every eligible movie; scoping the transaction
     * to just the load step avoids that while still avoiding an ugly multi-collection
     * fetch-join query.
     *
     * <p>{@code client.send(candidate)} is itself {@code @Async} (bounded by
     * {@code app.delivery.concurrency}, on {@code deliveryExecutor}), so movies are
     * already sent concurrently — this method collects the returned futures, waits for
     * the whole batch to finish, and logs how many succeeded vs. failed, so two runs
     * can never overlap and re-select the same still-{@code PENDING} movie. Any
     * unexpected exception from {@code client.send} (anything not already handled by
     * {@code @Retryable}/{@code @Recover}) is caught per-movie and logged as a failed
     * delivery rather than aborting the batch.
     */
    @Scheduled(cron = "${app.delivery.cron:-}")
    public void scheduledSend() {
        Optional.ofNullable(properties.delivery())
                .map(Delivery::url).filter(Predicate.not(Strings::isBlank))
                .ifPresent(url -> {
                    final List<DeliveryCandidate> candidates = deliveryLoader.findEligible();
                    log.info("Scheduled delivery run found {} movie(s) eligible for delivery",
                            candidates.size());
                    final List<CompletableFuture<Boolean>> deliveries = candidates.stream()
                            .map(candidate -> {
                                try {
                                    return client.send(candidate);
                                } catch (final RuntimeException exception) {
                                    log.error("Unexpected error sending movie {}",
                                            candidate.movie().getId(), exception);
                                    return CompletableFuture.completedFuture(false);
                                }
                            })
                            .toList();
                    final long succeeded = CompletableFuture
                            .allOf(deliveries.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> deliveries.stream()
                                    .filter(CompletableFuture::join)
                                    .count())
                            .join();
                    log.info("Scheduled delivery run finished: {} succeeded, {} failed",
                            succeeded, candidates.size() - succeeded);
                });
    }

    /**
     * Returns a page of movies whose delivery has permanently failed (retries exhausted).
     *
     * @param pageable the requested page and sort
     * @return a page of failed delivery statuses
     */
    @Transactional(readOnly = true)
    public Page<DeliveryStatusDto> findFailed(final Pageable pageable) {
        return deliveryRepository.findByStatus(Status.FAILED, pageable)
                .map(statusMapper::toDto);
    }

    /**
     * Returns the current delivery status of a single movie.
     *
     * @param movieId the movie's identifier
     * @return the movie's current delivery status
     * @throws NotFoundException if no delivery exists for {@code movieId}
     */
    @Transactional(readOnly = true)
    public DeliveryStatusDto status(final Long movieId) {
        final MovieDelivery delivery = deliveryRepository.findByMovieId(movieId)
                .orElseThrow(() -> NotFoundException
                    .forEntity("Delivery for movie", movieId));
        return statusMapper.toDto(delivery);
    }

    /**
     * Resets a movie's delivery back to {@code PENDING} so the next scheduled run picks
     * it up again, regardless of its previous status or attempt count.
     *
     * <p>Guarded by {@code MovieDelivery}'s optimistic-locking {@code version}: if a
     * scheduled delivery attempt for the same movie is concurrently in flight and
     * commits its own change (e.g. marking it {@code SENT} or {@code FAILED}) between
     * this method reading the delivery and saving its reset, the save below detects the
     * stale version and fails with {@link ObjectOptimisticLockingFailureException}
     * rather than silently overwriting that concurrent change. That is translated here
     * into a {@link DeliveryConflictException} so callers see a clear, actionable
     * conflict instead of a raw persistence exception; simply retrying the request
     * afterwards succeeds once the in-flight attempt has settled.
     *
     * @param movieId the movie's identifier
     * @throws DeliveryConflictException if a concurrent update is in flight for the
     *         same movie's delivery
     */
    @Transactional
    public void retry(final Long movieId) {
        log.info("Manual delivery retry requested for movie {}", movieId);
        final Movie movie = repository.findByIdOrThrow(movieId);
        final MovieDelivery delivery = movie.getOrCreateDelivery();
        delivery.reset();
        try {
            deliveryRepository.saveAndFlush(delivery);
        } catch (final ObjectOptimisticLockingFailureException exception) {
            log.warn("Manual delivery retry for movie {} conflicted with a concurrent update", 
                movieId, exception);
            throw new DeliveryConflictException("Movie " + movieId + "'s delivery is "
                    + "currently being processed; please try again shortly.");
        }
    }
}
