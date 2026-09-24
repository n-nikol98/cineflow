package com.nedko.cineflow.exception;

public class FileReadException extends RuntimeException {

    public FileReadException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
