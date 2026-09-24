package com.nedko.cineflow.mapper.xml;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.Actor;
import com.nedko.cineflow.xml.ActorXml;

@Component
public class ActorXmlMapper {

    public Actor toDomain(final ActorXml actorXml) {
        return new Actor(actorXml.getId(), actorXml.getName());
    }
}
