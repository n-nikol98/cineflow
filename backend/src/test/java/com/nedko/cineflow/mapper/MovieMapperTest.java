package com.nedko.cineflow.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nedko.cineflow.domain.Actor;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.dto.ActorDto;
import com.nedko.cineflow.dto.MovieDto;

class MovieMapperTest {

    private static final String MOVIE_TITLE = "Example";
    private static final int RELEASE_YEAR = 2020;
    private static final String DIRECTOR_ID = "nm-dir";
    private static final String DIRECTOR_NAME = "Director";
    private static final String FIRST_ACTOR_ID = "nm1";
    private static final String SECOND_ACTOR_ID = "nm2";

    private final MovieMapper mapper = new MovieMapper(new ActorMapper(), new DirectorMapper());

    @Test
    void mapsMovieWithActorsOrderedByExternalId() {
        final Movie movie = new Movie(
                new ImportTask(), MOVIE_TITLE, new Director(DIRECTOR_ID, DIRECTOR_NAME), RELEASE_YEAR);
        movie.addActor(new Actor(SECOND_ACTOR_ID, "Second"));
        movie.addActor(new Actor(FIRST_ACTOR_ID, "First"));

        final MovieDto dto = mapper.toDto(movie);

        assertNull(dto.id());
        assertEquals(DIRECTOR_ID, dto.director().id());
        assertEquals(List.of(FIRST_ACTOR_ID, SECOND_ACTOR_ID),
                dto.actors().stream().map(ActorDto::id).toList());
    }
}
