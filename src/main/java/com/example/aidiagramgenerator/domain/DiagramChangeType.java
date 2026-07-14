package com.example.aidiagramgenerator.domain;

/**
 * Describes why a diagram version was created.
 */
public enum DiagramChangeType {
    GENERATED,
    EDITED,
    AI_MODIFIED,
    RESTORED,
    REPAIRED
}
