package com.nedko.cineflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nedko.cineflow.dto.MovieDto;
import com.nedko.cineflow.dto.MovieKeyDto;
import com.nedko.cineflow.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService service;

    @GetMapping
    public Page<MovieDto> list(final Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public MovieDto get(@PathVariable final Long id) {
        return service.find(id);
    }

    /**
     * Looks up a movie by its natural key (title, director id, and release year)
     * rather than its database id — useful for resolving the natural key mentioned in
     * a failed/duplicate import's error message (or any other natural-key reference)
     * to a movie id, e.g. to then look up its delivery status, without paging through
     * every movie by hand.
     *
     * <p>{@code key}'s query parameters are bound directly onto {@link MovieKeyDto}
     * by name, so this method's signature never needs to change if the natural key's
     * fields do — see that record's Javadoc. {@code @Valid} rejects a request missing
     * any of the three fields, or with a non-numeric {@code releaseYear}, with a
     * {@code 400 Bad Request} (see {@code GlobalExceptionHandler}) instead of letting
     * it silently bind {@code null}s and return a misleading "not found".
     *
     * @param key the natural key to look up
     * @return the matching movie
     */
    @GetMapping("/lookup")
    public MovieDto lookup(@Valid @ModelAttribute final MovieKeyDto key) {
        return service.findByKey(key);
    }
}
