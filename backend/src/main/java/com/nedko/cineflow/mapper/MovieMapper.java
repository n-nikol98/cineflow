package com.nedko.cineflow.mapper;

import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.Genre;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.dto.ActorDto;
import com.nedko.cineflow.dto.MovieDto;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MovieMapper {

    private final ActorMapper actorMapper;
    private final DirectorMapper directorMapper;

    public MovieDto toDto(final Movie movie) {
        return new MovieDto(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getDurationMinutes(),
                movie.getGenres().stream()
                        .map(Genre::getName)
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList()),
                directorMapper.toDto(movie.getDirector()),
                movie.getDescription(),
                movie.getActors().stream()
                        .map(actorMapper::toDto)
                        .sorted(Comparator.comparing(ActorDto::id))
                        .collect(Collectors.toList()));
    }
}
