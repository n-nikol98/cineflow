package com.nedko.cineflow.mapper;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.Actor;
import com.nedko.cineflow.dto.ActorDto;

@Component
public class ActorMapper {

    public ActorDto toDto(final Actor actor) {
        return new ActorDto(actor.getExternalId(), actor.getName());
    }
}
