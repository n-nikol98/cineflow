package com.nedko.cineflow.service.inbound.reference;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.nedko.cineflow.domain.Actor;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.Genre;
import com.nedko.cineflow.repository.ActorRepository;
import com.nedko.cineflow.repository.DirectorRepository;
import com.nedko.cineflow.repository.GenreRepository;
import com.nedko.cineflow.xml.ActorXml;
import com.nedko.cineflow.xml.DirectorXml;

/**
 * Resolves the director, actor, and genre reference data of a parsed movie, creating
 * any of it that does not already exist.
 *
 * <p>Import tasks run concurrently (each on its own {@code importExecutor} thread, each
 * in its own transaction via {@code ImportWriter#write}), so two tasks can race to
 * create the same new director, actor, or genre at the same time: both find nothing on
 * lookup, both then try to insert, and the second insert violates a unique constraint.
 *
 * <p>On PostgreSQL (and most relational databases), a failed statement leaves the
 * whole surrounding transaction unusable until it is rolled back — so simply catching
 * the constraint violation and retrying within {@code ImportWriter#write}'s own
 * transaction would not work; every later statement in that transaction would fail too.
 * Instead, {@link ReferenceDataCreator} runs each creation attempt in its own new,
 * independent transaction: if it loses the race, only that isolated transaction is
 * rolled back, and the caller's transaction here is unaffected, so we can simply
 * re-query and reuse the row the winning task just committed.
 *
 * <p>Public: this is the entry point into this package, called from
 * {@code com.nedko.cineflow.service.inbound.ImportWriter}.
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataResolver {

    private final ActorRepository actors;
    private final DirectorRepository directors;
    private final GenreRepository genres;
    private final ReferenceDataCreator creator;

    /**
     * Looks up the director matching {@code directorXml}'s external id, or creates and
     * persists a new one if none exists yet.
     *
     * @param directorXml the parsed director data
     * @return the existing or newly created director
     */
    public Director findOrCreateDirector(final DirectorXml directorXml) {
        final String externalId = directorXml.getId();
        return findOrCreate(() -> directors.findByExternalId(externalId),
                () -> creator.createDirector(directorXml));
    }

    /**
     * Looks up the genre matching {@code genreName}, or creates and persists a new one
     * if none exists yet.
     *
     * @param genreName the genre's name
     * @return the existing or newly created genre
     */
    public Genre findOrCreateGenre(final String genreName) {
        return findOrCreate(() -> genres.findByName(genreName),
                () -> creator.createGenre(genreName));
    }

    /**
     * Looks up the actor matching {@code actorXml}'s external id, or creates and
     * persists a new one if none exists yet.
     *
     * @param actorXml the parsed actor data
     * @return the existing or newly created actor
     */
    public Actor findOrCreateActor(final ActorXml actorXml) {
        final String externalId = actorXml.getId();
        return findOrCreate(() -> actors.findByExternalId(externalId),
                () -> creator.createActor(actorXml));
    }

    /**
     * Looks up an entity via {@code lookup}, or creates it via {@code create} if none
     * exists yet.
     *
     * <p>If {@code create} loses the race to a concurrently-running import task (see
     * class Javadoc), it fails with {@link DataIntegrityViolationException}; in that
     * case we simply re-run {@code lookup} and return the row the other task just
     * committed, rather than propagating the failure.
     *
     * @param lookup finds the entity by its natural key, if it already exists
     * @param create creates and persists the entity, in its own isolated transaction
     * @return the existing or newly created entity
     */
    private <T> T findOrCreate(final Supplier<Optional<T>> lookup, final Supplier<T> create) {
        return lookup.get().orElseGet(() -> {
            try {
                return create.get();
            } catch (final DataIntegrityViolationException exception) {
                return lookup.get().orElseThrow(() -> exception);
            }
        });
    }
}
