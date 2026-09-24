package com.nedko.cineflow.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.nedko.cineflow.xml.adapter.WhitespaceAdapter;

@Getter
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class ActorXml {

    @XmlAttribute(name = "id", required = true)
    @XmlJavaTypeAdapter(WhitespaceAdapter.class)
    private String id;

    @XmlValue
    @XmlJavaTypeAdapter(WhitespaceAdapter.class)
    private String name;
}
