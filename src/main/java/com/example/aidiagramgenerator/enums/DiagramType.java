package com.example.aidiagramgenerator.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of supported diagram types.
 * New diagram types can be added here and paired with a matching DiagramGenerator.
 */
public enum DiagramType {
    CLASS("class"),
    SEQUENCE("sequence"),
    ER("er"),
    ARCHITECTURE("architecture"),
    C4("c4"),
    OBJECT("object"),
    ACTIVITY("activity"),
    STATE("state"),
    COLLABORATION("collaboration"),
    MICROSERVICES("microservices");

    private final String value;

    DiagramType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Case-insensitive JSON deserialization.
     * Accepts both the enum constant name (e.g. "CLASS") and the value string (e.g. "class").
     */
    @JsonCreator
    public static DiagramType fromValue(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Diagram type must not be blank");
        }
        String normalized = input.trim().toLowerCase();
        for (DiagramType type : values()) {
            if (type.value.equals(normalized) || type.name().equalsIgnoreCase(input.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unknown diagram type: '" + input + "'. Accepted values: " +
                java.util.Arrays.stream(values()).map(DiagramType::getValue).collect(java.util.stream.Collectors.joining(", ")));
    }

    @Override
    public String toString() {
        return this.value;
    }
}
