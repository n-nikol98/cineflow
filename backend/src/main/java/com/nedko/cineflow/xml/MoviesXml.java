package com.nedko.cineflow.xml;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@XmlRootElement(name = "movies")
@XmlAccessorType(XmlAccessType.FIELD)
public class MoviesXml {

    @XmlElement(name = "movie")
    private List<MovieXml> movies = new ArrayList<>();

    public List<MovieXml> movies() {
        return List.copyOf(movies);
    }
}
