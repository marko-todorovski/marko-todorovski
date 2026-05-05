package com.example.aidiagramgenerator.service.generation.model;

import java.util.Objects;

/**
 * Represents an extracted relationship between two entities.
 * Captures the subject, object, and the action/verb connecting them.
 */
public class ExtractedRelationship {

    /** The source entity (subject). */
    private final String sourceEntity;

    /** The target entity (object). */
    private final String targetEntity;

    /** The relationship type or verb (e.g., "creates", "sends", "extends"). */
    private final String relationshipType;

    /** The original verb/phrase from the text. */
    private final String originalVerb;

    /** Cardinality on the source side (e.g., "1", "*", "0..1"). */
    private final String sourceCardinality;

    /** Cardinality on the target side. */
    private final String targetCardinality;

    /** Confidence score from the NLP model (0.0 to 1.0). */
    private final double confidence;

    public ExtractedRelationship(String sourceEntity, String targetEntity, String relationshipType,
                                  String originalVerb, String sourceCardinality, 
                                  String targetCardinality, double confidence) {
        this.sourceEntity = sourceEntity;
        this.targetEntity = targetEntity;
        this.relationshipType = relationshipType;
        this.originalVerb = originalVerb;
        this.sourceCardinality = sourceCardinality;
        this.targetCardinality = targetCardinality;
        this.confidence = confidence;
    }

    /**
     * Convenience constructor for basic relationships.
     */
    public ExtractedRelationship(String sourceEntity, String targetEntity, String relationshipType) {
        this(sourceEntity, targetEntity, relationshipType, relationshipType, null, null, 1.0);
    }

    public String getSourceEntity() {
        return sourceEntity;
    }

    public String getTargetEntity() {
        return targetEntity;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public String getOriginalVerb() {
        return originalVerb;
    }

    public String getSourceCardinality() {
        return sourceCardinality;
    }

    public String getTargetCardinality() {
        return targetCardinality;
    }

    public double getConfidence() {
        return confidence;
    }

    /**
     * Returns a formatted string representation for diagram generation.
     * Example: "User -> Order : creates"
     */
    public String toArrowNotation() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceEntity).append(" -> ").append(targetEntity);
        if (relationshipType != null && !relationshipType.isEmpty()) {
            sb.append(" : ").append(relationshipType);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractedRelationship that = (ExtractedRelationship) o;
        return Objects.equals(sourceEntity, that.sourceEntity) &&
               Objects.equals(targetEntity, that.targetEntity) &&
               Objects.equals(relationshipType, that.relationshipType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceEntity, targetEntity, relationshipType);
    }

    @Override
    public String toString() {
        return "ExtractedRelationship{" +
                "source='" + sourceEntity + '\'' +
                ", target='" + targetEntity + '\'' +
                ", type='" + relationshipType + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
