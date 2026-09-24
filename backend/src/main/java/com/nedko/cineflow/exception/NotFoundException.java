package com.nedko.cineflow.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(final String message) {
        super(message);
    }

    public static NotFoundException forEntity(final String entityName, final Object id) {
        return new NotFoundException(entityName + " " + id + " was not found.");
    }
}
