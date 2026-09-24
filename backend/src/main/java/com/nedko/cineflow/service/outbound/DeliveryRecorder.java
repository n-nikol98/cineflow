package com.nedko.cineflow.service.outbound;

import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.repository.MovieDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists {@link MovieDelivery} state changes on behalf of {@link DeliveryClient}.
 *
 * <p>Extracted into its own bean (rather than being methods on {@code DeliveryClient})
 * because Spring's {@code @Transactional} is implemented via a proxy: a method calling
 * another {@code @Transactional} method on {@code this} bypasses the proxy entirely, so
 * the annotation would be silently ignored. Routing through this separate bean makes
 * each call a genuine cross-bean call the proxy can intercept.
 *
 * <p>Class-level {@link Propagation#REQUIRES_NEW} applies to all three methods, so the
 * attempt, success, and failure records are committed independently of one another and
 * of the network call in {@code DeliveryClient#send}: an attempt is durably recorded
 * even if the subsequent HTTP call fails and triggers a retry, and no DB connection is
 * held open for the duration of that (potentially slow) network call.
 *
 * <p>{@code attempt}, {@code success}, and {@code failure} all operate on the same
 * in-memory {@code movie.getOrCreateDelivery()} instance across the whole retry loop in
 * {@code DeliveryClient#send} (it is loaded once, up front, by {@code DeliveryLoader}).
 * Because {@code MovieDelivery} now carries an optimistic-locking {@code version},
 * {@link #update} always replaces that in-memory instance with the freshly persisted
 * one after each save: without this, {@code attempt}'s save would advance the row's
 * version in the database while the movie's in-memory delivery kept the old value, so
 * the very next call ({@code success} or {@code failure}) would immediately conflict
 * with its own prior write, breaking every delivery, not just concurrent ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
class DeliveryRecorder {

    private final MovieDeliveryRepository repository;

    /**
     * Records a new delivery attempt for {@code movie}, bumping its attempt count and
     * timestamp.
     *
     * @param movie the movie being delivered
     */
    void attempt(final Movie movie) {
        update(movie, delivery -> delivery.markAttempt(Instant.now()));
    }

    /**
     * Marks {@code movie}'s delivery as successfully sent.
     *
     * @param movie the movie that was successfully delivered
     * @return the total number of attempts it took to deliver {@code movie}
     */
    int success(final Movie movie) {
        return update(movie, MovieDelivery::markSent).getAttempts();
    }

    /**
     * Marks {@code movie}'s delivery as permanently failed.
     *
     * @param movie the movie whose delivery failed
     * @param errorMessage the error describing why delivery failed
     */
    void failure(final Movie movie, final String errorMessage) {
        update(movie, delivery -> delivery.markFailed(errorMessage));
    }

    /**
     * Fetches-or-creates {@code movie}'s {@link MovieDelivery}, applies {@code mutation}
     * to it, and saves the result — the shared fetch/mutate/save shape behind all three
     * public methods above.
     *
     * <p>If this loses an optimistic-locking race against a concurrent update (e.g. a
     * manual retry request landing between two of this movie's own attempt/success/
     * failure calls), the concurrent update is left as-is rather than retried or
     * propagated as a failure: retrying here would mean re-running the whole
     * {@code @Retryable} send loop, including sending a duplicate outbound HTTP
     * request, merely because a bookkeeping write lost a race — the delivery's actual
     * outcome (sent, or not) has already happened either way, and is exactly what
     * whichever update won the race reflects.
     *
     * @param movie the movie whose delivery is being updated
     * @param mutation the state change to apply to the fetched-or-created delivery
     * @return the persisted, mutated delivery, or the unsaved in-memory one if a
     *         concurrent update won the race
     */
    private MovieDelivery update(final Movie movie, final Consumer<MovieDelivery> mutation) {
        final MovieDelivery delivery = movie.getOrCreateDelivery();
        mutation.accept(delivery);
        try {
            final MovieDelivery saved = repository.saveAndFlush(delivery);
            movie.setDelivery(saved);
            return saved;
        } catch (final ObjectOptimisticLockingFailureException exception) {
            log.warn("Delivery bookkeeping for movie {} lost an optimistic-locking race "
                    + "against a concurrent update (e.g. a manual retry); keeping "
                    + "whichever update won instead of retrying", movie.getId(), exception);
            return delivery;
        }
    }
}
