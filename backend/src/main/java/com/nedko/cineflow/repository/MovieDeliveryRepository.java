package com.nedko.cineflow.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nedko.cineflow.domain.MovieDelivery;

public interface MovieDeliveryRepository extends JpaRepository<MovieDelivery, Long> {

    Optional<MovieDelivery> findByMovieId(final Long movieId);

    Page<MovieDelivery> findByStatus(final MovieDelivery.Status status, final Pageable pageable);
}
