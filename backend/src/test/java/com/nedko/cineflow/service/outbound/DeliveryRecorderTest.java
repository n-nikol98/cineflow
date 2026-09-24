package com.nedko.cineflow.service.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.repository.MovieDeliveryRepository;

/**
 * Tests {@link DeliveryRecorder#success(Movie)}, which is called after a movie has
 * been delivered successfully and saves that outcome to the database.
 *
 * <p>Two scenarios are covered:
 * <ul>
 *   <li>The normal case — the save succeeds, and the movie's in-memory delivery is
 *       swapped out for the copy that was just saved (which has a bumped version
 *       number and an updated attempt count).</li>
 *   <li>A concurrent-update case — the save fails with
 *       {@link ObjectOptimisticLockingFailureException} because another thread saved
 *       the same delivery first. This must not throw back out to the caller (which
 *       would otherwise trigger a retry that re-sends the movie); it should simply be
 *       swallowed.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryRecorderTest {

    private static final long PERSISTED_VERSION = 5;
    private static final int PERSISTED_ATTEMPTS = 3;

    @Mock
    private MovieDeliveryRepository repository;

    @InjectMocks
    private DeliveryRecorder recorder;

    @Captor
    private ArgumentCaptor<MovieDelivery> deliveryCaptor;

    @Test
    void successReplacesTheMoviesInMemoryDeliveryWithThePersistedCopy() {
        final Movie movie = movie();
        final MovieDelivery unsaved = movie.getOrCreateDelivery();
        // A distinct, already-persisted instance: version and attempts came back from
        // the database, so they must differ from the movie's original in-memory values.
        final MovieDelivery persisted = movie().getOrCreateDelivery();
        persisted.setVersion(PERSISTED_VERSION);
        persisted.markSent();
        IntStream.range(0, PERSISTED_ATTEMPTS).forEach(i -> persisted.markAttempt(Instant.now()));
        when(repository.saveAndFlush(any(MovieDelivery.class))).thenReturn(persisted);

        final int attempts = recorder.success(movie);

        verify(repository).saveAndFlush(deliveryCaptor.capture());
        final MovieDelivery captured = deliveryCaptor.getValue();
        assertSame(unsaved, captured);
        assertNotSame(persisted, captured);
        assertEquals(Status.SENT, captured.getStatus());

        assertEquals(PERSISTED_ATTEMPTS, attempts);
        final MovieDelivery current = movie.getOrCreateDelivery();
        assertSame(persisted, current);
        assertNotSame(unsaved, current);
        assertEquals(PERSISTED_VERSION, current.getVersion());
    }

    @Test
    void aLostOptimisticLockingRaceIsAbsorbedRatherThanPropagated() {
        final Movie movie = movie();
        final MovieDelivery unsaved = movie.getOrCreateDelivery();
        when(repository.saveAndFlush(any(MovieDelivery.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(MovieDelivery.class, 
                    1L));

        final int attempts = recorder.success(movie);

        // No exception propagates (which would otherwise make @Retryable re-run the
        // whole send loop, including re-sending the movie); the in-memory delivery
        // (whichever way the race actually resolved) is simply left as-is.
        assertEquals(0, attempts);
        // The failed save's mutation still happened in-memory (it was applied before
        // the save attempt), but no swap took place: the movie keeps its original,
        // unpersisted delivery instance rather than adopting a saved copy.
        final MovieDelivery current = movie.getOrCreateDelivery();
        assertSame(unsaved, current);
        assertEquals(Status.SENT, current.getStatus());
    }

    private static Movie movie() {
        return new Movie(new ImportTask(), "Some Movie", 
            new Director("dir-1", "Some Director"), 2020);
    }
}

