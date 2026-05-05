package com.example.aidiagramgenerator.dto.response;

import java.util.UUID;

/**
 * Response DTO containing aggregated metrics for a specific diagram's evaluations.
 */
public class DiagramMetricsResponse {

    private UUID diagramId;
    private double averageClarity;
    private double averageCorrectness;
    private double averageUsefulness;
    private long totalEvaluations;

    public DiagramMetricsResponse() {
    }

    public DiagramMetricsResponse(UUID diagramId, double averageClarity, double averageCorrectness,
                                  double averageUsefulness, long totalEvaluations) {
        this.diagramId = diagramId;
        this.averageClarity = averageClarity;
        this.averageCorrectness = averageCorrectness;
        this.averageUsefulness = averageUsefulness;
        this.totalEvaluations = totalEvaluations;
    }

    public UUID getDiagramId() { return diagramId; }
    public void setDiagramId(UUID diagramId) { this.diagramId = diagramId; }

    public double getAverageClarity() { return averageClarity; }
    public void setAverageClarity(double averageClarity) { this.averageClarity = averageClarity; }

    public double getAverageCorrectness() { return averageCorrectness; }
    public void setAverageCorrectness(double averageCorrectness) { this.averageCorrectness = averageCorrectness; }

    public double getAverageUsefulness() { return averageUsefulness; }
    public void setAverageUsefulness(double averageUsefulness) { this.averageUsefulness = averageUsefulness; }

    public long getTotalEvaluations() { return totalEvaluations; }
    public void setTotalEvaluations(long totalEvaluations) { this.totalEvaluations = totalEvaluations; }
}
