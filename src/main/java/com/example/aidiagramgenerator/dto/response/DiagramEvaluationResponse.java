package com.example.aidiagramgenerator.dto.response;

import java.util.UUID;

/**
 * Response DTO returned after a diagram evaluation is submitted.
 */
public class DiagramEvaluationResponse {

    private UUID diagramId;
    private int clarityScore;
    private int correctnessScore;
    private int usefulnessScore;
    private double averageScore;

    public DiagramEvaluationResponse() {
    }

    public DiagramEvaluationResponse(UUID diagramId, int clarityScore, int correctnessScore,
                                     int usefulnessScore, double averageScore) {
        this.diagramId = diagramId;
        this.clarityScore = clarityScore;
        this.correctnessScore = correctnessScore;
        this.usefulnessScore = usefulnessScore;
        this.averageScore = averageScore;
    }

    public UUID getDiagramId() { return diagramId; }
    public void setDiagramId(UUID diagramId) { this.diagramId = diagramId; }

    public int getClarityScore() { return clarityScore; }
    public void setClarityScore(int clarityScore) { this.clarityScore = clarityScore; }

    public int getCorrectnessScore() { return correctnessScore; }
    public void setCorrectnessScore(int correctnessScore) { this.correctnessScore = correctnessScore; }

    public int getUsefulnessScore() { return usefulnessScore; }
    public void setUsefulnessScore(int usefulnessScore) { this.usefulnessScore = usefulnessScore; }

    public double getAverageScore() { return averageScore; }
    public void setAverageScore(double averageScore) { this.averageScore = averageScore; }
}
