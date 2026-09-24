package com.nedko.cineflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nedko.cineflow.domain.Actor;

public interface ActorRepository extends JpaRepository<Actor, Long> {

    Optional<Actor> findByExternalId(final String externalId);
}
