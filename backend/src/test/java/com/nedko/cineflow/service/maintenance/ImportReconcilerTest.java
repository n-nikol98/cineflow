package com.nedko.cineflow.service.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nedko.cineflow.config.AppProperties;
import com.nedko.cineflow.domain.ImportTask.Status;
import com.nedko.cineflow.repository.ImportTaskRepository;

/**
 * Verifies {@link ImportReconciler} derives its cutoff from
 * {@code app.import.stale-after} and only reaches into
 * {@link ImportTaskRepository#failStale} for the two non-terminal statuses.
 */
@ExtendWith(MockitoExtension.class)
class ImportReconcilerTest {

    private static final int IMPORT_CONCURRENCY = 8;
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(5);
    private static final int FAILED_TASK_COUNT = 2;

    @Mock
    private ImportTaskRepository tasks;

    @Captor
    private ArgumentCaptor<Collection<Status>> statusesCaptor;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @Test
    void reconcileFailsQueuedAndProcessingTasksOlderThanStaleAfter() {
        final AppProperties properties = new AppProperties(null,
                new AppProperties.Import(IMPORT_CONCURRENCY, STALE_AFTER, CHECK_INTERVAL), null);
        when(tasks.failStale(anyCollection(), any(), anyString(), any())).thenReturn(FAILED_TASK_COUNT);

        final Instant before = Instant.now().minus(STALE_AFTER);
        new ImportReconciler(tasks, properties).reconcile();
        final Instant after = Instant.now().minus(STALE_AFTER);

        verify(tasks).failStale(statusesCaptor.capture(), cutoffCaptor.capture(), anyString(), any());

        assertEquals(EnumSet.of(Status.QUEUED, Status.PROCESSING), statusesCaptor.getValue());
        final Instant cutoff = cutoffCaptor.getValue();
        assertTrue(cutoff.compareTo(before) >= 0 && cutoff.compareTo(after) <= 0);
    }
}
