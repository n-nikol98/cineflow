package com.nedko.cineflow.exception.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.nedko.cineflow.dto.ApiErrorDto;
import com.nedko.cineflow.exception.DeliveryConflictException;
import com.nedko.cineflow.exception.EmptyFileException;
import com.nedko.cineflow.exception.FileReadException;
import com.nedko.cineflow.exception.NotFoundException;

class GlobalExceptionHandlerTest {

    private static final String DELIVERY_CONFLICT_MESSAGE = "in progress";
    private static final String EMPTY_FILE_MESSAGE = "empty";
    private static final String FILE_READ_MESSAGE = "could not read";
    private static final long MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024L;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsKnownExceptionsToTheirDocumentedApiErrors() {
        assertError(handler.handleNotFound(NotFoundException.forEntity("Movie", 4L)),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "Movie 4 was not found.");
        assertError(handler.handleDeliveryConflict(new DeliveryConflictException(DELIVERY_CONFLICT_MESSAGE)),
                HttpStatus.CONFLICT, "DELIVERY_CONFLICT", DELIVERY_CONFLICT_MESSAGE);
        assertError(handler.handleEmptyFile(new EmptyFileException(EMPTY_FILE_MESSAGE)),
                HttpStatus.BAD_REQUEST, "EMPTY_FILE", EMPTY_FILE_MESSAGE);
        assertError(handler.handleFileRead(
                new FileReadException(FILE_READ_MESSAGE, new IllegalStateException("disk"))),
                HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR", FILE_READ_MESSAGE);
        assertError(handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(MAX_UPLOAD_SIZE_BYTES)),
                HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The uploaded file exceeds the maximum allowed size.");
    }

    @Test
    void hidesUnexpectedExceptionDetailsFromApiConsumers() {
        assertError(handler.handleUnexpected(new IllegalStateException("database password exposed")),
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    private static void assertError(final ResponseEntity<ApiErrorDto> response,
            final HttpStatus status, final String code, final String message) {
        assertEquals(status, response.getStatusCode());
        assertEquals(new ApiErrorDto(code, message), response.getBody());
    }
}
