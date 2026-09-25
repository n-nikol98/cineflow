package com.nedko.cineflow.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movie_delivery")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovieDelivery {

    @Id
    @Column(name = "movie_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    private Instant lastAttemptAt;

    @Column(length = 4000)
    private String lastError;

    /**
     * Optimistic lock guarding against two concurrent updates silently overwriting
     * each other, e.g. a manual {@code DeliveryScheduler#retry} racing an in-flight
     * scheduled delivery attempt for the same movie: whichever of the two commits
     * second sees a stale version and fails with
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} instead
     * of silently reverting the other's change.
     *
     * <p>Deliberately kept as a primitive {@code long}, unlike this entity's other
     * numeric fields: {@code id} is assigned via {@link MapsId} rather than generated,
     * so Spring Data relies on this version field to tell new instances from existing
     * ones ({@code JpaMetamodelEntityInformation#isNew}). That check only inspects the
     * value when the version type is a primitive; boxing it to {@code Long} would
     * switch the check to "is null", which is never true here since Lombok's
     * {@code @Setter}/JPA always leave it at its default of {@code 0} for a brand-new,
     * unsaved delivery, causing new deliveries to be mistaken for existing ones and
     * merged instead of inserted.
     */
    @Version
    private long version;

    public MovieDelivery(final Movie movie) {
        this.movie = movie;
    }

    public void markAttempt(final Instant attemptedAt) {
        attempts++;
        lastAttemptAt = attemptedAt;
    }

    public void markSent() {
        status = Status.SENT;
        lastError = null;
    }

    public void markFailed(final String error) {
        status = Status.FAILED;
        lastError = error;
    }

    public void reset() {
        status = Status.PENDING;
        attempts = 0;
        lastAttemptAt = null;
        lastError = null;
    }

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }
}
