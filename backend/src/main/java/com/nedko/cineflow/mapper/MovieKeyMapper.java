package com.nedko.cineflow.mapper;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.dto.MovieKeyDto;
import com.nedko.cineflow.repository.model.MovieKey;

/**
 * Converts the validated, web-layer {@link MovieKeyDto} onto the plain
 * {@link MovieKey} the persistence layer works with - see {@link MovieKey}'s Javadoc
 * for why {@link com.nedko.cineflow.repository.MovieRepository} doesn't use
 * {@link MovieKeyDto} directly.
 */
@Component
public class MovieKeyMapper {

    public MovieKey toKey(final MovieKeyDto key) {
        return new MovieKey(key.title(), key.directorId(), key.releaseYear());
    }
}
