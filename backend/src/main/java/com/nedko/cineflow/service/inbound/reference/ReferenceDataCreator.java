package com.nedko.cineflow.service.inbound.reference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.nedko.cineflow.domain.Actor;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.Genre;
import com.nedko.cineflow.mapper.xml.ActorXmlMapper;
import com.nedko.cineflow.mapper.xml.DirectorXmlMapper;
import com.nedko.cineflow.repository.ActorRepository;
import com.nedko.cineflow.repository.DirectorRepository;
import com.nedko.cineflow.repository.GenreRepository;
import com.nedko.cineflow.xml.ActorXml;
import com.nedko.cineflow.xml.DirectorXml;

/**
 * Creates a single director, actor, or genre in its own new, independent transaction.
 *
 * <p>Extracted into its own bean (rather than methods on {@link ReferenceDataResolver})
 * so {@link Propagation#REQUIRES_NEW} actually takes effect: {@code @Transactional}
 * is applied by a Spring proxy around bean method calls, which a same-class
 * ("self-invocation") call would bypass entirely. Calling these methods through this
 * separate, injected bean instead goes through that proxy, so each creation attempt
 * genuinely runs in its own transaction, isolated from the caller's.
 *
 * <p>Package-private: only {@link ReferenceDataResolver}, in this same package, calls
 * it directly; everything outside this package goes through that resolver instead.
 *
 * @see ReferenceDataResolver for why that isolation matters
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
class ReferenceDataCreator {

    private final ActorRepository actors;
    private final DirectorRepository directors;
    private final GenreRepository genres;
    private final ActorXmlMapper actorMapper;
    private final DirectorXmlMapper directorMapper;

    /**
     * Persists a new actor mapped from {@code actorXml}.
     *
     * @param actorXml the parsed actor data
     * @return the newly created actor
     */
    Actor createActor(final ActorXml actorXml) {
        return actors.save(actorMapper.toDomain(actorXml));
    }

    /**
     * Persists a new director mapped from {@code directorXml}.
     *
     * @param directorXml the parsed director data
     * @return the newly created director
     */
    Director createDirector(final DirectorXml directorXml) {
        return directors.save(directorMapper.toDomain(directorXml));
    }

    /**
     * Persists a new genre with the given name.
     *
     * @param genreName the genre's name
     * @return the newly created genre
     */
    Genre createGenre(final String genreName) {
        return genres.save(new Genre(genreName));
    }
}
