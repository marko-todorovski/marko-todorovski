package com.example.aidiagramgenerator.enums;

/**
 * Enumeration of supported input types.
 * Used in the Diagram JPA entity and API responses to indicate
 * how the input was provided.
 */
public enum InputType {
    /** Natural language text input (e.g., "Create a class diagram with User and Order") */
    NATURAL_LANGUAGE,
    
    /** Plain text input (alias for NATURAL_LANGUAGE for backward compatibility) */
    TEXT,
    
    /** XML document input */
    XML,
    
    /** URL input pointing to external resources */
    URL,

    /** PDF document input */
    PDF;
    
    /**
     * Detects the input type based on content analysis.
     * 
     * @param content the raw input content
     * @return the detected InputType
     */
    public static InputType detect(String content) {
        if (content == null || content.isBlank()) {
            return NATURAL_LANGUAGE;
        }
        
        String trimmed = content.trim();
        
        // Check for URL pattern
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return URL;
        }
        
        // Check for XML pattern
        if (trimmed.startsWith("<") && trimmed.contains(">")) {
            return XML;
        }
        
        // Default to natural language
        return NATURAL_LANGUAGE;
    }
}
