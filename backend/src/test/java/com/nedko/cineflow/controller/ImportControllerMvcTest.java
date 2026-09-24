package com.nedko.cineflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.nedko.cineflow.domain.ImportTask.Status;
import com.nedko.cineflow.dto.ImportTaskDto;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.exception.handler.GlobalExceptionHandler;
import com.nedko.cineflow.service.inbound.ImportService;

class ImportControllerMvcTest {

    private static final String IMPORTS_PATH = "/imports";
    private static final long TASK_ID = 7L;
    private static final String FILE_NAME = "movies.xml";
    private static final long MISSING_TASK_ID = 99L;

    private ImportService imports;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        imports = Mockito.mock(ImportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ImportController(imports))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadAcceptsMultipartFileAndReturnsQueuedTask() throws Exception {
        when(imports.enqueue(any())).thenReturn(new ImportTaskDto(TASK_ID, FILE_NAME,
                Status.QUEUED, 0, ""));

        mockMvc.perform(multipart(IMPORTS_PATH)
                        .file(new MockMultipartFile("file", FILE_NAME, MediaType.APPLICATION_XML_VALUE,
                                "<movies/>".getBytes())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.fileName").value(FILE_NAME))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(imports).enqueue(any());
    }

    @Test
    void statusReturnsCompletedTaskWhenItExists() throws Exception {
        final int recordCount = 42;
        when(imports.find(TASK_ID)).thenReturn(new ImportTaskDto(TASK_ID, FILE_NAME,
                Status.COMPLETED, recordCount, ""));

        mockMvc.perform(get(IMPORTS_PATH + "/" + TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_ID))
                .andExpect(jsonPath("$.fileName").value(FILE_NAME))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recordCount").value(recordCount));

        verify(imports).find(TASK_ID);
    }

    @Test
    void statusReturnsNotFoundApiErrorWhenTheTaskDoesNotExist() throws Exception {
        when(imports.find(MISSING_TASK_ID)).thenThrow(NotFoundException.forEntity("Import task", MISSING_TASK_ID));

        mockMvc.perform(get(IMPORTS_PATH + "/" + MISSING_TASK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Import task 99 was not found."));
    }
}
