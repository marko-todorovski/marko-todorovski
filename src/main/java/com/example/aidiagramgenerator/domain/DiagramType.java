package com.example.aidiagramgenerator.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enumeration of supported software engineering diagram types.
 * Each type corresponds to a specific UML or architectural diagram category.
 */
public enum DiagramType {
    
    CLASS("class", "Class Diagram"),
    ER("er", "Entity-Relationship Diagram"),
    SEQUENCE("sequence", "Sequence Diagram"),
    USE_CASE("use_case", "Use Case Diagram"),
    COMPONENT("component", "Component Diagram"),
    DEPLOYMENT("deployment", "Deployment Diagram"),
    OBJECT("object", "Object Diagram"),
    ACTIVITY("activity", "Activity Diagram"),
    STATE("state", "State Diagram"),
    COLLABORATION("collaboration", "Collaboration Diagram"),
    MICROSERVICES("microservices", "Microservices Diagram");

    private final String code;
    private final String displayName;

    DiagramType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Returns the short code identifier for this diagram type.
     * @return the code string
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable display name for this diagram type.
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Finds a DiagramType by its code, case-insensitive.
     * @param code the code to search for
     * @return the matching DiagramType
     * @throws IllegalArgumentException if no matching type is found
     */
    public static DiagramType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Diagram type code cannot be null or blank");
        }
        for (DiagramType type : values()) {
            String normalized = code.trim();
            if (type.name().equalsIgnoreCase(normalized)
                    || type.code.equalsIgnoreCase(normalized)
                    || type.displayName.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown diagram type code: " + code + ". Accepted codes: " +
                java.util.Arrays.stream(values()).map(DiagramType::getCode).collect(java.util.stream.Collectors.joining(", ")));
    }

    @JsonCreator
    public static DiagramType fromJson(String value) {
        return fromCode(value);
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
