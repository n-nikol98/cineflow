package com.nedko.cineflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.nedko.cineflow.domain.ImportTask.Status;

class ImportTaskTest {

    @Test
    void newTaskDefaultsToQueuedStatus() {
        assertEquals(Status.QUEUED, new ImportTask().getStatus());
    }
}
