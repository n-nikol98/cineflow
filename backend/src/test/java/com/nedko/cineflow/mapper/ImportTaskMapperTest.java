package com.nedko.cineflow.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.dto.ImportTaskDto;

class ImportTaskMapperTest {

    private static final String FILE_NAME = "movies.xml";
    private static final int RECORD_COUNT = 3;

    private final ImportTaskMapper mapper = new ImportTaskMapper();

    @Test
    void mapsCompletedTaskToDto() {
        final ImportTask task = new ImportTask();
        task.setFileName(FILE_NAME);
        task.setStatus(ImportTask.Status.COMPLETED);
        task.setRecordCount(RECORD_COUNT);

        final ImportTaskDto dto = mapper.toDto(task);

        assertEquals(FILE_NAME, dto.fileName());
        assertEquals(ImportTask.Status.COMPLETED, dto.status());
        assertEquals(RECORD_COUNT, dto.recordCount());
        assertEquals("", dto.error());
    }
}
