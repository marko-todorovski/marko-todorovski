package com.example.aidiagramgenerator.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of a batch generation evaluation run.
 *
 * <p>Each item is considered a success when both:
 * <ul>
 *   <li>the generated diagram type matches the expected type</li>
 *   <li>the generated diagram content (Mermaid code) is non-empty</li>
 * </ul>
 *
 * <p>The success rate is {@code (successful / total) * 100}.
 */
public class GenerationEvaluationResult {

    private final int total;
    private final int successful;
    private final double successRate;
    private final List<ItemResult> items;
    private final LocalDateTime evaluatedAt;

    public GenerationEvaluationResult(int total, int successful, double successRate,
                                      List<ItemResult> items, LocalDateTime evaluatedAt) {
        this.total = total;
        this.successful = successful;
        this.successRate = successRate;
        this.items = items;
        this.evaluatedAt = evaluatedAt;
    }

    public int getTotal() { return total; }
    public int getSuccessful() { return successful; }
    public double getSuccessRate() { return successRate; }
    public List<ItemResult> getItems() { return items; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }

    // ── Per-item result ───────────────────────────────────────────────────────

    /**
     * Outcome for a single dataset entry.
     */
    public static class ItemResult {

        private final String text;
        private final String expectedType;
        private final String generatedType;
        private final boolean typeCorrect;
        private final boolean contentNonEmpty;
        private final boolean success;
        private final String failureReason;

        public ItemResult(String text, String expectedType, String generatedType,
                          boolean typeCorrect, boolean contentNonEmpty, String failureReason) {
            this.text = text;
            this.expectedType = expectedType;
            this.generatedType = generatedType;
            this.typeCorrect = typeCorrect;
            this.contentNonEmpty = contentNonEmpty;
            this.success = typeCorrect && contentNonEmpty;
            this.failureReason = failureReason;
        }

        public String getText() { return text; }
        public String getExpectedType() { return expectedType; }
        public String getGeneratedType() { return generatedType; }
        public boolean isTypeCorrect() { return typeCorrect; }
        public boolean isContentNonEmpty() { return contentNonEmpty; }
        public boolean isSuccess() { return success; }
        public String getFailureReason() { return failureReason; }
    }
}
