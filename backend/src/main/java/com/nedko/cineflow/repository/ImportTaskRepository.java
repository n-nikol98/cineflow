package com.nedko.cineflow.repository;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.ImportTask.Status;
import com.nedko.cineflow.exception.NotFoundException;

public interface ImportTaskRepository extends JpaRepository<ImportTask, Long> {

    /**
     * Looks up an import task by id, throwing instead of returning an empty
     * {@code Optional}.
     *
     * @param id the import task's identifier
     * @return the import task
     * @throws NotFoundException if no import task exists with the given id
     */
    default ImportTask findByIdOrThrow(final Long id) {
        return findById(id)
                .orElseThrow(() -> NotFoundException.forEntity("Import task", id));
    }

    /**
     * Bulk-updates {@code task}'s status directly in the database (bypassing the
     * persistence context), so the change is immediately visible to other transactions
     * without needing the full entity loaded. Runs in its own new transaction
     * ({@code REQUIRES_NEW}) so the status change is committed independently of, and
     * visible before, the (potentially slow) parsing/persistence that follows it in
     * {@code ImportProcessor}.
     *
     * @param id the import task's identifier
     * @param status the new status to set
     * @return the number of rows updated (0 or 1)
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE ImportTask task
            SET task.status = :status
            WHERE task.id = :id
            """)
    int updateStatus(@Param("id") final Long id,
            @Param("status") final ImportTask.Status status);

    /**
     * Bulk-fails every task still in one of {@code inProgressStatuses} whose
     * {@code createdAt} is older than {@code cutoff} — used by
     * {@code ImportReconciler} to recover tasks left stuck in
     * {@code QUEUED}/{@code PROCESSING} by an application restart or crash mid-import,
     * since nothing is left running to ever finish them otherwise.
     *
     * @param inProgressStatuses the non-terminal statuses eligible to be considered stale
     * @param cutoff tasks created before this instant are eligible
     * @param errorMessage the message to record on each failed task
     * @param completedAt the completion timestamp to record on each failed task
     * @return the number of tasks updated
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE ImportTask task
            SET task.status = com.nedko.cineflow.domain.ImportTask.Status.FAILED,
                task.errorMessage = :errorMessage,
                task.completedAt = :completedAt
            WHERE task.status IN :inProgressStatuses
            AND task.createdAt < :cutoff
            """)
    int failStale(@Param("inProgressStatuses") final Collection<Status> inProgressStatuses,
            @Param("cutoff") final Instant cutoff,
            @Param("errorMessage") final String errorMessage,
            @Param("completedAt") final Instant completedAt);
}
