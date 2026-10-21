package com.timedeal.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Long id) {
        super(resource + " not found: " + id);
    }
}
