package com.nedko.cineflow.exception.handler;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.nedko.cineflow.dto.ApiErrorDto;
import com.nedko.cineflow.exception.DeliveryConflictException;
import com.nedko.cineflow.exception.EmptyFileException;
import com.nedko.cineflow.exception.FileReadException;
import com.nedko.cineflow.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleNotFound(final NotFoundException exception) {
        log.debug("Not found: {}", exception.getMessage());
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DeliveryConflictException.class)
    public ResponseEntity<ApiErrorDto> handleDeliveryConflict(final DeliveryConflictException exception) {
        log.debug("Delivery conflict: {}", exception.getMessage());
        return response(HttpStatus.CONFLICT, "DELIVERY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(EmptyFileException.class)
    public ResponseEntity<ApiErrorDto> handleEmptyFile(final EmptyFileException exception) {
        log.debug("Rejected empty file upload: {}", exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, "EMPTY_FILE", exception.getMessage());
    }

    @ExceptionHandler(FileReadException.class)
    public ResponseEntity<ApiErrorDto> handleFileRead(final FileReadException exception) {
        log.error("File read error: {}", exception.getMessage(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR",
                exception.getMessage());
    }

    /**
     * Catches an upload exceeding the configured {@code spring.servlet.multipart}
     * size limits (see {@code IMPORT_MAX_FILE_SIZE}/{@code IMPORT_MAX_REQUEST_SIZE}).
     * Without this handler, Spring/Tomcat's own
     * {@link MaxUploadSizeExceededException} falls through to
     * {@link #handleUnexpected} as a misleading {@code 500 Internal Server Error}
     * instead of a client-error status describing the actual problem.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorDto> handleMaxUploadSizeExceeded(final MaxUploadSizeExceededException exception) {
        log.debug("Rejected upload exceeding the configured size limit: {}", exception.getMessage());
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The uploaded file exceeds the maximum allowed size.");
    }

    /**
     * Catches {@code @Valid} failures on a {@code @ModelAttribute} (e.g. a required
     * query parameter missing or blank, such as {@code MovieController#lookup}'s
     * natural-key lookup) - Spring throws this instead of
     * {@link MethodArgumentNotValidException} when there is no adjacent
     * {@code BindingResult} parameter to carry the errors.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorDto> handleBindFailure(final BindException exception) {
        final String message = fieldErrorsMessage(exception);
        log.debug("Request binding/validation failed: {}", message);
        return response(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message);
    }

    /**
     * Catches {@code @Valid} failures on a {@code @RequestBody}, for symmetry with
     * {@link #handleBindFailure} - not currently exercised by any endpoint, but kept
     * so any future {@code @RequestBody} validation fails the same way.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception) {
        final String message = fieldErrorsMessage(exception);
        log.debug("Request body validation failed: {}", message);
        return response(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message);
    }

    /**
     * Catches a query/path parameter that cannot be converted to its target type (e.g.
     * a non-numeric {@code releaseYear} on {@code MovieController#lookup}) - without
     * this handler, the conversion failure is an unchecked exception that would
     * otherwise fall through to {@link #handleUnexpected} as a misleading
     * {@code 500 Internal Server Error}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDto> handleTypeMismatch(final MethodArgumentTypeMismatchException exception) {
        final String message = "Parameter '" + exception.getName() + "' has an invalid value.";
        log.debug("Request parameter type mismatch: {}", message);
        return response(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message);
    }

    /**
     * Catches anything not handled above, so the API always returns a consistent
     * {@link ApiErrorDto} body instead of Spring Boot's default error page. The full
     * exception is logged server-side; only a generic, safe message is returned to the
     * caller, since an unexpected exception's own message may leak internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpected(final Exception exception) {
        log.error("Unhandled exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    private static String fieldErrorsMessage(final BindingResult result) {
        return result.getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldErrorMessage)
                .collect(Collectors.joining("; "));
    }

    private static String fieldErrorMessage(final FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    private static ResponseEntity<ApiErrorDto> response(final HttpStatus status, final String code,
            final String message) {
        return ResponseEntity.status(status).body(new ApiErrorDto(code, message));
    }
}

