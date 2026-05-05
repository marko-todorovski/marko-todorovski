package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for submitting a diagram evaluation.
 * All scores are required and must be between 1 and 5.
 */
public class EvaluationRequest {

    @NotNull(message = "Diagram ID is required")
    private UUID diagramId;

    @NotNull(message = "Clarity score is required")
    @Min(value = 1, message = "Clarity score must be at least 1")
    @Max(value = 5, message = "Clarity score must be at most 5")
    private Integer clarityScore;

    @NotNull(message = "Correctness score is required")
    @Min(value = 1, message = "Correctness score must be at least 1")
    @Max(value = 5, message = "Correctness score must be at most 5")
    private Integer correctnessScore;

    @NotNull(message = "Usefulness score is required")
    @Min(value = 1, message = "Usefulness score must be at least 1")
    @Max(value = 5, message = "Usefulness score must be at most 5")
    private Integer usefulnessScore;

    private String comment;

    /**
     * Default constructor for deserialization.
     */
    public EvaluationRequest() {
    }

    /**
     * Creates a new EvaluationRequest with the specified values.
     */
    public EvaluationRequest(UUID diagramId, Integer clarityScore, Integer correctnessScore, 
                             Integer usefulnessScore, String comment) {
        this.diagramId = diagramId;
        this.clarityScore = clarityScore;
        this.correctnessScore = correctnessScore;
        this.usefulnessScore = usefulnessScore;
        this.comment = comment;
    }

    // Getters and Setters

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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return "EvaluationRequest{" +
                "diagramId=" + diagramId +
                ", clarityScore=" + clarityScore +
                ", correctnessScore=" + correctnessScore +
                ", usefulnessScore=" + usefulnessScore +
                ", hasComment=" + (comment != null && !comment.isEmpty()) +
                '}';
    }
}
