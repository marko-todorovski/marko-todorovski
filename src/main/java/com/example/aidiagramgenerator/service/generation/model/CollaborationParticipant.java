package com.example.aidiagramgenerator.service.generation.model;

import java.util.Objects;

/**
 * A structured representation of a single participant extracted from a
 * collaboration-diagram description.
 *
 * <p>Participants are classified into three mutually exclusive types:
 * <ul>
 *   <li>{@link ParticipantType#PARTICIPANT} – human actors such as {@code User},
 *       {@code Customer}, or {@code Admin}</li>
 *   <li>{@link ParticipantType#COMPONENT} – technical systems such as
 *       {@code WebServer}, {@code Database}, or {@code ApiGateway}</li>
 *   <li>{@link ParticipantType#OBJECT} – domain objects that are neither actors nor
 *       systems, such as {@code ATM}, {@code Bank}, or {@code Order}</li>
 * </ul>
 *
 * <p>The {@code confidence} field is a [0.0, 1.0] score that reflects how certain the
 * classifier is about the type assignment — not about whether the name was found.
 */
public final class CollaborationParticipant {

    /**
     * The three semantic roles a collaboration participant can fulfil.
     */
    public enum ParticipantType {
        /** A human actor interacting with the system (e.g. User, Customer, Admin). */
        PARTICIPANT,
        /** A technical system, service, or infrastructure element (e.g. WebServer, ApiGateway). */
        COMPONENT,
        /** A domain object that is neither an actor nor a system component (e.g. ATM, Bank). */
        OBJECT
    }

    /** Normalised PascalCase identifier used in PlantUML output. */
    private final String name;

    /** The semantic role of this participant. */
    private final ParticipantType type;

    /**
     * Classifier confidence for the {@link #type} assignment in the range [0.0, 1.0].
     * Does <em>not</em> indicate detection confidence (the name was found in the text).
     */
    private final double confidence;

    public CollaborationParticipant(String name, ParticipantType type, double confidence) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Participant name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Participant type must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be in [0.0, 1.0]");
        }
        this.name = name.trim();
        this.type = type;
        this.confidence = confidence;
    }

    /** @return the PascalCase name of this participant */
    public String getName() {
        return name;
    }

    /** @return the semantic role ({@link ParticipantType}) of this participant */
    public ParticipantType getType() {
        return type;
    }

    /**
     * Returns the classifier's confidence in the {@link #type} assignment.
     *
     * @return a value in [0.0, 1.0]
     */
    public double getConfidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollaborationParticipant that = (CollaborationParticipant) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "CollaborationParticipant{name='" + name + "', type=" + type +
               ", confidence=" + confidence + '}';
    }
}
