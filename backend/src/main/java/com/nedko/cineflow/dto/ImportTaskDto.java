package com.nedko.cineflow.dto;

import com.nedko.cineflow.domain.ImportTask.Status;

public record ImportTaskDto(
        Long id,
        String fileName,
        Status status,
        int recordCount,
        String error) {
}
