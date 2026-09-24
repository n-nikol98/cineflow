package com.nedko.cineflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nedko.cineflow.dto.DeliveryStatusDto;
import com.nedko.cineflow.service.outbound.DeliveryScheduler;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoint for inspecting and manually retrying outbound movie deliveries.
 *
 * <p>Deliveries themselves are sent automatically by
 * {@link DeliveryScheduler#scheduledSend}; this controller only exposes read access to
 * their status and a way to reset a failed delivery back to {@code PENDING}.
 */
@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryScheduler scheduler;

    /**
     * Returns a page of movies whose delivery has permanently failed (retries exhausted).
     *
     * @param pageable the requested page and sort
     * @return a page of failed delivery statuses
     */
    @GetMapping("/failed")
    public Page<DeliveryStatusDto> failed(final Pageable pageable) {
        return scheduler.findFailed(pageable);
    }

    /**
     * Returns the current delivery status of a single movie.
     *
     * @param id the movie's identifier
     * @return the movie's current delivery status
     */
    @GetMapping("/{id}")
    public DeliveryStatusDto status(@PathVariable final Long id) {
        return scheduler.status(id);
    }

    /**
     * Resets a movie's delivery back to {@code PENDING} so the next scheduled run picks
     * it up again, regardless of its previous status or attempt count.
     *
     * @param id the movie's identifier
     */
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable final Long id) {
        scheduler.retry(id);
    }
}
