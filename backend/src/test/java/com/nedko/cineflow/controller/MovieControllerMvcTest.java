package com.nedko.cineflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nedko.cineflow.dto.MovieDto;
import com.nedko.cineflow.dto.MovieKeyDto;
import com.nedko.cineflow.exception.handler.GlobalExceptionHandler;
import com.nedko.cineflow.service.MovieService;

class MovieControllerMvcTest {

    private static final String MOVIES_PATH = "/movies";
    private static final String LOOKUP_PATH = MOVIES_PATH + "/lookup";
    private static final long MOVIE_ID = 3L;
    private static final String MOVIE_TITLE = "Example";
    private static final int RELEASE_YEAR = 2020;
    private static final int DURATION_MINUTES = 100;
    private static final String DIRECTOR_ID = "director-1";
    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";

    private MovieService movies;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        movies = Mockito.mock(MovieService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MovieController(movies))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void listReturnsRequestedMoviePage() throws Exception {
        final List<MovieDto> allMovies = fiveMovies();
        final int pageSize = 2;
        final List<MovieDto> secondPage = allMovies.subList(pageSize, pageSize * 2);
        when(movies.findAll(any())).thenReturn(
                new PageImpl<>(secondPage, PageRequest.of(1, pageSize), allMovies.size()));

        mockMvc.perform(get(MOVIES_PATH).param("page", "1").param("size", String.valueOf(pageSize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(pageSize))
                .andExpect(jsonPath("$.content[0].id").value(secondPage.get(0).id()))
                .andExpect(jsonPath("$.content[1].id").value(secondPage.get(1).id()))
                .andExpect(jsonPath("$.totalElements").value(allMovies.size()))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listWithoutPaginationParamsReturnsAllMoviesOnADefaultPage() throws Exception {
        final List<MovieDto> allMovies = fiveMovies();
        when(movies.findAll(any())).thenReturn(
                new PageImpl<>(allMovies, PageRequest.of(0, 20), allMovies.size()));

        mockMvc.perform(get(MOVIES_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(allMovies.size()))
                .andExpect(jsonPath("$.content[0].id").value(allMovies.get(0).id()))
                .andExpect(jsonPath("$.content[4].id").value(allMovies.get(4).id()));
    }

    @Test
    void getReturnsMovieById() throws Exception {
        when(movies.find(MOVIE_ID)).thenReturn(exampleMovie());

        mockMvc.perform(get(MOVIES_PATH + "/" + MOVIE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MOVIE_ID));

        verify(movies).find(MOVIE_ID);
    }

    @Test
    void lookupReturnsMovieMatchingNaturalKey() throws Exception {
        final MovieKeyDto key = new MovieKeyDto(MOVIE_TITLE, DIRECTOR_ID, RELEASE_YEAR);
        when(movies.findByKey(key)).thenReturn(exampleMovie());

        mockMvc.perform(get(LOOKUP_PATH)
                .param("title", MOVIE_TITLE)
                .param("directorId", DIRECTOR_ID)
                .param("releaseYear", String.valueOf(RELEASE_YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MOVIE_ID));

        verify(movies).findByKey(key);
    }

    @Test
    void lookupWithMissingRequiredParameterReturnsBadRequestInsteadOfLookingUpNulls() throws Exception {
        mockMvc.perform(get(LOOKUP_PATH)
                .param("title", MOVIE_TITLE)
                .param("directorId", DIRECTOR_ID))
                // releaseYear intentionally omitted
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST_CODE));

        verifyNoInteractions(movies);
    }

    @Test
    void lookupWithBlankTitleReturnsBadRequest() throws Exception {
        mockMvc.perform(get(LOOKUP_PATH)
                .param("title", "")
                .param("directorId", DIRECTOR_ID)
                .param("releaseYear", String.valueOf(RELEASE_YEAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST_CODE));

        verifyNoInteractions(movies);
    }

    @Test
    void lookupWithNonNumericReleaseYearReturnsBadRequestInsteadOfServerError() throws Exception {
        mockMvc.perform(get(LOOKUP_PATH)
                .param("title", MOVIE_TITLE)
                .param("directorId", DIRECTOR_ID)
                .param("releaseYear", "not-a-year"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST_CODE));

        verifyNoInteractions(movies);
    }

    private static MovieDto exampleMovie() {
        return new MovieDto(MOVIE_ID, MOVIE_TITLE, RELEASE_YEAR,
                DURATION_MINUTES, List.of(), null, "", List.of());
    }

    private static List<MovieDto> fiveMovies() {
        return List.of(
                movie(1L, "First Movie"),
                movie(2L, "Second Movie"),
                exampleMovie(),
                movie(4L, "Fourth Movie"),
                movie(5L, "Fifth Movie"));
    }

    private static MovieDto movie(long id, String title) {
        return new MovieDto(id, title, RELEASE_YEAR, DURATION_MINUTES, List.of(), null, "", List.of());
    }
}
