package com.example.aidiagramgenerator.domain;

import java.util.Objects;

/**
 * The intermediate result of classifying a {@link SemanticModel} into a diagram type.
 *
 * <p>Captures the winning diagram type, the numeric confidence score (0–100),
 * and a human-readable explanation of why this type was chosen.
 *
 * <p>Instances are created by the classification layers inside
 * {@code DiagramClassificationServiceImpl} and subsequently converted to a
 * {@link ClassificationResponse} that includes the decision and user message.
 */
public final class ClassificationResult {

    private final DiagramType type;
    private final double confidence;
    private final String explanation;

    /**
     * Creates a new ClassificationResult.
     *
     * @param type        the classified diagram type (must not be null)
     * @param confidence  confidence score in the range [0, 100]
     * @param explanation a concise explanation of why this type was chosen (must not be null)
     * @throws IllegalArgumentException if type or explanation is null, or confidence is out of range
     */
    public ClassificationResult(DiagramType type, double confidence, String explanation) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be between 0 and 100, got: " + confidence);
        }
        this.type = type;
        this.confidence = confidence;
        this.explanation = explanation;
    }

    /**
     * Returns the classified diagram type.
     */
    public DiagramType getType() {
        return type;
    }

    /**
     * Returns the confidence score in the range [0, 100].
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Returns a human-readable explanation for this classification.
     */
    public String getExplanation() {
        return explanation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassificationResult that)) return false;
        return Double.compare(that.confidence, confidence) == 0
                && type == that.type
                && Objects.equals(explanation, that.explanation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, confidence, explanation);
    }

    @Override
    public String toString() {
        return "ClassificationResult{type=" + type
                + ", confidence=" + String.format("%.1f", confidence)
                + "%, explanation='" + explanation + "'}";
    }
}
