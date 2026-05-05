package com.example.aidiagramgenerator.service.render;

/**
 * Exception thrown when diagram rendering fails.
 * This can occur due to invalid PlantUML syntax, rendering engine errors,
 * or resource limitations.
 */
public class DiagramRenderingException extends RuntimeException {

    private final String plantUmlCode;
    private final RenderingErrorType errorType;

    /**
     * Enumeration of rendering error types for better error handling.
     */
    public enum RenderingErrorType {
        INVALID_SYNTAX("Invalid PlantUML syntax"),
        RENDERING_ERROR("Error during rendering process"),
        OUTPUT_ERROR("Error writing output"),
        TIMEOUT("Rendering operation timed out"),
        UNKNOWN("Unknown rendering error");

        private final String description;

        RenderingErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Creates a new DiagramRenderingException.
     *
     * @param message      the error message
     * @param plantUmlCode the PlantUML code that failed to render
     * @param errorType    the type of rendering error
     */
    public DiagramRenderingException(String message, String plantUmlCode, RenderingErrorType errorType) {
        super(message);
        this.plantUmlCode = plantUmlCode;
        this.errorType = errorType;
    }

    /**
     * Creates a new DiagramRenderingException with a cause.
     *
     * @param message      the error message
     * @param cause        the underlying cause
     * @param plantUmlCode the PlantUML code that failed to render
     * @param errorType    the type of rendering error
     */
    public DiagramRenderingException(String message, Throwable cause, String plantUmlCode, RenderingErrorType errorType) {
        super(message, cause);
        this.plantUmlCode = plantUmlCode;
        this.errorType = errorType;
    }

    /**
     * Creates a new DiagramRenderingException with default error type.
     *
     * @param message      the error message
     * @param plantUmlCode the PlantUML code that failed to render
     */
    public DiagramRenderingException(String message, String plantUmlCode) {
        this(message, plantUmlCode, RenderingErrorType.UNKNOWN);
    }

    /**
     * Creates a new DiagramRenderingException with cause and default error type.
     *
     * @param message      the error message
     * @param cause        the underlying cause
     * @param plantUmlCode the PlantUML code that failed to render
     */
    public DiagramRenderingException(String message, Throwable cause, String plantUmlCode) {
        this(message, cause, plantUmlCode, RenderingErrorType.UNKNOWN);
    }

    /**
     * Returns the PlantUML code that failed to render.
     *
     * @return the PlantUML code
     */
    public String getPlantUmlCode() {
        return plantUmlCode;
    }

    /**
     * Returns the type of rendering error.
     *
     * @return the error type
     */
    public RenderingErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns a truncated version of the PlantUML code for logging purposes.
     *
     * @param maxLength the maximum length
     * @return truncated PlantUML code
     */
    public String getTruncatedPlantUmlCode(int maxLength) {
        if (plantUmlCode == null) {
            return null;
        }
        if (plantUmlCode.length() <= maxLength) {
            return plantUmlCode;
        }
        return plantUmlCode.substring(0, maxLength) + "... [truncated]";
    }

    @Override
    public String toString() {
        return "DiagramRenderingException{" +
                "message='" + getMessage() + '\'' +
                ", errorType=" + errorType +
                ", plantUmlCodeLength=" + (plantUmlCode != null ? plantUmlCode.length() : 0) +
                '}';
    }
}
