package com.nedko.cineflow.mapper.xml;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.xml.MovieXml;

@Component
public class MovieXmlMapper {

    public Movie toDomain(final MovieXml movieXml, final ImportTask importTask,
        final Director director) {
        final Movie movie = new Movie(importTask, movieXml.getTitle(),
            director, movieXml.getReleaseYear());
        movie.setDurationMinutes(movieXml.getDurationMinutes());
        movie.setDescription(movieXml.getDescription());
        return movie;
    }
}
