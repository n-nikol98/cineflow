package com.nedko.cineflow.service.inbound;

import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.mapper.xml.MovieXmlMapper;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.service.inbound.reference.ReferenceDataResolver;
import com.nedko.cineflow.xml.MovieXml;
import com.nedko.cineflow.xml.MoviesXml;

/**
 * Persists the movies of a parsed XML import, along with their directors, actors, and
 * genres, reusing existing directors/actors/genres where possible.
 *
 * <p>All movies in a single file are persisted in one all-or-nothing transaction: if
 * any movie fails to persist for any reason, including losing a concurrent
 * check-then-insert race against another import task inserting the exact same movie
 * (same title, director, and release year), the whole transaction rolls back and no
 * movie from this file is kept. This keeps the import's outcome simple to reason
 * about: an import either fully succeeds, or fails and persists no movies at all.
 *
 * <p>Reference data is the deliberate exception to this: finding or creating the
 * director/actor/genre reference data is delegated to {@link ReferenceDataResolver},
 * which persists each one in its own independent transaction, so it is kept even if
 * this movie transaction later rolls back. See that class for why: concurrently
 * running import tasks can race to create the same new director/actor/genre, and the
 * reference data itself is harmless and reusable even if the movie referencing it
 * never ends up persisted.
 *
 * <p>A single import inserting several movies in one transaction opens a second,
 * different kind of race than the single-movie duplicate race above: if two
 * concurrently-running imports both persist the same two (or more) movies, but in a
 * different order — e.g. file A lists [X, Y] while file B lists [Y, X] — each import's
 * database transaction can end up holding an in-progress insert lock on one of the
 * movies while waiting to acquire the lock the other transaction already holds on the
 * other movie, a classic lock-ordering ("ABBA") deadlock. PostgreSQL always detects
 * this and safely aborts one of the two transactions rather than hanging forever, so
 * this never corrupts data or violates the all-or-nothing guarantee above — but it
 * would mean one of the two imports fails for a reason that has nothing to do with an
 * actual duplicate movie, just unlucky timing. {@link #write} avoids this entirely (no
 * database-level deadlock detection ever needed) by always inserting a file's movies in
 * one fixed, deterministic order — sorted by the same natural key (title, director,
 * release year) used to detect duplicates — so that any two concurrent imports sharing
 * some of the same movies always attempt to lock them in the same relative order.
 *
 * <p><b>Known, accepted limitation:</b> a single large, still-uncommitted import can
 * cause a concurrently-running import that shares one of its movies to fail entirely,
 * even though that movie genuinely is a duplicate. This happens because the duplicate
 * check only sees other imports' <em>committed</em> movies (PostgreSQL's read-committed
 * isolation never shows one transaction another's uncommitted writes): if import A has
 * already inserted a movie but not yet committed (e.g. because it is still working
 * through hundreds of other movies) when import B checks for that same movie, B does
 * not find it, attempts to insert it itself, and briefly blocks on the database's
 * unique index until A finishes. If A then commits, B's blocked insert surfaces as a
 * real constraint violation and rolls back B's whole transaction — discarding every
 * other, genuinely new movie in B's file too. Avoiding this would require either
 * locking or reverting to per-movie transactions, both deliberately rejected in favor
 * of this class's simpler, purely fail-fast design; the failure is transient and
 * self-resolving in practice, since re-submitting the same file afterward always
 * succeeds (A will have committed or rolled back by then, so the duplicate check
 * correctly sees its outcome).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ImportWriter {

    /**
     * Orders movies by their natural key (title, director's external ID, release
     * year), so that any two imports sharing some of the same movies always attempt
     * to persist them in the same relative order — see the class Javadoc for why this
     * ordering is what prevents lock-ordering deadlocks between concurrent imports.
     */
    private static final Comparator<Movie> NATURAL_ORDER = Comparator
            .comparing(Movie::getTitle)
            .thenComparing(Movie::getDirector)
            .thenComparing(Movie::getReleaseYear);

    private final MovieRepository movies;
    private final MovieXmlMapper movieMapper;
    private final ReferenceDataResolver referenceData;

    /**
     * Persists every movie in {@code moviesXml} that is not a duplicate (same title,
     * director, and release year as an existing movie), creating any missing directors,
     * actors, and genres along the way, all within a single transaction.
     *
     * @param task the import task the persisted movies belong to
     * @param moviesXml the parsed movies to persist
     * @return how many movies were newly persisted (excluding duplicates)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int write(final ImportTask task, final MoviesXml moviesXml) {
        final int recordCount = moviesXml.movies().stream()
                .map(movieXml -> toMovie(task, movieXml))
                .sorted(NATURAL_ORDER)
                .mapToInt(this::persistIfNew)
                .sum();
        log.debug("Import task {}: persisted {} movie(s)", task.getId(), recordCount);
        return recordCount;
    }

    /**
     * Builds the {@link Movie} domain object for {@code movieXml}, resolving its
     * director and attaching its genres and actors, creating any missing reference
     * data along the way.
     *
     * @param task the import task the movie belongs to
     * @param movieXml the parsed movie
     * @return the fully-built, not-yet-persisted movie
     */
    private Movie toMovie(final ImportTask task, final MovieXml movieXml) {
        final Director director = referenceData.findOrCreateDirector(movieXml.getDirector());
        final Movie movie = movieMapper.toDomain(movieXml, task, director);
        movieXml.genres().stream()
                .map(referenceData::findOrCreateGenre)
                .forEach(movie::addGenre);
        movieXml.actors().stream()
                .map(referenceData::findOrCreateActor)
                .forEach(movie::addActor);
        return movie;
    }

    /**
     * Persists {@code movie}, unless a movie with the same title, director, and
     * release year already exists, in which case it is skipped as a duplicate.
     *
     * @param movie the fully-built movie to persist
     * @return {@code 1} if the movie was persisted, {@code 0} if it was a duplicate
     */
    private int persistIfNew(final Movie movie) {
        final String title = movie.getTitle();
        final Director director = movie.getDirector();
        final Integer releaseYear = movie.getReleaseYear();
        if (movies.existsByTitleAndDirectorAndReleaseYear(title, director, releaseYear)) {
            log.debug("Skipping duplicate movie \"{}\" ({}) by director {}",
                    title, releaseYear, director.getExternalId());
            return 0;
        }
        movies.save(movie);
        return 1;
    }
}
