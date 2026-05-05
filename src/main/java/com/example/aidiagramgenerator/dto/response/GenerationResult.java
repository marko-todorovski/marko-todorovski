package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for diagram generation results.
 * Contains the generated diagram details including PlantUML code and metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationResult {

    private UUID id;
    private DiagramType diagramType;
    private String plantUmlCode;
    private String pngBase64;
    private String svgContent;
    private int entityCount;
    private int relationshipCount;
    private int actionCount;
    private String modelUsed;
    private LocalDateTime generatedAt;
    private String message;
    private Integer confidenceScore;
    private Boolean confirmationRequired;
    private DiagramExplanation explanation;
    private String generationMode;
    private String decision;

    /**
     * Default constructor for serialization.
     */
    public GenerationResult() {
    }

    /**
     * Builder for GenerationResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    public String getPlantUmlCode() {
        return plantUmlCode;
    }

    public void setPlantUmlCode(String plantUmlCode) {
        this.plantUmlCode = plantUmlCode;
    }

    public String getPngBase64() {
        return pngBase64;
    }

    public void setPngBase64(String pngBase64) {
        this.pngBase64 = pngBase64;
    }

    public String getSvgContent() {
        return svgContent;
    }

    public void setSvgContent(String svgContent) {
        this.svgContent = svgContent;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public void setEntityCount(int entityCount) {
        this.entityCount = entityCount;
    }

    public int getRelationshipCount() {
        return relationshipCount;
    }

    public void setRelationshipCount(int relationshipCount) {
        this.relationshipCount = relationshipCount;
    }

    public int getActionCount() {
        return actionCount;
    }

    public void setActionCount(int actionCount) {
        this.actionCount = actionCount;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Integer confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public void setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
    }

    public DiagramExplanation getExplanation() {
        return explanation;
    }

    public void setExplanation(DiagramExplanation explanation) {
        this.explanation = explanation;
    }

    public String getGenerationMode() {
        return generationMode;
    }

    public void setGenerationMode(String generationMode) {
        this.generationMode = generationMode;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    /**
     * Builder class for GenerationResult.
     */
    public static class Builder {
        private final GenerationResult result = new GenerationResult();

        public Builder id(UUID id) {
            result.setId(id);
            return this;
        }

        public Builder diagramType(DiagramType diagramType) {
            result.setDiagramType(diagramType);
            return this;
        }

        public Builder plantUmlCode(String plantUmlCode) {
            result.setPlantUmlCode(plantUmlCode);
            return this;
        }

        public Builder pngBase64(String pngBase64) {
            result.setPngBase64(pngBase64);
            return this;
        }

        public Builder svgContent(String svgContent) {
            result.setSvgContent(svgContent);
            return this;
        }

        public Builder entityCount(int entityCount) {
            result.setEntityCount(entityCount);
            return this;
        }

        public Builder relationshipCount(int relationshipCount) {
            result.setRelationshipCount(relationshipCount);
            return this;
        }

        public Builder actionCount(int actionCount) {
            result.setActionCount(actionCount);
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            result.setModelUsed(modelUsed);
            return this;
        }

        public Builder generatedAt(LocalDateTime generatedAt) {
            result.setGeneratedAt(generatedAt);
            return this;
        }

        public Builder message(String message) {
            result.setMessage(message);
            return this;
        }

        public Builder confidenceScore(Integer confidenceScore) {
            result.setConfidenceScore(confidenceScore);
            return this;
        }

        public Builder confirmationRequired(Boolean confirmationRequired) {
            result.setConfirmationRequired(confirmationRequired);
            return this;
        }

        public Builder explanation(DiagramExplanation explanation) {
            result.setExplanation(explanation);
            return this;
        }

        public Builder generationMode(String generationMode) {
            result.setGenerationMode(generationMode);
            return this;
        }

        public Builder decision(String decision) {
            result.setDecision(decision);
            return this;
        }

        public GenerationResult build() {
            return result;
        }
    }
}
