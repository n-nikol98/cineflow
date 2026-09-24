package com.nedko.cineflow.service.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import com.nedko.cineflow.domain.Director;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.domain.MovieDelivery.Status;
import com.nedko.cineflow.repository.MovieDeliveryRepository;
import com.nedko.cineflow.repository.MovieRepository;
import jakarta.persistence.EntityManager;

/**
 * Verifies, against a real (embedded H2) database rather than a mock, that
 * {@code MovieDelivery}'s {@code @Version} field genuinely makes Hibernate detect and
 * reject a stale concurrent update — the mechanism {@code DeliveryScheduler#retry}
 * relies on to avoid silently overwriting an in-flight scheduled delivery attempt for
 * the same movie.
 *
 * <p>{@code @DataJpaTest} normally wraps each test method in a single transaction
 * (rolled back afterwards), which would make two "concurrent" loads in the same test
 * share one persistence context and thus the exact same in-memory entity instance —
 * never actually racing. Each simulated transaction below is instead run explicitly,
 * with its own {@code TransactionTemplate} and commit, so the two loads are genuinely
 * independent persistence contexts, as two real concurrent requests would be.
 */
@DataJpaTest
class DeliveryOptimisticLockingTest {

    @Autowired
    private MovieRepository movies;

    @Autowired
    private MovieDeliveryRepository deliveries;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransaction() {
        final TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        return template;
    }

    @Test
    void concurrentUpdateOfTheSameDeliveryLosesToTheFirstCommittedChange() {
        final Long movieId = newTransaction().execute(status -> {
            final ImportTask task = new ImportTask();
            task.setFileName("test.xml");
            final Director director = new Director("dir-1", "Some Director");
            entityManager.persist(director);
            entityManager.persist(task);
            final Movie movie = new Movie(task, "Some Movie", director, 2020);
            movies.saveAndFlush(movie);
            return movie.getId();
        });
        assertNotNull(movieId);

        // Two concurrent transactions, e.g. a scheduled delivery attempt and a manual
        // retry, each load their own, independent copy of the same delivery before
        // either commits. We keep this detached copy to simulate the second
        // transaction's persistence context, which never sees the first's commit.
        final MovieDelivery staleCopy = newTransaction()
                .execute(status -> assertPresent(deliveries.findByMovieId(movieId)));
        assertNotNull(staleCopy);

        // The first transaction (the scheduled delivery attempt) commits first,
        // marking the delivery sent and bumping its version.
        newTransaction().executeWithoutResult(status -> {
            final MovieDelivery delivery = assertPresent(deliveries.findByMovieId(movieId));
            assertEquals(staleCopy.getVersion(), delivery.getVersion());
            delivery.markSent();
            deliveries.saveAndFlush(delivery);
        });

        // The second transaction (the concurrent manual retry) still holds its
        // pre-commit, now-stale copy and tries to save its own change: Hibernate must
        // reject this rather than silently reverting the first transaction's change.
        staleCopy.reset();
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> newTransaction()
                .executeWithoutResult(status -> deliveries.saveAndFlush(staleCopy)));

        // The first transaction's change is the one that is actually kept.
        final Status persistedStatus = newTransaction().execute(status ->
                assertPresent(deliveries.findByMovieId(movieId)).getStatus());
        assertNotNull(persistedStatus);
        assertEquals(Status.SENT, persistedStatus);
    }

    private static <T> T assertPresent(final Optional<T> value) {
        assertTrue(value.isPresent());
        return value.get();
    }
}
