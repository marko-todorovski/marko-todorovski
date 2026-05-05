package com.example.aidiagramgenerator.dto.response;

/**
 * Response DTO containing global analytics across all diagram evaluations.
 */
public class GlobalDiagramAnalyticsResponse {

    private double averageClarity;
    private double averageCorrectness;
    private double averageUsefulness;
    private long totalEvaluations;
    private long totalEvaluatedDiagrams;

    public GlobalDiagramAnalyticsResponse() {
    }

    public GlobalDiagramAnalyticsResponse(double averageClarity, double averageCorrectness,
                                          double averageUsefulness, long totalEvaluations,
                                          long totalEvaluatedDiagrams) {
        this.averageClarity = averageClarity;
        this.averageCorrectness = averageCorrectness;
        this.averageUsefulness = averageUsefulness;
        this.totalEvaluations = totalEvaluations;
        this.totalEvaluatedDiagrams = totalEvaluatedDiagrams;
    }

    public double getAverageClarity() { return averageClarity; }
    public void setAverageClarity(double averageClarity) { this.averageClarity = averageClarity; }

    public double getAverageCorrectness() { return averageCorrectness; }
    public void setAverageCorrectness(double averageCorrectness) { this.averageCorrectness = averageCorrectness; }

    public double getAverageUsefulness() { return averageUsefulness; }
    public void setAverageUsefulness(double averageUsefulness) { this.averageUsefulness = averageUsefulness; }

    public long getTotalEvaluations() { return totalEvaluations; }
    public void setTotalEvaluations(long totalEvaluations) { this.totalEvaluations = totalEvaluations; }

    public long getTotalEvaluatedDiagrams() { return totalEvaluatedDiagrams; }
    public void setTotalEvaluatedDiagrams(long totalEvaluatedDiagrams) { this.totalEvaluatedDiagrams = totalEvaluatedDiagrams; }
}
