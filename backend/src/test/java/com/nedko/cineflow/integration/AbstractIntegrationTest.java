package com.nedko.cineflow.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.repository.ImportTaskRepository;
import com.nedko.cineflow.repository.MovieRepository;

/**
 * Shared setup and helpers for full-context integration tests: both
 * {@link ImportIntegrationTest} and {@link DeliveryIntegrationTest} need the real,
 * asynchronous import pipeline to get a completed {@link ImportTask}/persisted
 * {@code Movie} to work with, so uploading an XML file and polling its task to a
 * terminal status lives here rather than being duplicated in each.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ImportTaskRepository tasks;

    @Autowired
    protected MovieRepository movies;

    @BeforeEach
    void clearImportData() {
        movies.deleteAll();
        tasks.deleteAll();
    }

    protected long upload(final String xml) throws Exception {
        final String response = mockMvc.perform(multipart("/imports")
                        .file(new MockMultipartFile("file", "movies.xml", "application/xml",
                                xml.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).required("id").asLong();
    }

    protected ImportTask awaitTerminalTask(final long taskId) throws InterruptedException {
        final Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        ImportTask task;
        do {
            task = tasks.findByIdOrThrow(taskId);
            final ImportTask.Status status = task.getStatus();
            if (ImportTask.Status.COMPLETED.equals(status)
                    || ImportTask.Status.FAILED.equals(status)) {
                return task;
            }
            Thread.sleep(25);
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError("Import task " + taskId + " did not reach a terminal status");
    }
}
