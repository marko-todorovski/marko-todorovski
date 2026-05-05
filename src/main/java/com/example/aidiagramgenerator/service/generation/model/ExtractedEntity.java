package com.example.aidiagramgenerator.service.generation.model;

import java.util.Objects;

/**
 * Represents an extracted entity from natural language text.
 * Supports both single-word and multi-word entities (e.g., "OrderItem", "Payment Service").
 */
public class ExtractedEntity {

    /** The entity name, normalized to PascalCase. */
    private final String name;

    /** Original text as it appeared in the input. */
    private final String originalText;

    /** Character offset where the entity starts in the source text. */
    private final int startOffset;

    /** Character offset where the entity ends in the source text. */
    private final int endOffset;

    /** Entity type from NER (e.g., ORGANIZATION, PERSON, MISC) or "CONCEPT" for domain entities. */
    private final String entityType;

    /** Confidence score from the NLP model (0.0 to 1.0). */
    private final double confidence;

    public ExtractedEntity(String name, String originalText, int startOffset, int endOffset, 
                           String entityType, double confidence) {
        this.name = name;
        this.originalText = originalText;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.entityType = entityType;
        this.confidence = confidence;
    }

    /**
     * Convenience constructor for entities without offset information.
     */
    public ExtractedEntity(String name, String entityType) {
        this(name, name, -1, -1, entityType, 1.0);
    }

    public String getName() {
        return name;
    }

    public String getOriginalText() {
        return originalText;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public String getEntityType() {
        return entityType;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractedEntity that = (ExtractedEntity) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "ExtractedEntity{" +
                "name='" + name + '\'' +
                ", entityType='" + entityType + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
