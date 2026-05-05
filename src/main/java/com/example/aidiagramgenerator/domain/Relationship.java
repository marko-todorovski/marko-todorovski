package com.example.aidiagramgenerator.domain;

import java.util.Objects;

/**
 * Represents a relationship between two entities in the semantic model.
 * A relationship has a source, target, and type (e.g., "association", "inheritance").
 */
public final class Relationship {

    private final String source;
    private final String target;
    private final String type;

    /**
     * Creates a new Relationship with the specified source, target, and type.
     *
     * @param source the source entity name
     * @param target the target entity name
     * @param type   the relationship type
     * @throws IllegalArgumentException if source, target, or type is null or blank
     */
    public Relationship(String source, String target, String type) {
        validateSource(source);
        validateTarget(target);
        validateType(type);
        
        this.source = source.trim();
        this.target = target.trim();
        this.type = type.trim();
    }

    private void validateSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source cannot be null or blank");
        }
    }

    private void validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target cannot be null or blank");
        }
    }

    private void validateType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Relationship type cannot be null or blank");
        }
    }

    /**
     * Returns the source entity name.
     *
     * @return the source entity name
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the target entity name.
     *
     * @return the target entity name
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the relationship type.
     *
     * @return the relationship type
     */
    public String getType() {
        return type;
    }

    /**
     * Checks if this relationship connects the given entities (in either direction).
     *
     * @param entity1 the first entity name
     * @param entity2 the second entity name
     * @return true if this relationship connects the given entities
     */
    public boolean connects(String entity1, String entity2) {
        return (source.equals(entity1) && target.equals(entity2)) ||
               (source.equals(entity2) && target.equals(entity1));
    }

    /**
     * Checks if this relationship involves the given entity (as source or target).
     *
     * @param entityName the entity name to check
     * @return true if the entity is involved in this relationship
     */
    public boolean involves(String entityName) {
        return source.equals(entityName) || target.equals(entityName);
    }

    /**
     * Creates a reversed copy of this relationship (source and target swapped).
     *
     * @return a new Relationship with swapped source and target
     */
    public Relationship reverse() {
        return new Relationship(target, source, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Relationship that = (Relationship) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type);
    }

    @Override
    public String toString() {
        return "Relationship{" +
                "source='" + source + '\'' +
                ", target='" + target + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
