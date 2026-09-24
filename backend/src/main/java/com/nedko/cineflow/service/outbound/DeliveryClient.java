package com.nedko.cineflow.service.outbound;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.nedko.cineflow.config.AppProperties;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.service.outbound.model.DeliveryCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends a single movie to the configured outbound endpoint, retrying transient
 * failures with bounded exponential backoff, and records the outcome via
 * {@link DeliveryRecorder}.
 *
 * <p>Both {@code @Async} and {@code @Retryable} apply to {@link #send}, so a whole
 * retry loop (including its backoff sleeps) runs on {@code deliveryExecutor} rather than
 * blocking the caller. If every attempt fails, {@link #recover} runs instead, recording
 * the permanent failure and returning a completed (not exceptional) future so the caller
 * doesn't need to unwrap a {@code CompletionException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DeliveryClient {

    private final AppProperties properties;
    private final DeliveryRecorder recorder;
    private final RestClient client = RestClient.create();

    /**
     * Takes an already-mapped {@link DeliveryCandidate} rather than mapping the movie
     * itself: this method runs {@code @Async} (on {@code deliveryExecutor}), off the
     * thread that loaded {@code candidate.movie()}, so its lazily-fetched associations
     * (genres, actors, director) are no longer reachable on it. The caller
     * ({@code DeliveryScheduler.scheduledSend}, via {@code DeliveryLoader}) maps the DTO
     * synchronously, inside its own transaction, before dispatching here.
     * {@code candidate.movie()} itself is still needed for its identifier and its
     * (effectively eager) {@code delivery} association, both of which
     * {@link DeliveryRecorder} needs for bookkeeping.
     *
     * <p>Returns {@code CompletableFuture<Boolean>} (rather than {@code Void}) so
     * callers can both wait for the whole batch of deliveries to finish and tally how
     * many succeeded vs. failed; this is also required for {@code @Async} to be
     * awaitable at all. Relies on the explicit {@code @EnableAsync}/{@code @EnableRetry}
     * ordering in {@code AsyncRetryConfig} so the whole retry loop (including backoff
     * sleeps) runs on {@code deliveryExecutor}, not just each individual attempt.
     *
     * @param candidate the movie to deliver, paired with its already-mapped payload
     * @return a future completed with {@code true} once the movie has been
     *         successfully delivered
     */
    @Async("deliveryExecutor")
    @Retryable(maxAttemptsExpression = "${app.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.initial-delay:1000}",
                    multiplierExpression = "${app.retry.multiplier:2}",
                    maxDelayExpression = "${app.retry.max-delay:30000}"))
    CompletableFuture<Boolean> send(final DeliveryCandidate candidate) {
        final Movie movie = candidate.movie();
        final Long id = movie.getId();
        final String url = properties.delivery().url();
        log.debug("Attempting delivery of movie {} to {}", id, url);

        recorder.attempt(movie);

        client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(candidate.payload())
                .retrieve()
                .toBodilessEntity();

        final int attempts = recorder.success(movie);
        log.info("Successfully delivered movie {} after {} attempt(s)", id, attempts);
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Invoked by {@code @Retryable} once every attempt of {@link #send} has been
     * exhausted; records the permanent failure and returns {@code false} instead of
     * letting the exception propagate.
     *
     * @param exception the exception from the last failed attempt
     * @param candidate the movie whose delivery permanently failed
     * @return a future completed with {@code false}
     */
    @Recover
    CompletableFuture<Boolean> recover(final RuntimeException exception,
            final DeliveryCandidate candidate) {
        final Movie movie = candidate.movie();
        final String errorMessage = exception.getMessage();
        log.error("Delivery of movie {} failed permanently after exhausting retries: {}",
                movie.getId(), errorMessage, exception);
        recorder.failure(movie, errorMessage);
        return CompletableFuture.completedFuture(false);
    }
}
