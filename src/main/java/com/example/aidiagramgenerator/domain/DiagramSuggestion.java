package com.example.aidiagramgenerator.domain;

/**
 * Domain model representing a diagram type suggestion with confidence scoring.
 * Used internally by the suggestion service to communicate results to the controller.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public class DiagramSuggestion {

    private final DiagramType suggestedDiagramType;
    private final int confidenceScore;
    private final String reasoningMessage;
    private final ClassificationSource source;

    /**
     * Indicates which classification layer produced the suggestion.
     */
    public enum ClassificationSource {
        EXPLICIT_MENTION,
        AI_PROVIDER,
        SEMANTIC_PATTERN,
        KEYWORD_SCORING
    }

    public DiagramSuggestion(DiagramType suggestedDiagramType, int confidenceScore,
                             String reasoningMessage, ClassificationSource source) {
        this.suggestedDiagramType = suggestedDiagramType;
        this.confidenceScore = Math.max(0, Math.min(100, confidenceScore));
        this.reasoningMessage = reasoningMessage;
        this.source = source;
    }

    public DiagramType getSuggestedDiagramType() {
        return suggestedDiagramType;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public String getReasoningMessage() {
        return reasoningMessage;
    }

    public ClassificationSource getSource() {
        return source;
    }

    /**
     * Returns true if the confidence is high enough to proceed without confirmation.
     *
     * @param threshold the minimum confidence score (0-100)
     * @return true if confidence meets the threshold
     */
    public boolean isConfident(int threshold) {
        return confidenceScore >= threshold;
    }

    @Override
    public String toString() {
        return "DiagramSuggestion{" +
                "type=" + suggestedDiagramType +
                ", confidence=" + confidenceScore +
                ", source=" + source +
                '}';
    }
}
