package com.example.aidiagramgenerator.exception;

/**
 * Custom exception for diagram generation errors
 */
public class DiagramGenerationException extends RuntimeException {

    public DiagramGenerationException(String message) {
        super(message);
    }

    public DiagramGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
