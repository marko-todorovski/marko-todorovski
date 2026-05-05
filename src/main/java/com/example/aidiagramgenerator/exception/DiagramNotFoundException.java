package com.example.aidiagramgenerator.exception;

/**
 * Exception thrown when a requested diagram is not found.
 */
public class DiagramNotFoundException extends RuntimeException {

    public DiagramNotFoundException(String message) {
        super(message);
    }

    public DiagramNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
