package com.nedko.cineflow.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Delivery delivery, @Name("import") Import importProperties, Retry retry) {

    /**
     * Configuration for the async import pipeline: sizing its thread pool, and
     * detecting/failing "stale" tasks stuck in {@code QUEUED}/{@code PROCESSING}
     * because the application restarted or crashed mid-import, leaving nothing left
     * running to ever finish them. See {@code ImportReconciler} for the reconciliation
     * job that uses {@code staleAfter}/{@code checkInterval}.
     *
     * @param concurrency size of the bounded thread pool that processes uploaded XML
     *         files asynchronously
     * @param staleAfter how old (by {@code createdAt}) a still-in-progress task must be
     *         before it is considered stale and failed
     * @param checkInterval how often the reconciliation job runs
     */
    public record Import(int concurrency, Duration staleAfter, Duration checkInterval) {
    }

    public record Delivery(String url, String cron, int concurrency) {
    }

    /**
     * Retry timing for outbound delivery. Delays are integer milliseconds because Spring
     * Retry's {@code @Backoff} expressions consume millisecond values.
     */
    public record Retry(int maxAttempts, long initialDelay, double multiplier, long maxDelay) {
    }
}
