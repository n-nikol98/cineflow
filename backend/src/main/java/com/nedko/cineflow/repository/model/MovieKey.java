package com.nedko.cineflow.repository.model;

import com.nedko.cineflow.repository.MovieRepository;

/**
 * The fields that identify a movie without knowing its database id: title, director's
 * external id, and release year — the repository/persistence-layer counterpart of
 * {@link com.nedko.cineflow.dto.MovieKeyDto}.
 *
 * <p>{@link com.nedko.cineflow.dto.MovieKeyDto} is a web-layer type: it carries Bean
 * Validation annotations meant for validating an incoming HTTP request, so
 * {@link MovieRepository} (and the persistence layer generally) should not depend on
 * it directly. {@link com.nedko.cineflow.service.MovieService#findByKey} maps a
 * validated {@link com.nedko.cineflow.dto.MovieKeyDto} onto this plain record before calling
 * {@link MovieRepository#findByKey}, the same way {@link com.nedko.cineflow.mapper.MovieKeyMapper}
 * converts between {@link com.nedko.cineflow.domain.Movie} and 
 * {@link com.nedko.cineflow.dto.MovieKeyDto}.
 *
 * @param title the movie's title
 * @param directorId the movie's director's external id, i.e.
 *         {@link com.nedko.cineflow.domain.Director#getExternalId}
 *         - not {@link com.nedko.cineflow.domain.Director#getId}
 * @param releaseYear the movie's release year
 */
public record MovieKey(String title, String directorId, Integer releaseYear) {

    @Override
    public String toString() {
        return String.join("/", title, directorId, String.valueOf(releaseYear));
    }
}
