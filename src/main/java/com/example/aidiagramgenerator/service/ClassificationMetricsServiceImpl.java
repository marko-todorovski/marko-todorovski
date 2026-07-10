package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult;
import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult.ClassMetrics;
import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult.DatasetSummary;
import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult.ItemResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the classification pipeline against all three labelled datasets and
 * reports accuracy, per-class precision/recall/F1, and macro-averaged metrics.
 *
 * <p>Datasets loaded:
 * <ul>
 *   <li>{@code evaluation-dataset.json} — general classification samples</li>
 *   <li>{@code generation-evaluation-dataset.json} — generation-oriented samples</li>
 *   <li>{@code usecase-evaluation-dataset.json} — use-case-focused samples</li>
 * </ul>
 */
@Service
public class ClassificationMetricsServiceImpl implements ClassificationMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(ClassificationMetricsServiceImpl.class);

    private final DiagramClassificationService classificationService;
    private final ObjectMapper objectMapper;

    @Value("${diagram.evaluation.dataset:classpath:evaluation-dataset.json}")
    private Resource classificationDatasetResource;

    @Value("${diagram.generation-evaluation.dataset:classpath:generation-evaluation-dataset.json}")
    private Resource generationDatasetResource;

    @Value("${diagram.usecase-evaluation.dataset:classpath:usecase-evaluation-dataset.json}")
    private Resource usecaseDatasetResource;

    public ClassificationMetricsServiceImpl(DiagramClassificationService classificationService,
                                             ObjectMapper objectMapper) {
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public ClassificationMetricsResult evaluate() {
        List<ItemResult> items = new ArrayList<>();

        DatasetSummary classificationSummary = evaluateDataset(
                classificationDatasetResource, "evaluation-dataset.json", items);
        DatasetSummary generationSummary = evaluateDataset(
                generationDatasetResource, "generation-evaluation-dataset.json", items);
        DatasetSummary usecaseSummary = evaluateDataset(
                usecaseDatasetResource, "usecase-evaluation-dataset.json", items);

        int totalSamples = items.size();
        long totalCorrect = items.stream().filter(ItemResult::isCorrect).count();
        double accuracy = totalSamples > 0 ? (double) totalCorrect / totalSamples * 100.0 : 0.0;

        Map<String, ClassMetrics> perClassMetrics = buildPerClassMetrics(items);
        double[] macroAverages = computeMacroAverages(perClassMetrics);

        printReport(items, perClassMetrics, macroAverages, accuracy, totalSamples, (int) totalCorrect,
                classificationSummary, generationSummary, usecaseSummary);

        ClassificationMetricsResult result = new ClassificationMetricsResult();
        result.setTotalSamples(totalSamples);
        result.setTotalCorrect((int) totalCorrect);
        result.setAccuracy(round2(accuracy));
        result.setMacroPrecision(round2(macroAverages[0]));
        result.setMacroRecall(round2(macroAverages[1]));
        result.setMacroF1(round2(macroAverages[2]));
        result.setPerClassMetrics(perClassMetrics);
        result.setClassificationDataset(classificationSummary);
        result.setGenerationDataset(generationSummary);
        result.setUsecaseDataset(usecaseSummary);
        result.setItems(items);
        result.setEvaluatedAt(LocalDateTime.now());
        return result;
    }

    // ── Dataset loading ───────────────────────────────────────────────────────

    /** Evaluates one dataset resource and appends {@link ItemResult}s to {@code items}. */
    private DatasetSummary evaluateDataset(Resource resource, String datasetName,
                                            List<ItemResult> items) {
        List<EvaluationEntry> entries = loadEntries(resource, datasetName);
        int correct = 0;

        for (EvaluationEntry entry : entries) {
            String text = entry.text;
            String expectedTypeName = entry.expectedType.trim().toUpperCase();
            String predictedTypeName = classifySafely(text);
            boolean isCorrect = expectedTypeName.equals(predictedTypeName);
            if (isCorrect) correct++;
            items.add(new ItemResult(datasetName, text, expectedTypeName, predictedTypeName, isCorrect));
        }

        double accuracy = entries.isEmpty() ? 0.0 : (double) correct / entries.size() * 100.0;
        return new DatasetSummary(datasetName, entries.size(), correct, round2(accuracy));
    }

    private List<EvaluationEntry> loadEntries(Resource resource, String datasetName) {
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<List<EvaluationEntry>>() {});
        } catch (IOException e) {
            logger.error("Failed to load evaluation dataset '{}': {}", datasetName, e.getMessage());
            return List.of();
        }
    }

    private String classifySafely(String text) {
        try {
            DiagramType type = classificationService.classify(text);
            return type != null ? type.name() : "UNKNOWN";
        } catch (Exception e) {
            logger.warn("Classification failed for text (truncated: '{}'): {}",
                    text.length() > 60 ? text.substring(0, 60) + "…" : text, e.getMessage());
            return "ERROR";
        }
    }

    // ── Metrics computation ───────────────────────────────────────────────────

    /** Builds a confusion-matrix-derived metrics map for every class that appears in the data. */
    private Map<String, ClassMetrics> buildPerClassMetrics(List<ItemResult> items) {
        // Collect TP, FP, FN per class
        Map<String, int[]> counts = new LinkedHashMap<>(); // int[]{tp, fp, fn, support}

        // Ensure all expected labels are present
        for (ItemResult item : items) {
            counts.computeIfAbsent(item.getExpectedType(), k -> new int[4]);
        }

        for (ItemResult item : items) {
            String expected = item.getExpectedType();
            String predicted = item.getPredictedType();

            // Support = number of expected samples per class
            counts.get(expected)[3]++;

            if (expected.equals(predicted)) {
                // True Positive for expected class
                counts.get(expected)[0]++;
            } else {
                // False Negative for expected class (missed)
                counts.get(expected)[2]++;
                // False Positive for predicted class (if it's a valid class)
                counts.computeIfAbsent(predicted, k -> new int[4]);
                counts.get(predicted)[1]++;
            }
        }

        Map<String, ClassMetrics> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int tp = entry.getValue()[0];
            int fp = entry.getValue()[1];
            int fn = entry.getValue()[2];
            int support = entry.getValue()[3];

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) * 100.0 : 0.0;
            double recall    = (tp + fn) > 0 ? (double) tp / (tp + fn) * 100.0 : 0.0;
            double f1        = (precision + recall) > 0
                    ? 2.0 * precision * recall / (precision + recall)
                    : 0.0;

            metrics.put(entry.getKey(),
                    new ClassMetrics(tp, fp, fn, support, round2(precision), round2(recall), round2(f1)));
        }
        return metrics;
    }

    /** Returns [macroPrecision, macroRecall, macroF1] averaged over classes with support > 0. */
    private double[] computeMacroAverages(Map<String, ClassMetrics> perClassMetrics) {
        double sumP = 0, sumR = 0, sumF1 = 0;
        int classesWithSupport = 0;

        for (Map.Entry<String, ClassMetrics> entry : perClassMetrics.entrySet()) {
            if (entry.getValue().getSupport() > 0) {
                sumP  += entry.getValue().getPrecision();
                sumR  += entry.getValue().getRecall();
                sumF1 += entry.getValue().getF1();
                classesWithSupport++;
            }
        }

        if (classesWithSupport == 0) return new double[]{0.0, 0.0, 0.0};
        return new double[]{
                sumP  / classesWithSupport,
                sumR  / classesWithSupport,
                sumF1 / classesWithSupport
        };
    }

    // ── Console reporting ─────────────────────────────────────────────────────

    private void printReport(List<ItemResult> items,
                              Map<String, ClassMetrics> perClassMetrics,
                              double[] macroAverages,
                              double accuracy, int total, int correct,
                              DatasetSummary cls, DatasetSummary gen, DatasetSummary uc) {

        logger.info("╔══════════════════════════════════════════════════════════════╗");
        logger.info("║          CLASSIFICATION EVALUATION REPORT                   ║");
        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  Dataset                         Total  Correct  Accuracy   ║");
        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  {:<32}  {:>5}    {:>5}   {:>6.1f}%   ║",
                cls.getName(), cls.getTotal(), cls.getCorrect(), cls.getAccuracy());
        logger.info("║  {:<32}  {:>5}    {:>5}   {:>6.1f}%   ║",
                gen.getName(), gen.getTotal(), gen.getCorrect(), gen.getAccuracy());
        logger.info("║  {:<32}  {:>5}    {:>5}   {:>6.1f}%   ║",
                uc.getName(), uc.getTotal(), uc.getCorrect(), uc.getAccuracy());
        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  TOTAL                               {:>5}    {:>5}   {:>6.1f}%   ║",
                total, correct, accuracy);
        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  Per-Class Metrics                                          ║");
        logger.info("║  {:<18}  {:>7}  {:>7}  {:>7}  {:>7}   ║",
                "Class", "Support", "Prec%", "Rec%", "F1%");
        logger.info("╠══════════════════════════════════════════════════════════════╣");

        for (Map.Entry<String, ClassMetrics> e : perClassMetrics.entrySet()) {
            if (e.getValue().getSupport() > 0) {
                logger.info("║  {:<18}  {:>7}  {:>7.1f}  {:>7.1f}  {:>7.1f}   ║",
                        e.getKey(),
                        e.getValue().getSupport(),
                        e.getValue().getPrecision(),
                        e.getValue().getRecall(),
                        e.getValue().getF1());
            }
        }

        logger.info("╠══════════════════════════════════════════════════════════════╣");
        logger.info("║  MACRO AVERAGE             {:>7.1f}  {:>7.1f}  {:>7.1f}   ║",
                macroAverages[0], macroAverages[1], macroAverages[2]);
        logger.info("╚══════════════════════════════════════════════════════════════╝");

        // Per-item misclassification log
        logger.info("── Misclassifications ─────────────────────────────────────────");
        items.stream()
                .filter(it -> !it.isCorrect())
                .forEach(it -> logger.info("  [{}] expected={} predicted={}  »  {}",
                        it.getDataset(),
                        it.getExpectedType(),
                        it.getPredictedType(),
                        truncate(it.getText(), 80)));
        logger.info("───────────────────────────────────────────────────────────────");
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ── Dataset entry POJO ────────────────────────────────────────────────────

    /** Accepts both plain {@code {"text","expectedType"}} and extended {@code {"title","text","expectedType"}} entries. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EvaluationEntry {
        public String text;
        public String expectedType;
    }
}
