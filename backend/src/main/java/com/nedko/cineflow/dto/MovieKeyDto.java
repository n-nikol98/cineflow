package com.nedko.cineflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The web-layer request shape for looking up a movie by its natural key (title,
 * director's external id, and release year) instead of its database id.
 * {@link com.nedko.cineflow.controller.MovieController#lookup} binds request query
 * parameters directly onto this record's components and validates them; if the natural
 * key ever needs another field (or one fewer), this is the one place its query
 * parameters are defined.
 *
 * <p>{@code directorId} deliberately matches the {@code director.id} field seen in
 * {@link MovieDto}/{@link DirectorDto} responses (not {@code Director}'s internal
 * database id, which the API never exposes) — see this record's constructor param
 * Javadoc.
 *
 * <p>All three components are required: {@code @Valid} on {@code MovieController#lookup}
 * rejects a request missing any of them, or supplying a blank title/directorId or a
 * non-numeric releaseYear, with a {@code 400 Bad Request} rather than silently binding
 * a {@code null} and later returning a misleading "not found".
 *
 * <p>{@link com.nedko.cineflow.service.MovieService#findByKey} maps this onto
 * {@link com.nedko.cineflow.repository.model.MovieKey}, the persistence-layer counterpart
 * that {@link com.nedko.cineflow.repository.MovieRepository} actually queries with —
 * see that record's Javadoc for why the persistence layer doesn't use this
 * validation-annotated, web-facing type directly.
 *
 * @param title the movie's title
 * @param directorId the movie's director's id, as returned in {@code director.id} on
 *         a {@link MovieDto} (the {@code id} attribute on {@code <director>} in the
 *         import XML, i.e. {@code Director.externalId} - not {@code Director}'s
 *         internal database id)
 * @param releaseYear the movie's release year
 */
public record MovieKeyDto(
        @NotBlank String title,
        @NotBlank String directorId,
        @NotNull Integer releaseYear) {
}
