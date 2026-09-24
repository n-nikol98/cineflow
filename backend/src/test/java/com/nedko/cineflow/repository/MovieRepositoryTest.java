package com.nedko.cineflow.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.repository.model.MovieKey;
import jakarta.persistence.EntityManager;

@DataJpaTest
class MovieRepositoryTest {

    private static final String NATURAL_KEY_MOVIE_TITLE = "Natural Key Movie";
    private static final String NATURAL_KEY_DIRECTOR_ID = "director-" + NATURAL_KEY_MOVIE_TITLE;
    private static final int RELEASE_YEAR = 2020;
    private static final int MISMATCHED_RELEASE_YEAR = 1999;

    @Autowired
    private MovieRepository movies;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsOnlyEligiblePendingDeliveriesInOldestFirstOrder() {
        final Instant now = Instant.now();
        final Movie oldest = persistMovie("Oldest", now.minus(3, ChronoUnit.HOURS));
        final Movie retriable = persistMovie("Retriable", now.minus(2, ChronoUnit.HOURS));
        retriable.getDelivery().markAttempt(now.minusSeconds(1));
        final Movie delayed = persistMovie("Delayed", now.minus(1, ChronoUnit.HOURS));
        delayed.getDelivery().markAttempt(now.plusSeconds(60));
        final Movie sent = persistMovie("Sent", now.minus(4, ChronoUnit.HOURS));
        sent.getDelivery().markSent();
        entityManager.flush();
        entityManager.clear();

        final List<Movie> eligible = movies.findAllEligibleForDelivery(Status.PENDING, now);

        assertEquals(List.of(oldest.getId(), retriable.getId()),
                eligible.stream().map(Movie::getId).toList());
    }

    @Test
    void findsMovieByItsNaturalKey() {
        final Movie movie = persistMovie(NATURAL_KEY_MOVIE_TITLE, Instant.now());
        entityManager.flush();
        entityManager.clear();

        final Optional<Movie> found = movies.findByKey(new MovieKey(NATURAL_KEY_MOVIE_TITLE, 
            NATURAL_KEY_DIRECTOR_ID, RELEASE_YEAR));

        assertTrue(found.isPresent());
        assertEquals(movie.getId(), found.get().getId());
    }

    @Test
    void findByKeyReturnsEmptyWhenNoMovieMatches() {
        persistMovie(NATURAL_KEY_MOVIE_TITLE, Instant.now());
        entityManager.flush();
        entityManager.clear();

        final Optional<Movie> found = movies.findByKey(new MovieKey(NATURAL_KEY_MOVIE_TITLE, 
            NATURAL_KEY_DIRECTOR_ID, MISMATCHED_RELEASE_YEAR));

        assertFalse(found.isPresent());
    }

    private Movie persistMovie(final String title, final Instant createdAt) {
        final ImportTask task = new ImportTask();
        task.setFileName(title + ".xml");
        entityManager.persist(task);

        final Director director = new Director("director-" + title, "Director " + title);
        entityManager.persist(director);

        final Movie movie = new Movie(task, title, director, RELEASE_YEAR);
        movie.setCreatedAt(createdAt);
        entityManager.persist(movie);
        return movie;
    }
}
