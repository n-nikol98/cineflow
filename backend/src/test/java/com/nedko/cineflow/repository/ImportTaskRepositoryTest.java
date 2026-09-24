package com.nedko.cineflow.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import com.nedko.cineflow.domain.ImportTask;
import jakarta.persistence.EntityManager;

/**
 * Verifies {@link ImportTaskRepository#failStale}, the bulk update
 * {@code ImportReconciler} relies on to recover
 * import tasks abandoned by an application restart/crash: only tasks that are both
 * still in progress ({@code QUEUED}/{@code PROCESSING}) and older than the cutoff
 * should be failed; terminal or recent tasks must be left untouched.
 *
 * <p>{@code @DataJpaTest} normally wraps each test method in a single transaction
 * (rolled back afterwards), but {@code failStale} itself runs in its own
 * {@code REQUIRES_NEW} transaction — which, at read-committed isolation, cannot see
 * this method's own still-uncommitted inserts. Fixture rows are therefore persisted and
 * committed in their own explicit transaction first, exactly like
 * {@code DeliveryOptimisticLockingTest} does for the same reason.
 */
@DataJpaTest
class ImportTaskRepositoryTest {

    @Autowired
    private ImportTaskRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransaction() {
        final TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        return template;
    }

    private ImportTask persistTask(final ImportTask.Status status, final Instant createdAt) {
        final ImportTask task = new ImportTask();
        task.setFileName("movies.xml");
        task.setStatus(status);
        task.setCreatedAt(createdAt);
        entityManager.persist(task);
        return task;
    }

    @Test
    void onlyFailsInProgressTasksOlderThanTheCutoff() {
        final Instant now = Instant.now();
        final Instant cutoff = now.minus(10, ChronoUnit.MINUTES);

        final List<Long> ids = Objects.requireNonNull(newTransaction().execute(status -> List.of(
                persistTask(ImportTask.Status.QUEUED, now.minus(20, ChronoUnit.MINUTES)).getId(),
                persistTask(ImportTask.Status.PROCESSING, now.minus(15, ChronoUnit.MINUTES)).getId(),
                persistTask(ImportTask.Status.PROCESSING, now.minus(1, ChronoUnit.MINUTES)).getId(),
                persistTask(ImportTask.Status.COMPLETED, now.minus(30, ChronoUnit.MINUTES)).getId())));
        final Long staleQueuedId = ids.get(0);
        final Long staleProcessingId = ids.get(1);
        final Long recentProcessingId = ids.get(2);
        final Long staleButCompletedId = ids.get(3);

        final int failed = Objects.requireNonNull(newTransaction().execute(status ->
                repository.failStale(List.of(ImportTask.Status.QUEUED, ImportTask.Status.PROCESSING),
                        cutoff, "Abandoned.", now)));

        assertEquals(2, failed);
        assertEquals(ImportTask.Status.FAILED, repository.findByIdOrThrow(staleQueuedId).getStatus());
        assertEquals(ImportTask.Status.FAILED, repository.findByIdOrThrow(staleProcessingId).getStatus());
        assertEquals("Abandoned.", repository.findByIdOrThrow(staleQueuedId).getErrorMessage());
        assertEquals(ImportTask.Status.PROCESSING, repository.findByIdOrThrow(recentProcessingId).getStatus());
        assertEquals(ImportTask.Status.COMPLETED, repository.findByIdOrThrow(staleButCompletedId).getStatus());
    }
}

