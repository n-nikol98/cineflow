package com.nedko.cineflow.mapper.xml;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.xml.DirectorXml;

@Component
public class DirectorXmlMapper {

    public Director toDomain(final DirectorXml directorXml) {
        return new Director(directorXml.getId(), directorXml.getName());
    }
}
