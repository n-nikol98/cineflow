package com.nedko.cineflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.repository.model.MovieKey;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Looks up a movie by id, throwing instead of returning an empty {@code Optional}.
     *
     * @param id the movie's identifier
     * @return the movie
     * @throws NotFoundException if no movie exists with the given id
     */
    default Movie findByIdOrThrow(final Long id) {
        return findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Looks up a movie by its natural key (title, director's external id, and release
     * year) — the same key used to detect duplicates on import and referenced in the
     * error message of a lost duplicate-import race (see {@code ImportWriter}'s class
     * Javadoc). This lets a caller who only has that natural key (e.g. from a failed
     * import's error message) resolve it to a movie id, e.g. to look up its delivery
     * status, without paging through every movie by hand.
     *
     * <p>The query reads {@code key}'s components by name via SpEL, so
     * {@link MovieKey} is the single place that defines what the natural key is
     * — adding or removing a field only means changing that record and this query.
     * {@code key.directorId()} is matched against {@code Movie.director.externalId},
     * since that's the id {@code MovieKey} means by "directorId" — see that
     * record's Javadoc.
     *
     * @param key the natural key to look up
     * @return the matching movie, if any
     */
    @Query("""
            SELECT movie
            FROM Movie movie
            WHERE movie.title = :#{#key.title}
              AND movie.director.externalId = :#{#key.directorId}
              AND movie.releaseYear = :#{#key.releaseYear}
            """)
    Optional<Movie> findByKey(@Param("key") final MovieKey key);

    /**
     * Looks up a movie by its natural key, throwing instead of returning an empty
     * {@code Optional} — see {@link #findByKey} for what the natural key is and why
     * this lookup is useful.
     *
     * @param key the natural key to look up
     * @return the matching movie
     * @throws NotFoundException if no movie matches that natural key
     */
    default Movie findByKeyOrThrow(final MovieKey key) {
        return findByKey(key).orElseThrow(() -> notFound(key));
    }

    /**
     * Finds movies whose delivery is still {@code status} and, if a previous delivery
     * attempt was made, whose backoff delay has already elapsed (i.e.
     * {@code lastAttemptAt <= now}) — movies never attempted before
     * ({@code lastAttemptAt IS NULL}) are always eligible. Results are ordered oldest
     * first ({@code createdAt} ascending) so older imports are delivered before newer
     * ones.
     *
     * @param status the delivery status to match (typically {@code PENDING})
     * @param now the current time, compared against each movie's backoff delay
     * @return the eligible movies, oldest first
     */
    @Query("""
            SELECT movie
            FROM Movie movie
            JOIN movie.delivery delivery
            WHERE delivery.status = :status
              AND (
                  delivery.lastAttemptAt IS NULL
                  OR delivery.lastAttemptAt <= :now
              )
            ORDER BY movie.createdAt ASC
            """)
    List<Movie> findAllEligibleForDelivery(@Param("status") final Status status,
            @Param("now") final Instant now);

    boolean existsByTitleAndDirectorAndReleaseYear(final String title, 
        final Director director, final Integer releaseYear);

    /**
     * Builds the exception thrown when no movie matches a lookup, whether by id
     * ({@link #findByIdOrThrow}) or by natural key ({@link #findByKeyOrThrow}).
     *
     * @param id the id or natural key that no movie matched
     * @return the exception to throw
     */
    private NotFoundException notFound(final Object id) {
        return NotFoundException.forEntity("Movie", id);
    }
}
