package com.nedko.cineflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Verifies the wiring of {@link AsyncRetryConfig}'s {@code deliveryExecutor}: that
 * {@code @Async}/{@code @Retryable} methods actually run their retry attempts on that
 * executor's threads, honor the configured backoff delay between attempts, and (for
 * {@code @Transactional} methods) open and roll back a fresh transaction per attempt
 * rather than reusing one across retries.
 *
 * <p>Rather than depending on the real {@code deliveryExecutor}/transaction manager
 * beans, this test boots a minimal {@link AnnotationConfigApplicationContext} containing
 * only {@link AsyncRetryConfig} plus two test doubles:
 * <ul>
 *   <li>{@link RetryProbe}, a bean whose methods are annotated the same way production
 *       delivery code is (async + retryable, one of them also transactional), and which
 *       records the thread name of each attempt so the test can assert against it.</li>
 *   <li>{@link RecordingTransactionManager}, a bare-bones {@link AbstractPlatformTransactionManager}
 *       that records which thread began each transaction and how many commits/rollbacks
 *       occurred, without touching a real database.</li>
 * </ul>
 */
class AsyncRetryConfigTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_DELAY_MILLIS = 50;
    private static final Duration BACKOFF_DELAY = Duration.ofMillis(BACKOFF_DELAY_MILLIS);
    private static final String DELIVERY_THREAD_PREFIX = "delivery-";
    private static final long AWAIT_TIMEOUT_SECONDS = 5;
    private static final String IMPORT_CONCURRENCY_PROPERTY = "app.import.concurrency";
    private static final String DELIVERY_CONCURRENCY_PROPERTY = "app.delivery.concurrency";

    /**
     * {@link RetryProbe#failAndRecover()} always throws, so it must retry
     * {@link #MAX_ATTEMPTS} times before {@link RetryProbe#recover} kicks in. This
     * asserts every attempt (and the final recovery) ran on a {@code delivery-*}
     * executor thread, and that the elapsed time across attempts is at least the
     * expected cumulative backoff.
     */
    @Test
    void retriesAndRecoversOnDeliveryExecutor() throws Exception {
        try (AnnotationConfigApplicationContext context = createContext()) {
            final RetryProbe probe = context.getBean(RetryProbe.class);

            final String recoveryThread = probe.failAndRecover()
                    .get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            final List<String> attemptThreads = probe.attemptThreads();
            assertEquals(MAX_ATTEMPTS, attemptThreads.size());
            assertTrue(attemptThreads.stream()
                    .allMatch(threadName -> threadName.startsWith(DELIVERY_THREAD_PREFIX)));
            assertTrue(recoveryThread.startsWith(DELIVERY_THREAD_PREFIX));
            assertTrue(probe.elapsedBetweenFirstAndLastAttempt()
                    .compareTo(BACKOFF_DELAY.multipliedBy(MAX_ATTEMPTS - 1)) >= 0);
        }
    }

    /**
     * Same retry/recovery scenario as {@link #retriesAndRecoversOnDeliveryExecutor()},
     * but for {@link RetryProbe#failTransactionallyAndRecover()}, which is additionally
     * {@code @Transactional}. Asserts each retry attempt opens and rolls back its own
     * transaction (never a commit, since every attempt fails), all on delivery executor
     * threads — i.e. Spring Retry re-runs the whole transactional method per attempt
     * rather than retrying inside a single long-lived transaction.
     */
    @Test
    void retriesEachTransactionalAttemptOnDeliveryExecutor() throws Exception {
        try (AnnotationConfigApplicationContext context = createContext()) {
            final RetryProbe probe = context.getBean(RetryProbe.class);
            final RecordingTransactionManager transactionManager =
                    context.getBean(RecordingTransactionManager.class);

            final String recoveryThread = probe.failTransactionallyAndRecover()
                    .get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            final List<String> transactionalAttemptThreads = probe.transactionalAttemptThreads();
            assertEquals(MAX_ATTEMPTS, transactionalAttemptThreads.size());
            assertTrue(transactionalAttemptThreads.stream()
                    .allMatch(threadName -> threadName.startsWith(DELIVERY_THREAD_PREFIX)));
            final List<String> transactionThreads = transactionManager.transactionThreads();
            assertEquals(MAX_ATTEMPTS, transactionThreads.size());
            assertTrue(transactionThreads.stream()
                    .allMatch(threadName -> threadName.startsWith(DELIVERY_THREAD_PREFIX)));
            assertEquals(MAX_ATTEMPTS, transactionManager.rollbackCount());
            assertEquals(0, transactionManager.commitCount());
            assertTrue(recoveryThread.startsWith(DELIVERY_THREAD_PREFIX));
        }
    }

    /**
     * Boots a minimal context with just {@link AsyncRetryConfig} (which provides the
     * real {@code deliveryExecutor}/retry infrastructure), the test doubles below, and
     * the concurrency properties {@link AsyncRetryConfig} requires to be bound.
     */
    private AnnotationConfigApplicationContext createContext() {
        final AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", 
                Map.of(IMPORT_CONCURRENCY_PROPERTY, "1", DELIVERY_CONCURRENCY_PROPERTY, "1")));
        context.register(AsyncRetryConfig.class, TransactionTestConfig.class, RetryProbe.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionTestConfig {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    /**
     * Bean whose two methods mirror the annotations real delivery code uses
     * ({@code @Async("deliveryExecutor")} + {@code @Retryable}, one of them also
     * {@code @Transactional}), but always fail so every retry attempt runs and gets
     * recorded, until {@link #recover} finally supplies a result.
     */
    static class RetryProbe {

        private final List<String> attemptThreads = new CopyOnWriteArrayList<>();
        private final List<Long> attemptTimes = new CopyOnWriteArrayList<>();
        private final List<String> transactionalAttemptThreads =
                new CopyOnWriteArrayList<>();

        @Async("deliveryExecutor")
        @Retryable(maxAttempts = MAX_ATTEMPTS,
                backoff = @Backoff(delay = BACKOFF_DELAY_MILLIS))
        CompletableFuture<String> failAndRecover() {
            attemptThreads.add(Thread.currentThread().getName());
            attemptTimes.add(System.nanoTime());
            throw new IllegalStateException("expected test failure");
        }

        @Async("deliveryExecutor")
        @Retryable(maxAttempts = MAX_ATTEMPTS,
                backoff = @Backoff(delay = BACKOFF_DELAY_MILLIS))
        @Transactional
        CompletableFuture<String> failTransactionallyAndRecover() {
            transactionalAttemptThreads.add(Thread.currentThread().getName());
            throw new IllegalStateException("expected transactional test failure");
        }

        @Recover
        CompletableFuture<String> recover(final IllegalStateException exception) {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }

        List<String> attemptThreads() {
            return attemptThreads;
        }

        List<String> transactionalAttemptThreads() {
            return transactionalAttemptThreads;
        }

        Duration elapsedBetweenFirstAndLastAttempt() {
            return Duration.ofNanos(attemptTimes.getLast() - attemptTimes.getFirst());
        }
    }

    /**
     * Bare-bones {@link AbstractPlatformTransactionManager} that only records which
     * thread began each transaction and how many commits/rollbacks occurred, without
     * touching any real datastore — just enough to verify {@code @Transactional} retry
     * behavior in {@link AsyncRetryConfigTest#retriesEachTransactionalAttemptOnDeliveryExecutor()}.
     */
    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final List<String> transactionThreads = new CopyOnWriteArrayList<>();
        private int rollbackCount;
        private int commitCount;

        @Override
        @NonNull
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(@NonNull final Object transaction,
                @NonNull final TransactionDefinition definition) {
            transactionThreads.add(Thread.currentThread().getName());
        }

        @Override
        protected void doCommit(@NonNull final DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(@NonNull final DefaultTransactionStatus status) {
            rollbackCount++;
        }

        List<String> transactionThreads() {
            return transactionThreads;
        }

        int rollbackCount() {
            return rollbackCount;
        }

        int commitCount() {
            return commitCount;
        }
    }
}
