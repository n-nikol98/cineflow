package com.nedko.cineflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nedko.cineflow.domain.Genre;

public interface GenreRepository extends JpaRepository<Genre, String> {

    default Optional<Genre> findByName(final String name) {
        return findById(name);
    }
}
