package com.example.aidiagramgenerator.service.generation.model;

import java.util.Objects;

/**
 * A directed connection between two collaboration diagram participants,
 * optionally annotated with a message label.
 *
 * <p>Connections are deduplicated by the {@code (source, target)} pair — the label
 * is <em>not</em> part of equality so that two interactions between the same objects
 * count as one connection in the connection list.
 *
 * <p>The {@code label} field contains the first detected interaction verb or message
 * text between the two objects (e.g. {@code "request"}, {@code "query"}).
 * It may be {@code null} when no label was found.
 */
public final class CollaborationConnection {

    /** Name of the source participant (PascalCase, PlantUML-safe). */
    private final String source;

    /** Name of the target participant (PascalCase, PlantUML-safe). */
    private final String target;

    /**
     * Human-readable label for the interaction (e.g. {@code "1. request"}).
     * May be {@code null} when the connection was inferred without an explicit label.
     */
    private final String label;

    public CollaborationConnection(String source, String target, String label) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Connection source must not be blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Connection target must not be blank");
        }
        this.source = source.trim();
        this.target = target.trim();
        this.label  = (label == null || label.isBlank()) ? null : label.trim();
    }

    /** Convenience constructor for label-free connections. */
    public CollaborationConnection(String source, String target) {
        this(source, target, null);
    }

    /** @return the source participant name */
    public String getSource() {
        return source;
    }

    /** @return the target participant name */
    public String getTarget() {
        return target;
    }

    /**
     * @return the interaction label, or {@code null} if none was detected
     */
    public String getLabel() {
        return label;
    }

    /**
     * Equality is based solely on {@code (source, target)} — the label is ignored.
     * This ensures a single directed edge is counted once even if multiple messages
     * flow along it.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollaborationConnection that = (CollaborationConnection) o;
        return Objects.equals(source, that.source) && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }

    @Override
    public String toString() {
        return label != null
                ? source + " --> " + target + " : " + label
                : source + " --> " + target;
    }
}
