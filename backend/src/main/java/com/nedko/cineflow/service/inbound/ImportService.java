package com.nedko.cineflow.service.inbound;

import java.io.IOException;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nedko.cineflow.domain.ImportTask;
import com.nedko.cineflow.dto.ImportTaskDto;
import com.nedko.cineflow.mapper.ImportTaskMapper;
import com.nedko.cineflow.repository.ImportTaskRepository;
import com.nedko.cineflow.exception.EmptyFileException;
import com.nedko.cineflow.exception.FileReadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for importing movies from an uploaded XML file.
 *
 * <p>Persists an {@link ImportTask} record synchronously (so its id is immediately
 * available to the caller) and hands the file bytes off to {@link ImportProcessor} for
 * asynchronous parsing and persistence, so the HTTP request completes without waiting
 * for the (potentially large) file to be fully processed. Callers poll {@link #find}
 * with the returned id to observe the task's progress and outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportTaskRepository repository;
    private final ImportTaskMapper mapper;
    private final ImportProcessor processor;

    /**
     * Validates and reads the uploaded file, records a new {@link ImportTask} for it,
     * and dispatches it to {@link ImportProcessor} for asynchronous processing.
     *
     * @param file the uploaded XML file
     * @return the newly created import task, with status {@code QUEUED}
     * @throws EmptyFileException if {@code file} is empty
     * @throws FileReadException if {@code file}'s bytes could not be read
     */
    public ImportTaskDto enqueue(final MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyFileException("The uploaded file is empty.");
        }

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (final IOException exception) {
            log.error("Could not read uploaded file \"{}\": {}",
                    file.getOriginalFilename(), exception.getMessage(), exception);
            throw new FileReadException("The uploaded file could not be read.", exception);
        }

        final ImportTask newTask = new ImportTask();
        newTask.setFileName(Optional.ofNullable(file.getOriginalFilename()).orElse("upload.xml"));
        final ImportTask task = repository.save(newTask);
        log.info("Queued import task {} for file \"{}\" ({} bytes)",
                task.getId(), task.getFileName(), bytes.length);
        processor.process(task.getId(), bytes);
        return mapper.toDto(task);
    }

    /**
     * Looks up an import task's current status, record count, and error message (if any).
     *
     * @param id the import task's identifier
     * @return the task's current state
     */
    @Transactional(readOnly = true)
    public ImportTaskDto find(final Long id) {
        final ImportTask task = repository.findByIdOrThrow(id);
        return mapper.toDto(task);
    }
}
