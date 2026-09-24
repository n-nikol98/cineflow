package com.nedko.cineflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.domain.ImportTask.Status;

class ImportIntegrationTest extends AbstractIntegrationTest {

    @Test
    void uploadReturnsImmediatelyAndAsynchronouslyPersistsValidatedMovies() throws Exception {
        final long taskId = upload("""
                <movies>
                  <movie>
                    <title>  First   Movie </title>
                    <releaseYear>2020</releaseYear>
                    <durationMinutes>101</durationMinutes>
                    <genres><genre> Drama </genre></genres>
                    <director id=" director-1 ">  Director  One </director>
                    <actors><actor id=" actor-1 "> Actor One </actor></actors>
                  </movie>
                  <movie>
                    <title>Second Movie</title>
                    <releaseYear>2021</releaseYear>
                    <director id="director-2">Director Two</director>
                  </movie>
                </movies>
                """);

        final ImportTask completed = awaitTerminalTask(taskId);

        assertEquals(Status.COMPLETED, completed.getStatus());
        assertEquals(2, completed.getRecordCount());
        assertEquals(2, movies.count());
        mockMvc.perform(get("/imports/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recordCount").value(2));
    }

    @Test
    void malformedXmlCompletesTheTaskAsFailedRatherThanLeavingItInProgress() throws Exception {
        final long taskId = upload("<movies><movie><title>Missing required fields</title></movie></movies>");

        final ImportTask failed = awaitTerminalTask(taskId);

        assertEquals(Status.FAILED, failed.getStatus());
        assertEquals(0, failed.getRecordCount());
        assertTrue(failed.getErrorMessage() != null && !failed.getErrorMessage().isBlank());
        assertEquals(0, movies.count());
    }

    @Test
    void emptyUploadReturnsTheRestApiErrorContract() throws Exception {
        mockMvc.perform(multipart("/imports")
                        .file(new MockMultipartFile("file", "empty.xml",
                         "application/xml", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.message").value("The uploaded file is empty."));
    }
}
