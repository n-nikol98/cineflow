package com.nedko.cineflow.service.outbound.model;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.dto.MovieDto;

/**
 * A {@link Movie} paired with its already-mapped {@link MovieDto}, produced by
 * {@code DeliveryLoader} while its lazy associations (genres, actors, director) are
 * still reachable. {@code movie} is kept alongside {@code payload} because
 * {@code DeliveryClient} still needs it for its identifier and {@code delivery}
 * association.
 */
public record DeliveryCandidate(Movie movie, MovieDto payload) {
}
