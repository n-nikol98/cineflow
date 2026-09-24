package com.nedko.cineflow.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nedko.cineflow.domain.Movie;
import com.nedko.cineflow.domain.MovieDelivery;
import com.nedko.cineflow.domain.MovieDelivery.Status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.repository.MovieDeliveryRepository;
import com.nedko.cineflow.service.outbound.DeliveryScheduler;
import com.sun.net.httpserver.HttpServer;

class DeliveryIntegrationTest extends AbstractIntegrationTest {

    private static final HttpServer RECEIVER = startReceiver();
    private static final ExecutorService RECEIVER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicInteger RECEIVER_STATUS = new AtomicInteger();
    private static final AtomicInteger RECEIVED_REQUESTS = new AtomicInteger();

    private static volatile String receivedBody;

    @Autowired
    private DeliveryScheduler scheduler;

    @Autowired
    private MovieDeliveryRepository deliveries;

    @DynamicPropertySource
    static void configureDelivery(final DynamicPropertyRegistry registry) {
        registry.add("app.delivery.url",
                () -> "http://127.0.0.1:" + RECEIVER.getAddress().getPort() + "/deliveries");
        registry.add("app.retry.max-attempts", () -> "2");
        registry.add("app.retry.initial-delay", () -> "10");
        registry.add("app.retry.multiplier", () -> "1");
        registry.add("app.retry.max-delay", () -> "10");
    }

    @AfterAll
    static void stopReceiver() {
        RECEIVER.stop(0);
        RECEIVER_EXECUTOR.shutdownNow();
    }

    @BeforeEach
    void resetReceiver() {
        RECEIVER_STATUS.set(200);
        RECEIVED_REQUESTS.set(0);
        receivedBody = null;
    }

    @Test
    void deliversImportedMovieToConfiguredReceiverAndRecordsSuccess() throws Exception {
        final long movieId = importMovie();

        scheduler.scheduledSend();

        final MovieDelivery delivery = deliveries.findByMovieId(movieId).orElseThrow();
        assertEquals(Status.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(1, RECEIVED_REQUESTS.get());
        assertTrue(receivedBody.contains("\"title\":\"Delivered Movie\""));
    }

    @Test
    void recordsPermanentFailureAfterConfiguredRetriesAreExhausted() throws Exception {
        final long movieId = importMovie();
        RECEIVER_STATUS.set(500);

        scheduler.scheduledSend();

        final MovieDelivery delivery = deliveries.findByMovieId(movieId).orElseThrow();
        assertEquals(Status.FAILED, delivery.getStatus());
        assertEquals(2, delivery.getAttempts());
        assertEquals(2, RECEIVED_REQUESTS.get());
        assertFalse(delivery.getLastError().isBlank());
    }

    private long importMovie() throws Exception {
        final long taskId = upload("""
                <movies>
                  <movie>
                    <title>Delivered Movie</title>
                    <releaseYear>2020</releaseYear>
                    <director id="director-1">Director One</director>
                  </movie>
                </movies>
                """);
        final ImportTask task = awaitTerminalTask(taskId);
        assertEquals(ImportTask.Status.COMPLETED, task.getStatus());
        return movies.findAll().stream()
                .map(Movie::getId)
                .findFirst()
                .orElseThrow();
    }

    private static HttpServer startReceiver() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/deliveries", exchange -> {
                RECEIVED_REQUESTS.incrementAndGet();
                receivedBody = new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(RECEIVER_STATUS.get(), -1);
                exchange.close();
            });
            server.setExecutor(RECEIVER_EXECUTOR);
            server.start();
            return server;
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not start test delivery receiver", exception);
        }
    }
}
