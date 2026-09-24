package com.nedko.cineflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nedko.cineflow.dto.ImportTaskDto;
import com.nedko.cineflow.service.inbound.ImportService;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * REST endpoint for uploading movie XML files and checking their import progress.
 *
 * <p>{@link #upload} enqueues a file for asynchronous parsing/persistence and
 * returns immediately (HTTP 202 Accepted) with the newly created task; {@link #status}
 * lets callers poll that task for its current status.
 */
@RestController
@RequestMapping("/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService service;

    /**
     * Accepts an uploaded XML file for asynchronous import.
     *
     * @param file the uploaded movie XML file
     * @return the newly created import task, with status {@code QUEUED}
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportTaskDto upload(@RequestPart("file") @NotNull final MultipartFile file) {
        return service.enqueue(file);
    }

    /**
     * Looks up an import task's current status, record count, and error message (if any).
     *
     * @param id the import task's identifier
     * @return the task's current state
     */
    @GetMapping("/{id}")
    public ImportTaskDto status(@PathVariable final Long id) {
        return service.find(id);
    }
}
