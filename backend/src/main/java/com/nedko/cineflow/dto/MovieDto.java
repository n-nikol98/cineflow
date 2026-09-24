package com.nedko.cineflow.dto;

import java.util.List;

public record MovieDto(
        Long id,
        String title,
        Integer releaseYear,
        Integer durationMinutes,
        List<String> genres,
        DirectorDto director,
        String description,
        List<ActorDto> actors) {
}
