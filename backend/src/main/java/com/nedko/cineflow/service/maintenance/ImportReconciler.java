package com.nedko.cineflow.service.maintenance;

import java.time.Instant;
import java.util.EnumSet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nedko.cineflow.config.AppProperties;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.ImportTask.Status;
import com.nedko.cineflow.repository.ImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically recovers import tasks left stuck in {@code QUEUED}/{@code PROCESSING}
 * because nothing is left running to ever finish them — typically because the
 * application restarted or crashed (e.g. an out-of-memory kill on a huge file) between
 * {@code ImportProcessor#process} marking a task {@code PROCESSING} and it reaching its
 * {@code finally} block, or because the task was never picked up by the async executor
 * at all before a restart.
 *
 * <p>A task is considered stale once it is older (by {@code createdAt}) than
 * {@code app.import.stale-after}, checked every {@code app.import.check-interval}. This
 * is a heuristic, not a guarantee: a genuinely still-running import of an unusually
 * large file older than the configured threshold would also be marked failed here,
 * even though {@code ImportProcessor} is still legitimately working on it — and since
 * {@link ImportTaskRepository#failStale} is a direct bulk update, {@code ImportProcessor}
 * has no way to know this happened and will simply overwrite the outcome again once it
 * finishes. Pick {@code stale-after} comfortably above how long a real import ever
 * takes to avoid this; the trade-off favors recovering genuinely orphaned tasks over
 * perfectly protecting unusually slow (but still healthy) ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ImportReconciler {

    private static final EnumSet<Status> IN_PROGRESS_STATUSES =
            EnumSet.of(ImportTask.Status.QUEUED, ImportTask.Status.PROCESSING);
    private static final String STALE_MESSAGE = "This import was abandoned, likely because the "
            + "application restarted or crashed while it was running. Please re-upload the file.";

    private final ImportTaskRepository tasks;
    private final AppProperties properties;

    @Scheduled(fixedDelayString = "${app.import.check-interval}")
    void reconcile() {
        final Instant now = Instant.now();
        final Instant cutoff = now.minus(properties.importProperties().staleAfter());
        final int failed = tasks.failStale(IN_PROGRESS_STATUSES, cutoff, STALE_MESSAGE, now);
        if (failed > 0) {
            log.warn("Reconciled {} stale import task(s) stuck since before {}", failed, cutoff);
        }
    }
}
