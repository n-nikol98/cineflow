package com.nedko.cineflow.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movie",
        uniqueConstraints = @UniqueConstraint(columnNames = 
            {"title", "director_id", "release_year"}))
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_task_id", nullable = false)
    private ImportTask importTask;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "director_id", nullable = false)
    private Director director;

    @Column(name = "release_year", nullable = false)
    private Integer releaseYear;

    private Integer durationMinutes;

    @Column(length = 4000)
    private String description;

    @ManyToMany
    @JoinTable(
            name = "movie_actor",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id"))
    private Set<Actor> actors = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "movie_genre",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_name"))
    private Set<Genre> genres = new HashSet<>();

    @OneToOne(mappedBy = "movie", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private MovieDelivery delivery;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Movie(final ImportTask importTask, final String title,
            final Director director, final Integer releaseYear) {
        this.importTask = importTask;
        this.title = title;
        this.director = director;
        this.releaseYear = releaseYear;
        this.delivery = new MovieDelivery(this);
    }

    public void addActor(final Actor actor) {
        actors.add(actor);
    }

    public void addGenre(final Genre genre) {
        genres.add(genre);
    }

    public MovieDelivery getOrCreateDelivery() {
        if (delivery == null) {
            delivery = new MovieDelivery(this);
        }
        return delivery;
    }
}
