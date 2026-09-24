package com.nedko.cineflow.mapper;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.dto.DirectorDto;

@Component
public class DirectorMapper {

    public DirectorDto toDto(final Director director) {
        return new DirectorDto(director.getExternalId(), director.getName());
    }
}
