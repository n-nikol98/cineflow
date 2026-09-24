package com.nedko.cineflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nedko.cineflow.domain.Director;

public interface DirectorRepository extends JpaRepository<Director, Long> {

    Optional<Director> findByExternalId(final String externalId);
}
