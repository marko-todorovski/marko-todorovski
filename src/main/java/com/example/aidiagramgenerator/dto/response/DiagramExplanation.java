package com.example.aidiagramgenerator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagramExplanation {

    private String typeReasoning;
    private int confidenceScore;
    private String confidenceLevel;
    private List<String> extractedEntities;
    private List<RelationshipInfo> detectedRelationships;
    private List<String> detectedActions;
    private String classificationSource;

    public DiagramExplanation() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTypeReasoning() {
        return typeReasoning;
    }

    public void setTypeReasoning(String typeReasoning) {
        this.typeReasoning = typeReasoning;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(int confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public List<String> getExtractedEntities() {
        return extractedEntities;
    }

    public void setExtractedEntities(List<String> extractedEntities) {
        this.extractedEntities = extractedEntities;
    }

    public List<RelationshipInfo> getDetectedRelationships() {
        return detectedRelationships;
    }

    public void setDetectedRelationships(List<RelationshipInfo> detectedRelationships) {
        this.detectedRelationships = detectedRelationships;
    }

    public List<String> getDetectedActions() {
        return detectedActions;
    }

    public void setDetectedActions(List<String> detectedActions) {
        this.detectedActions = detectedActions;
    }

    public String getClassificationSource() {
        return classificationSource;
    }

    public void setClassificationSource(String classificationSource) {
        this.classificationSource = classificationSource;
    }

    public record RelationshipInfo(String source, String target, String type) {
    }

    public static class Builder {
        private final DiagramExplanation explanation = new DiagramExplanation();

        public Builder typeReasoning(String typeReasoning) {
            explanation.setTypeReasoning(typeReasoning);
            return this;
        }

        public Builder confidenceScore(int confidenceScore) {
            explanation.setConfidenceScore(confidenceScore);
            return this;
        }

        public Builder confidenceLevel(String confidenceLevel) {
            explanation.setConfidenceLevel(confidenceLevel);
            return this;
        }

        public Builder extractedEntities(List<String> extractedEntities) {
            explanation.setExtractedEntities(extractedEntities);
            return this;
        }

        public Builder detectedRelationships(List<RelationshipInfo> detectedRelationships) {
            explanation.setDetectedRelationships(detectedRelationships);
            return this;
        }

        public Builder detectedActions(List<String> detectedActions) {
            explanation.setDetectedActions(detectedActions);
            return this;
        }

        public Builder classificationSource(String classificationSource) {
            explanation.setClassificationSource(classificationSource);
            return this;
        }

        public DiagramExplanation build() {
            return explanation;
        }
    }
}
