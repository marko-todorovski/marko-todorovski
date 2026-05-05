package com.example.aidiagramgenerator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for evaluation response data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationResponse {

    private Long id;
    private UUID diagramId;
    private Integer clarityScore;
    private Integer correctnessScore;
    private Integer usefulnessScore;
    private Double averageScore;
    private String comment;
    private LocalDateTime evaluatedAt;
    private String message;

    /**
     * Default constructor for serialization.
     */
    public EvaluationResponse() {
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public void setDiagramId(UUID diagramId) {
        this.diagramId = diagramId;
    }

    public Integer getClarityScore() {
        return clarityScore;
    }

    public void setClarityScore(Integer clarityScore) {
        this.clarityScore = clarityScore;
    }

    public Integer getCorrectnessScore() {
        return correctnessScore;
    }

    public void setCorrectnessScore(Integer correctnessScore) {
        this.correctnessScore = correctnessScore;
    }

    public Integer getUsefulnessScore() {
        return usefulnessScore;
    }

    public void setUsefulnessScore(Integer usefulnessScore) {
        this.usefulnessScore = usefulnessScore;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Builder for EvaluationResponse.
     */
    public static class Builder {
        private final EvaluationResponse response = new EvaluationResponse();

        public Builder id(Long id) {
            response.setId(id);
            return this;
        }

        public Builder diagramId(UUID diagramId) {
            response.setDiagramId(diagramId);
            return this;
        }

        public Builder clarityScore(Integer clarityScore) {
            response.setClarityScore(clarityScore);
            return this;
        }

        public Builder correctnessScore(Integer correctnessScore) {
            response.setCorrectnessScore(correctnessScore);
            return this;
        }

        public Builder usefulnessScore(Integer usefulnessScore) {
            response.setUsefulnessScore(usefulnessScore);
            return this;
        }

        public Builder averageScore(Double averageScore) {
            response.setAverageScore(averageScore);
            return this;
        }

        public Builder comment(String comment) {
            response.setComment(comment);
            return this;
        }

        public Builder evaluatedAt(LocalDateTime evaluatedAt) {
            response.setEvaluatedAt(evaluatedAt);
            return this;
        }

        public Builder message(String message) {
            response.setMessage(message);
            return this;
        }

        public EvaluationResponse build() {
            return response;
        }
    }
}
