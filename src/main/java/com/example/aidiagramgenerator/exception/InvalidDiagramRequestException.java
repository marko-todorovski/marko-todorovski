package com.example.aidiagramgenerator.exception;

/**
 * Exception thrown when a diagram request is too vague or ambiguous
 * to determine the appropriate diagram type.
 * 
 * <p>This exception signals that the user should provide more descriptive input
 * about the system structure or interactions they want to visualize.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public class InvalidDiagramRequestException extends RuntimeException {

    public InvalidDiagramRequestException(String message) {
        super(message);
    }

    public InvalidDiagramRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
