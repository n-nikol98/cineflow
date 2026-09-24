package com.nedko.cineflow.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_task")
@Getter
@Setter
@NoArgsConstructor
public class ImportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    @Column(length = 4000)
    private String errorMessage;

    private int recordCount;

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
