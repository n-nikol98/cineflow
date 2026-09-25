package com.nedko.cineflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "director")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Director implements Comparable<Director> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    public Director(final String externalId, final String name) {
        this.externalId = externalId;
        this.name = name;
    }

    /**
     * Orders directors by their {@link #externalId}, which is unique, so this is a
     * stable, deterministic ordering usable to sort movies into a fixed persistence
     * order (see {@code ImportWriter}).
     */
    @Override
    public int compareTo(final Director other) {
        return externalId.compareTo(other.externalId);
    }
}
