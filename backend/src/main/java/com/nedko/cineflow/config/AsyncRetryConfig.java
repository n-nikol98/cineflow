package com.nedko.cineflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Application infrastructure configuration.
 *
 * <p>Explicit async/retry advisor ordering is required because both {@code @Async} and
 * {@code @Retryable} are implemented as AOP proxy advisors. The adjacent
 * low-precedence slots reserve {@code Ordered.LOWEST_PRECEDENCE} for Spring's
 * transaction advisor, producing an {@code @Async -> @Retryable -> @Transactional}
 * interceptor stack whenever a method uses all three.
 *
 * <p>A live retry/backoff test showed that using {@code Ordered.HIGHEST_PRECEDENCE} for
 * {@code @EnableAsync} while relying on {@code @EnableRetry}'s default order prevented
 * retry interception entirely.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync(order = Ordered.LOWEST_PRECEDENCE - 2)
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
class AsyncRetryConfig {

    /**
     * Bounded thread pool backing {@code @Async} import processing
     * ({@code ImportProcessor.process}). Named explicitly and referenced via
     * {@code @Async("importExecutor")} so it stays independent from
     * {@link #deliveryExecutor}: without a qualifier, {@code @Async} resolves to
     * whichever single {@link TaskExecutor} bean is present, which would otherwise
     * make imports share (and be capped by) the outbound delivery pool.
     */
    @Bean
    TaskExecutor importExecutor(final AppProperties properties) {
        return boundedExecutor(properties.importProperties().concurrency(), "import-");
    }

    /**
     * Bounded thread pool used to send eligible movies to the outbound endpoint in
     * parallel during a single scheduled delivery run. Bounding it (rather than using
     * an unbounded executor, e.g. one thread per movie) caps how many concurrent
     * outbound HTTP calls and DB connections the delivery pipeline can use at once,
     * regardless of how many movies are eligible in a given run.
     */
    @Bean
    TaskExecutor deliveryExecutor(final AppProperties properties) {
        return boundedExecutor(properties.delivery().concurrency(), "delivery-");
    }

    /**
     * Creates a fixed-size (core == max) thread pool executor bounded to
     * {@code concurrency} threads (minimum 1), used to cap concurrent async work.
     */
    private static TaskExecutor boundedExecutor(final int concurrency,
            final String threadNamePrefix) {
        final int size = Math.max(1, concurrency);
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
