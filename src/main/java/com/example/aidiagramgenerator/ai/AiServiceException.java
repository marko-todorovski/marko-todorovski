package com.example.aidiagramgenerator.ai;

/**
 * Exception thrown when an AI provider fails to generate a response.
 * 
 * <p>This exception wraps provider-specific errors (API failures, timeouts,
 * rate limits, model unavailability) into a unified exception type.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public class AiServiceException extends RuntimeException {

    /**
     * Constructs a new AI service exception with the specified message.
     * 
     * @param message the detail message
     */
    public AiServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a new AI service exception with the specified message and cause.
     * 
     * @param message the detail message
     * @param cause the underlying cause
     */
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
