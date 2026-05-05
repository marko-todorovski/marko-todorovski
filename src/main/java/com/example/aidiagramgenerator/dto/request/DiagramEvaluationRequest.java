package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for evaluating a generated diagram.
 */
public class DiagramEvaluationRequest {

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

    public DiagramEvaluationRequest() {
    }

    public DiagramEvaluationRequest(Integer clarityScore, Integer correctnessScore, Integer usefulnessScore) {
        this.clarityScore = clarityScore;
        this.correctnessScore = correctnessScore;
        this.usefulnessScore = usefulnessScore;
    }

    public Integer getClarityScore() { return clarityScore; }
    public void setClarityScore(Integer clarityScore) { this.clarityScore = clarityScore; }

    public Integer getCorrectnessScore() { return correctnessScore; }
    public void setCorrectnessScore(Integer correctnessScore) { this.correctnessScore = correctnessScore; }

    public Integer getUsefulnessScore() { return usefulnessScore; }
    public void setUsefulnessScore(Integer usefulnessScore) { this.usefulnessScore = usefulnessScore; }
}
