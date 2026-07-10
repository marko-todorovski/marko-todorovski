package com.example.aidiagramgenerator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full classification evaluation report covering all three evaluation datasets.
 *
 * <p>Includes per-class precision/recall/F1 and macro-averaged metrics alongside
 * a per-dataset accuracy breakdown and individual item-level predictions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassificationMetricsResult {

    private int totalSamples;
    private int totalCorrect;
    private double accuracy;

    private double macroPrecision;
    private double macroRecall;
    private double macroF1;

    /** Per-class metrics keyed by DiagramType name (e.g. "SEQUENCE", "CLASS"). */
    private Map<String, ClassMetrics> perClassMetrics;

    private DatasetSummary classificationDataset;
    private DatasetSummary generationDataset;
    private DatasetSummary usecaseDataset;

    private List<ItemResult> items;

    private LocalDateTime evaluatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ClassificationMetricsResult() {
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    public static class ClassMetrics {
        private int truePositives;
        private int falsePositives;
        private int falseNegatives;
        /** Number of samples where this class is the expected label. */
        private int support;
        private double precision;
        private double recall;
        private double f1;

        public ClassMetrics() {
        }

        public ClassMetrics(int truePositives, int falsePositives, int falseNegatives,
                            int support, double precision, double recall, double f1) {
            this.truePositives = truePositives;
            this.falsePositives = falsePositives;
            this.falseNegatives = falseNegatives;
            this.support = support;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
        }

        public int getTruePositives() { return truePositives; }
        public void setTruePositives(int truePositives) { this.truePositives = truePositives; }

        public int getFalsePositives() { return falsePositives; }
        public void setFalsePositives(int falsePositives) { this.falsePositives = falsePositives; }

        public int getFalseNegatives() { return falseNegatives; }
        public void setFalseNegatives(int falseNegatives) { this.falseNegatives = falseNegatives; }

        public int getSupport() { return support; }
        public void setSupport(int support) { this.support = support; }

        public double getPrecision() { return precision; }
        public void setPrecision(double precision) { this.precision = precision; }

        public double getRecall() { return recall; }
        public void setRecall(double recall) { this.recall = recall; }

        public double getF1() { return f1; }
        public void setF1(double f1) { this.f1 = f1; }
    }

    public static class DatasetSummary {
        private String name;
        private int total;
        private int correct;
        private double accuracy;

        public DatasetSummary() {
        }

        public DatasetSummary(String name, int total, int correct, double accuracy) {
            this.name = name;
            this.total = total;
            this.correct = correct;
            this.accuracy = accuracy;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getCorrect() { return correct; }
        public void setCorrect(int correct) { this.correct = correct; }

        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    }

    public static class ItemResult {
        private String dataset;
        private String text;
        private String expectedType;
        private String predictedType;
        private boolean correct;

        public ItemResult() {
        }

        public ItemResult(String dataset, String text, String expectedType,
                          String predictedType, boolean correct) {
            this.dataset = dataset;
            this.text = text;
            this.expectedType = expectedType;
            this.predictedType = predictedType;
            this.correct = correct;
        }

        public String getDataset() { return dataset; }
        public void setDataset(String dataset) { this.dataset = dataset; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getExpectedType() { return expectedType; }
        public void setExpectedType(String expectedType) { this.expectedType = expectedType; }

        public String getPredictedType() { return predictedType; }
        public void setPredictedType(String predictedType) { this.predictedType = predictedType; }

        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getTotalSamples() { return totalSamples; }
    public void setTotalSamples(int totalSamples) { this.totalSamples = totalSamples; }

    public int getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public double getMacroPrecision() { return macroPrecision; }
    public void setMacroPrecision(double macroPrecision) { this.macroPrecision = macroPrecision; }

    public double getMacroRecall() { return macroRecall; }
    public void setMacroRecall(double macroRecall) { this.macroRecall = macroRecall; }

    public double getMacroF1() { return macroF1; }
    public void setMacroF1(double macroF1) { this.macroF1 = macroF1; }

    public Map<String, ClassMetrics> getPerClassMetrics() { return perClassMetrics; }
    public void setPerClassMetrics(Map<String, ClassMetrics> perClassMetrics) { this.perClassMetrics = perClassMetrics; }

    public DatasetSummary getClassificationDataset() { return classificationDataset; }
    public void setClassificationDataset(DatasetSummary classificationDataset) { this.classificationDataset = classificationDataset; }

    public DatasetSummary getGenerationDataset() { return generationDataset; }
    public void setGenerationDataset(DatasetSummary generationDataset) { this.generationDataset = generationDataset; }

    public DatasetSummary getUsecaseDataset() { return usecaseDataset; }
    public void setUsecaseDataset(DatasetSummary usecaseDataset) { this.usecaseDataset = usecaseDataset; }

    public List<ItemResult> getItems() { return items; }
    public void setItems(List<ItemResult> items) { this.items = items; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
