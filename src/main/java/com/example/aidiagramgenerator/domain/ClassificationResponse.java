package com.example.aidiagramgenerator.domain;

import java.util.Objects;

/**
 * The structured response returned by the confidence-based classification pipeline.
 *
 * <p>Summarises the outcome in three layers:
 * <ol>
 *   <li>{@link #decision} — what the system should do next ({@code AUTO / SUGGEST / CLARIFY})</li>
 *   <li>{@link #message}  — a user-facing message appropriate for the decision tier</li>
 *   <li>{@link #diagramType} / {@link #confidence} — the underlying classification result</li>
 * </ol>
 *
 * <p>Decision thresholds:
 * <ul>
 *   <li>confidence ≥ 70 → {@link ClassificationDecision#AUTO}</li>
 *   <li>confidence 40–69 → {@link ClassificationDecision#SUGGEST}</li>
 *   <li>confidence &lt; 40 → {@link ClassificationDecision#CLARIFY}</li>
 * </ul>
 */
public final class ClassificationResponse {

    private final ClassificationDecision decision;
    private final String message;
    private final DiagramType diagramType;
    private final double confidence;

    private ClassificationResponse(Builder builder) {
        this.decision = builder.decision;
        this.message = builder.message;
        this.diagramType = builder.diagramType;
        this.confidence = builder.confidence;
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    /**
     * Creates an AUTO response (confidence ≥ 70).
     */
    public static ClassificationResponse auto(DiagramType type, double confidence, String message) {
        return new Builder()
                .decision(ClassificationDecision.AUTO)
                .diagramType(type)
                .confidence(confidence)
                .message(message)
                .build();
    }

    /**
     * Creates a SUGGEST response (confidence 40–69).
     */
    public static ClassificationResponse suggest(DiagramType type, double confidence, String message) {
        return new Builder()
                .decision(ClassificationDecision.SUGGEST)
                .diagramType(type)
                .confidence(confidence)
                .message(message)
                .build();
    }

    /**
     * Creates a CLARIFY response (confidence &lt; 40).
     */
    public static ClassificationResponse clarify(DiagramType type, double confidence, String message) {
        return new Builder()
                .decision(ClassificationDecision.CLARIFY)
                .diagramType(type)
                .confidence(confidence)
                .message(message)
                .build();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ClassificationDecision getDecision() {
        return decision;
    }

    public String getMessage() {
        return message;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public double getConfidence() {
        return confidence;
    }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassificationResponse that)) return false;
        return Double.compare(that.confidence, confidence) == 0
                && decision == that.decision
                && Objects.equals(message, that.message)
                && diagramType == that.diagramType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, message, diagramType, confidence);
    }

    @Override
    public String toString() {
        return "ClassificationResponse{decision=" + decision
                + ", diagramType=" + diagramType.name()
                + ", confidence=" + String.format("%.1f", confidence)
                + "%, message='" + message + "'}";
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private ClassificationDecision decision;
        private String message;
        private DiagramType diagramType;
        private double confidence;

        public Builder decision(ClassificationDecision decision) {
            this.decision = decision;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder diagramType(DiagramType diagramType) {
            this.diagramType = diagramType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public ClassificationResponse build() {
            Objects.requireNonNull(decision, "decision must not be null");
            Objects.requireNonNull(diagramType, "diagramType must not be null");
            Objects.requireNonNull(message, "message must not be null");
            return new ClassificationResponse(this);
        }
    }
}
