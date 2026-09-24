package com.nedko.cineflow.xml;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.nedko.cineflow.xml.adapter.WhitespaceAdapter;

@Getter
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class MovieXml {

    @XmlElement(required = true)
    @XmlJavaTypeAdapter(WhitespaceAdapter.class)
    private String title;

    @XmlElement(required = true)
    private Integer releaseYear;

    private Integer durationMinutes;

    @XmlElementWrapper(name = "genres")
    @XmlElement(name = "genre")
    @XmlJavaTypeAdapter(WhitespaceAdapter.class)
    private List<String> genres = new ArrayList<>();

    @XmlElement(required = true)
    private DirectorXml director;

    @XmlElementWrapper(name = "actors")
    @XmlElement(name = "actor")
    private List<ActorXml> actors = new ArrayList<>();

    private String description;

    public List<ActorXml> actors() {
        return List.copyOf(actors);
    }

    public List<String> genres() {
        return List.copyOf(genres);
    }
}
