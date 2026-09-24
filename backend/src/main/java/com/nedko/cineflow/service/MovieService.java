package com.nedko.cineflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.dto.MovieDto;
import com.nedko.cineflow.dto.MovieKeyDto;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.mapper.MovieKeyMapper;
import com.nedko.cineflow.mapper.MovieMapper;
import com.nedko.cineflow.repository.MovieRepository;
import com.nedko.cineflow.repository.model.MovieKey;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository repository;
    private final MovieMapper mapper;
    private final MovieKeyMapper keyMapper;

    public Page<MovieDto> findAll(final Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    public MovieDto find(final Long id) {
        final Movie movie = repository.findByIdOrThrow(id);
        return mapper.toDto(movie);
    }

    /**
     * Looks up a movie by its natural key (title, director's external id, and release
     * year) instead of its database id — see
     * {@link MovieRepository#findByKey} for why this is useful.
     *
     * <p>{@code key} is the validated, web-layer {@link MovieKeyDto}; {@link MovieKeyMapper}
     * maps it onto the plain {@link MovieKey} the persistence layer works with, the same
     * way {@link #find} works with a domain {@link Movie} and only maps to
     * {@link MovieDto} at the very end - see {@link MovieKey}'s Javadoc for why the
     * persistence layer doesn't use {@link MovieKeyDto} directly.
     *
     * @param key the natural key to look up
     * @return the matching movie
     * @throws NotFoundException if no movie matches that natural key
     */
    public MovieDto findByKey(final MovieKeyDto key) {
        final Movie movie = repository.findByKeyOrThrow(keyMapper.toKey(key));
        return mapper.toDto(movie);
    }
}
