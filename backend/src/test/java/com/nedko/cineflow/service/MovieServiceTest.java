package com.nedko.cineflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.dto.MovieDto;
import com.nedko.cineflow.dto.MovieKeyDto;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.mapper.ActorMapper;
import com.nedko.cineflow.mapper.DirectorMapper;
import com.nedko.cineflow.mapper.MovieKeyMapper;
import com.nedko.cineflow.mapper.MovieMapper;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.repository.model.MovieKey;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    private static final Long MOVIE_ID = 1L;
    private static final Long SECOND_MOVIE_ID = 2L;
    private static final Long THIRD_MOVIE_ID = 3L;
    private static final Long MISSING_MOVIE_ID = 99L;
    private static final String MOVIE_ENTITY_NAME = "Movie";
    private static final String MOVIE_TITLE = "Example";
    private static final String SECOND_MOVIE_TITLE = "Second Movie";
    private static final String THIRD_MOVIE_TITLE = "Third Movie";
    private static final int RELEASE_YEAR = 2020;
    private static final String DIRECTOR_ID = "director-1";
    private static final String DIRECTOR_NAME = "Director";
    private static final int PAGE_NUMBER = 0;
    private static final int PAGE_SIZE = 3;
    private static final int TOTAL_ELEMENTS = 6;

    @Mock
    private MovieRepository repository;

    @Spy
    private MovieMapper mapper = new MovieMapper(new ActorMapper(), new DirectorMapper());

    @Spy
    private MovieKeyMapper keyMapper = new MovieKeyMapper();

    @InjectMocks
    private MovieService service;

    @Test
    void findMapsTheMovieLoadedById() {
        final Movie movie = movie(MOVIE_ID, MOVIE_TITLE);
        when(repository.findByIdOrThrow(MOVIE_ID)).thenReturn(movie);

        final MovieDto result = service.find(MOVIE_ID);

        assertEquals(mapper.toDto(movie), result);
        verify(repository).findByIdOrThrow(MOVIE_ID);
    }

    @Test
    void findThrowsNotFoundExceptionWhenMovieIsMissing() {
        when(repository.findByIdOrThrow(MISSING_MOVIE_ID))
                .thenThrow(NotFoundException.forEntity(MOVIE_ENTITY_NAME, MISSING_MOVIE_ID));

        assertThrows(NotFoundException.class, () -> service.find(MISSING_MOVIE_ID));

        verifyNoInteractions(mapper);
    }

    @Test
    void findAllMapsEveryMovieInTheRequestedPage() {
        final List<Movie> movies = List.of(movie(MOVIE_ID, MOVIE_TITLE),
                movie(SECOND_MOVIE_ID, SECOND_MOVIE_TITLE),
                movie(THIRD_MOVIE_ID, THIRD_MOVIE_TITLE));
        final PageRequest pageable = PageRequest.of(PAGE_NUMBER, PAGE_SIZE);
        when(repository.findAll(pageable))
                .thenReturn(new PageImpl<>(movies, pageable, TOTAL_ELEMENTS));

        final Page<MovieDto> result = service.findAll(pageable);

        assertEquals(movies.stream().map(mapper::toDto).toList(), result.getContent());
        assertEquals(TOTAL_ELEMENTS, result.getTotalElements());
        verify(repository).findAll(pageable);
    }

    @Test
    void findByKeyMapsTheMovieMatchingTitleDirectorAndReleaseYear() {
        final Movie movie = movie(MOVIE_ID, MOVIE_TITLE);
        final MovieKeyDto key = new MovieKeyDto(MOVIE_TITLE, DIRECTOR_ID, RELEASE_YEAR);
        final MovieKey movieKey = new MovieKey(MOVIE_TITLE, DIRECTOR_ID, RELEASE_YEAR);
        when(repository.findByKeyOrThrow(movieKey)).thenReturn(movie);

        final MovieDto result = service.findByKey(key);

        assertEquals(mapper.toDto(movie), result);
        verify(repository).findByKeyOrThrow(movieKey);
    }

    @Test
    void findByKeyThrowsNotFoundExceptionWhenNoMovieMatches() {
        final MovieKeyDto key = new MovieKeyDto(MOVIE_TITLE, DIRECTOR_ID, RELEASE_YEAR);
        final MovieKey movieKey = new MovieKey(MOVIE_TITLE, DIRECTOR_ID, RELEASE_YEAR);
        when(repository.findByKeyOrThrow(movieKey))
                .thenThrow(NotFoundException.forEntity(MOVIE_ENTITY_NAME, movieKey));

        assertThrows(NotFoundException.class, () -> service.findByKey(key));

        verifyNoInteractions(mapper);
    }

    private static Movie movie(final Long id, final String title) {
        final Movie movie = new Movie(new ImportTask(), title,
                new Director(DIRECTOR_ID, DIRECTOR_NAME), RELEASE_YEAR);
        movie.setId(id);
        return movie;
    }
}
