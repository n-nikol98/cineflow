package com.nedko.cineflow.mapper;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.dto.ImportTaskDto;

@Component
public class ImportTaskMapper {

    public ImportTaskDto toDto(final ImportTask task) {
        return new ImportTaskDto(
                task.getId(),
                task.getFileName(),
                task.getStatus(),
                task.getRecordCount(),
                Optional.ofNullable(task.getErrorMessage()).orElse(""));
    }
}
