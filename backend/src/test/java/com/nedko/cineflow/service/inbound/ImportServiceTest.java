package com.nedko.cineflow.service.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.dto.ImportTaskDto;
import com.nedko.cineflow.exception.EmptyFileException;
import com.nedko.cineflow.exception.FileReadException;
import com.nedko.cineflow.exception.NotFoundException;
import com.nedko.cineflow.mapper.ImportTaskMapper;
import com.nedko.cineflow.repository.ImportTaskRepository;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    private static final long TASK_ID = 42L;
    private static final long SECOND_TASK_ID = 1L;
    private static final long MISSING_TASK_ID = 99L;
    private static final String FILE_NAME = "movies.xml";
    private static final String EMPTY_FILE_NAME = "empty.xml";
    private static final String BROKEN_FILE_NAME = "broken.xml";
    private static final String DEFAULT_FILE_NAME = "upload.xml";
    private static final String XML_CONTENT_TYPE = "application/xml";
    private static final String IO_FAILURE_MESSAGE = "storage unavailable";
    private static final String MULTIPART_NAME = "file";

    @Mock
    private ImportTaskRepository repository;

    @Mock
    private ImportTaskMapper mapper;

    @Mock
    private ImportProcessor processor;

    @InjectMocks
    private ImportService service;

    @Captor
    private ArgumentCaptor<ImportTask> taskCaptor;

    @Test
    void rejectsEmptyUploadWithoutPersistingOrDispatching() {
        final MockMultipartFile file = new MockMultipartFile(MULTIPART_NAME, EMPTY_FILE_NAME, XML_CONTENT_TYPE,
                new byte[0]);

        assertThrows(EmptyFileException.class, () -> service.enqueue(file));

        verifyNoInteractions(repository, mapper, processor);
    }

    @Test
    void reportsFileReadFailureWithoutPersistingOrDispatching() throws Exception {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(BROKEN_FILE_NAME);
        when(file.getBytes()).thenThrow(new IOException(IO_FAILURE_MESSAGE));

        assertThrows(FileReadException.class, () -> service.enqueue(file));

        verifyNoInteractions(repository, mapper, processor);
    }

    @Test
    void persistsQueuedTaskMapsItAndDispatchesTheExactBytes() {
        final byte[] bytes = "<movies/>".getBytes(StandardCharsets.UTF_8);
        final MockMultipartFile file = new MockMultipartFile(MULTIPART_NAME, FILE_NAME, XML_CONTENT_TYPE, bytes);
        final ImportTask saved = new ImportTask();
        saved.setId(TASK_ID);
        saved.setFileName(FILE_NAME);
        final ImportTaskDto dto = new ImportTaskDto(TASK_ID, FILE_NAME, ImportTask.Status.QUEUED, 0, "");
        when(repository.save(any(ImportTask.class))).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(dto);

        final ImportTaskDto result = service.enqueue(file);

        assertEquals(dto, result);
        verify(repository).save(taskCaptor.capture());
        assertEquals(FILE_NAME, taskCaptor.getValue().getFileName());
        verify(processor).process(TASK_ID, bytes);
        verify(mapper).toDto(saved);
    }

    @Test
    void suppliesDefaultNameWhenTheUploadHasNone() throws Exception {
        final MultipartFile file = mock(MultipartFile.class);
        final byte[] bytes = {1};
        final ImportTask saved = new ImportTask();
        saved.setId(SECOND_TASK_ID);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getBytes()).thenReturn(bytes);
        when(repository.save(any(ImportTask.class))).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(new ImportTaskDto(SECOND_TASK_ID, DEFAULT_FILE_NAME,
                ImportTask.Status.QUEUED, 0, ""));

        service.enqueue(file);

        verify(repository).save(taskCaptor.capture());
        assertEquals(DEFAULT_FILE_NAME, taskCaptor.getValue().getFileName());
        verify(processor).process(SECOND_TASK_ID, bytes);
        verify(processor, never()).process(isNull(), any());
    }

    @Test
    void findReturnsMappedDtoForExistingTask() {
        final ImportTask task = new ImportTask();
        task.setId(TASK_ID);
        task.setFileName(FILE_NAME);
        final ImportTaskDto dto = new ImportTaskDto(TASK_ID, FILE_NAME, ImportTask.Status.COMPLETED, 3, "");
        when(repository.findByIdOrThrow(TASK_ID)).thenReturn(task);
        when(mapper.toDto(task)).thenReturn(dto);

        final ImportTaskDto result = service.find(TASK_ID);

        assertEquals(dto, result);
        verify(mapper).toDto(task);
    }

    @Test
    void findPropagatesNotFoundExceptionWhenTaskIsMissing() {
        when(repository.findByIdOrThrow(MISSING_TASK_ID))
                .thenThrow(NotFoundException.forEntity("Import task", MISSING_TASK_ID));

        assertThrows(NotFoundException.class, () -> service.find(MISSING_TASK_ID));

        verifyNoInteractions(mapper);
    }
}
