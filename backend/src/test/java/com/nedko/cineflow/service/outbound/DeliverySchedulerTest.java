package com.nedko.cineflow.service.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.nedko.cineflow.config.AppProperties;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.dto.DeliveryStatusDto;
import com.nedko.cineflow.exception.DeliveryConflictException;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.mapper.ActorMapper;
import com.nedko.cineflow.mapper.DeliveryStatusMapper;
import com.nedko.cineflow.mapper.DirectorMapper;
import com.nedko.cineflow.mapper.MovieMapper;
import com.nedko.cineflow.repository.MovieDeliveryRepository;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.service.outbound.model.DeliveryCandidate;

/**
 * Tests {@link DeliveryScheduler}, covering each of its four public methods:
 * <ul>
 *   <li>{@link DeliveryScheduler#scheduledSend()} — the automatic, cron-driven entry
 *       point. Verifies it dispatches every eligible movie returned by
 *       {@code DeliveryLoader} and does nothing at all (not even loading candidates)
 *       when no outbound URL is configured.</li>
 *   <li>{@link DeliveryScheduler#findFailed(Pageable)} — verifies the requested page of
 *       {@code FAILED} deliveries is fetched from the repository and mapped to DTOs via
 *       {@link DeliveryStatusMapper}.</li>
 *   <li>{@link DeliveryScheduler#status(Long)} — verifies a single movie's delivery is
 *       looked up and mapped to a DTO, and that a missing delivery surfaces as a
 *       {@link NotFoundException}.</li>
 *   <li>{@link DeliveryScheduler#retry(Long)} — verifies a manual retry resets the
 *       delivery back to {@code PENDING}, and that losing the optimistic-locking race
 *       against a concurrent, in-flight scheduled delivery attempt is translated into a
 *       clean {@link DeliveryConflictException} instead of either propagating the raw
 *       persistence exception or silently overwriting the concurrent change.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeliverySchedulerTest {

    private static final Long MOVIE_ID = 1L;
    private static final Long SECOND_MOVIE_ID = 2L;
    private static final Long MISSING_MOVIE_ID = 99L;
    private static final String FAILURE_MESSAGE = "boom";
    private static final String DELIVERY_URL = "http://receiver";
    private static final String BLANK_DELIVERY_URL = " ";
    private static final String CRON_EXPRESSION = "-";
    private static final int DELIVERY_CONCURRENCY = 2;
    private static final String CONFLICT_MESSAGE = "Movie " + MOVIE_ID + "'s delivery is "
            + "currently being processed; please try again shortly.";

    @Mock
    private MovieRepository repository;

    @Mock
    private MovieDeliveryRepository deliveryRepository;

    @Mock
    private DeliveryLoader deliveryLoader;

    @Mock
    private AppProperties properties;

    @Mock
    private DeliveryClient client;

    @Spy
    private DeliveryStatusMapper statusMapper;

    // Not a dependency of DeliveryScheduler, so it isn't wired via @InjectMocks;
    private final MovieMapper movieMapper = new MovieMapper(new ActorMapper(), new DirectorMapper());

    @InjectMocks
    private DeliveryScheduler scheduler;

    @Test
    void scheduledSendDispatchesEveryEligibleMovieAndWaitsForTheBatch() {
        final Movie first = movieWithDelivery();
        first.setId(MOVIE_ID);
        final Movie second = movieWithDelivery();
        second.setId(SECOND_MOVIE_ID);
        final DeliveryCandidate firstCandidate = new DeliveryCandidate(first, movieMapper.toDto(first));
        final DeliveryCandidate secondCandidate = new DeliveryCandidate(second, movieMapper.toDto(second));
        when(properties.delivery()).thenReturn(new AppProperties.Delivery(DELIVERY_URL, 
            CRON_EXPRESSION, DELIVERY_CONCURRENCY));
        when(deliveryLoader.findEligible()).thenReturn(List.of(firstCandidate, secondCandidate));
        when(client.send(firstCandidate)).thenReturn(CompletableFuture.completedFuture(true));
        when(client.send(secondCandidate)).thenReturn(CompletableFuture.completedFuture(false));

        scheduler.scheduledSend();

        verify(deliveryLoader).findEligible();
        verify(client).send(firstCandidate);
        verify(client).send(secondCandidate);
    }

    @Test
    void scheduledSendDoesNothingWhenDeliveryUrlIsBlank() {
        when(properties.delivery()).thenReturn(new AppProperties.Delivery(BLANK_DELIVERY_URL, 
            CRON_EXPRESSION, DELIVERY_CONCURRENCY));

        scheduler.scheduledSend();

        verifyNoInteractions(deliveryLoader, client);
    }

    @Test
    void findFailedReturnsMappedPageOfFailedDeliveries() {
        final Movie movie = movieWithDelivery();
        movie.setId(MOVIE_ID);
        final MovieDelivery delivery = movie.getOrCreateDelivery();
        final Pageable pageable = PageRequest.of(0, 20);
        final Page<MovieDelivery> deliveryPage = new PageImpl<>(List.of(delivery), pageable, 1);
        final DeliveryStatusDto dto = statusMapper.toDto(delivery);
        when(deliveryRepository.findByStatus(Status.FAILED, pageable)).thenReturn(deliveryPage);

        final Page<DeliveryStatusDto> result = scheduler.findFailed(pageable);

        assertEquals(List.of(dto), result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void statusReturnsMappedDtoForExistingDelivery() {
        final Movie movie = movieWithDelivery();
        movie.setId(MOVIE_ID);
        final MovieDelivery delivery = movie.getOrCreateDelivery();
        final DeliveryStatusDto dto = statusMapper.toDto(delivery);
        when(deliveryRepository.findByMovieId(MOVIE_ID)).thenReturn(Optional.of(delivery));

        final DeliveryStatusDto result = scheduler.status(MOVIE_ID);

        assertEquals(dto, result);
    }

    @Test
    void statusThrowsNotFoundExceptionWhenDeliveryIsMissing() {
        when(deliveryRepository.findByMovieId(MISSING_MOVIE_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> scheduler.status(MISSING_MOVIE_ID));

        verify(deliveryRepository).findByMovieId(MISSING_MOVIE_ID);
        verifyNoInteractions(statusMapper);
    }

    @Test
    void retryResetsDeliveryWhenNoConcurrentUpdateIsInFlight() {
        final Movie movie = movieWithDelivery();
        when(repository.findByIdOrThrow(MOVIE_ID)).thenReturn(movie);
        when(deliveryRepository.saveAndFlush(any(MovieDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.retry(MOVIE_ID);

        final MovieDelivery delivery = movie.getOrCreateDelivery();
        assertEquals(Status.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
        verify(deliveryRepository).saveAndFlush(delivery);
    }

    @Test
    void retryThrowsDeliveryConflictWhenConcurrentUpdateWinsTheRace() {
        final Movie movie = movieWithDelivery();
        when(repository.findByIdOrThrow(MOVIE_ID)).thenReturn(movie);
        when(deliveryRepository.saveAndFlush(any(MovieDelivery.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(MovieDelivery.class, MOVIE_ID));

        final DeliveryConflictException exception = assertThrows(DeliveryConflictException.class, 
            () -> scheduler.retry(MOVIE_ID));

        assertEquals(CONFLICT_MESSAGE, exception.getMessage());
    }

    private static Movie movieWithDelivery() {
        final Movie movie = new Movie(new ImportTask(), "Some Movie",
            new Director("dir-1", "Some Director"), 2020);
        movie.getOrCreateDelivery().markFailed(FAILURE_MESSAGE);
        return movie;
    }
}
