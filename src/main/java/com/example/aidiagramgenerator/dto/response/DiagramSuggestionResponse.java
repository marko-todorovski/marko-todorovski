package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a diagram type suggestion with confidence scoring.
 * Returned when the system needs user confirmation before proceeding with generation.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagramSuggestionResponse {

    private DiagramType suggestedDiagramType;
    private int confidenceScore;
    private String reasoningMessage;
    private boolean confirmationRequired;

    public DiagramSuggestionResponse() {
    }

    public DiagramSuggestionResponse(DiagramType suggestedDiagramType, int confidenceScore,
                                     String reasoningMessage, boolean confirmationRequired) {
        this.suggestedDiagramType = suggestedDiagramType;
        this.confidenceScore = confidenceScore;
        this.reasoningMessage = reasoningMessage;
        this.confirmationRequired = confirmationRequired;
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DiagramType suggestedDiagramType;
        private int confidenceScore;
        private String reasoningMessage;
        private boolean confirmationRequired;

        public Builder suggestedDiagramType(DiagramType suggestedDiagramType) {
            this.suggestedDiagramType = suggestedDiagramType;
            return this;
        }

        public Builder confidenceScore(int confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder reasoningMessage(String reasoningMessage) {
            this.reasoningMessage = reasoningMessage;
            return this;
        }

        public Builder confirmationRequired(boolean confirmationRequired) {
            this.confirmationRequired = confirmationRequired;
            return this;
        }

        public DiagramSuggestionResponse build() {
            return new DiagramSuggestionResponse(
                    suggestedDiagramType, confidenceScore, reasoningMessage, confirmationRequired);
        }
    }

    // ─── Getters and Setters ──────────────────────────────────────────────────

    public DiagramType getSuggestedDiagramType() {
        return suggestedDiagramType;
    }

    public void setSuggestedDiagramType(DiagramType suggestedDiagramType) {
        this.suggestedDiagramType = suggestedDiagramType;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(int confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getReasoningMessage() {
        return reasoningMessage;
    }

    public void setReasoningMessage(String reasoningMessage) {
        this.reasoningMessage = reasoningMessage;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public void setConfirmationRequired(boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
    }

    @Override
    public String toString() {
        return "DiagramSuggestionResponse{" +
                "suggestedDiagramType=" + suggestedDiagramType +
                ", confidenceScore=" + confidenceScore +
                ", confirmationRequired=" + confirmationRequired +
                '}';
    }
}
