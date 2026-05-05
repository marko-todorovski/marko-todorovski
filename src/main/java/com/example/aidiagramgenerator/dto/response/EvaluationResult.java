package com.example.aidiagramgenerator.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a batch classification evaluation run against the evaluation dataset.
 *
 * <p>Contains aggregate accuracy metrics plus a per-item breakdown for detailed analysis.
 */
public class EvaluationResult {

    private final int total;
    private final int correct;
    private final double accuracy;
    private final List<ItemResult> items;
    private final LocalDateTime evaluatedAt;

    public EvaluationResult(int total, int correct, double accuracy,
                            List<ItemResult> items, LocalDateTime evaluatedAt) {
        this.total = total;
        this.correct = correct;
        this.accuracy = accuracy;
        this.items = items;
        this.evaluatedAt = evaluatedAt;
    }

    public int getTotal() { return total; }
    public int getCorrect() { return correct; }
    public double getAccuracy() { return accuracy; }
    public List<ItemResult> getItems() { return items; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }

    // ── Per-item result ───────────────────────────────────────────────────────

    /**
     * Result for a single evaluation dataset item.
     */
    public static class ItemResult {

        private final String text;
        private final String expectedType;
        private final String predictedType;
        private final boolean correct;

        public ItemResult(String text, String expectedType, String predictedType, boolean correct) {
            this.text = text;
            this.expectedType = expectedType;
            this.predictedType = predictedType;
            this.correct = correct;
        }

        public String getText() { return text; }
        public String getExpectedType() { return expectedType; }
        public String getPredictedType() { return predictedType; }
        public boolean isCorrect() { return correct; }
    }
}
