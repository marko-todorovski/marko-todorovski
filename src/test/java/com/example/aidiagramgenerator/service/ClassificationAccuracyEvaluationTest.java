package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluates the classification accuracy of {@link DiagramClassificationServiceImpl}
 * against a labelled dataset loaded from {@code src/test/resources/evaluation-dataset.json}.
 *
 * <p>Each dataset entry contains:
 * <ul>
 *   <li>{@code text}         — the natural language input</li>
 *   <li>{@code expectedType} — the correct {@link DiagramType} name (e.g. "SEQUENCE")</li>
 * </ul>
 *
 * <p>The test prints a full breakdown to the console:
 * <ul>
 *   <li>Per-sample pass/fail with predicted vs expected type</li>
 *   <li>Per-type accuracy (precision per class)</li>
 *   <li>Overall accuracy percentage</li>
 * </ul>
 *
 * <p>The test asserts that overall accuracy is at least 70% (configurable via
 * {@link #MINIMUM_ACCURACY_PERCENT}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Classification Accuracy Evaluation")
class ClassificationAccuracyEvaluationTest {

    /** Minimum acceptable accuracy for the test to pass. */
    private static final double MINIMUM_ACCURACY_PERCENT = 70.0;

    private static final String DATASET_PATH = "/evaluation-dataset.json";

    @Mock
    private AiModelService aiModelService;

    // ── Dataset record ────────────────────────────────────────────────────────

    static class EvaluationEntry {
        public String text;
        public String expectedType;
    }

    // ── Evaluation result record ──────────────────────────────────────────────

    static class EvaluationResult {
        final String text;
        final DiagramType expected;
        final DiagramType predicted;
        final boolean correct;

        EvaluationResult(String text, DiagramType expected, DiagramType predicted) {
            this.text = text;
            this.expected = expected;
            this.predicted = predicted;
            this.correct = expected == predicted;
        }
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Overall accuracy should be >= " + MINIMUM_ACCURACY_PERCENT + "%")
    void evaluateClassificationAccuracy() throws Exception {
        DiagramClassificationServiceImpl service = new DiagramClassificationServiceImpl(aiModelService);
        List<EvaluationEntry> dataset = loadDataset();
        List<EvaluationResult> results = new ArrayList<>();

        for (EvaluationEntry entry : dataset) {
            DiagramType expected = DiagramType.valueOf(entry.expectedType.trim().toUpperCase());
            DiagramType predicted = classifySafely(service, entry.text);
            results.add(new EvaluationResult(entry.text, expected, predicted));
        }

        printReport(results);

        long correct = results.stream().filter(r -> r.correct).count();
        double accuracy = (double) correct / results.size() * 100.0;

        assertTrue(
            accuracy >= MINIMUM_ACCURACY_PERCENT,
            String.format("Accuracy %.1f%% is below the required %.1f%%", accuracy, MINIMUM_ACCURACY_PERCENT)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<EvaluationEntry> loadDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(DATASET_PATH)) {
            if (in == null) {
                throw new IllegalStateException("evaluation-dataset.json not found on classpath at: " + DATASET_PATH);
            }
            return mapper.readValue(in, new TypeReference<List<EvaluationEntry>>() {});
        }
    }

    private DiagramType classifySafely(DiagramClassificationServiceImpl service, String text) {
        try {
            return service.classify(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void printReport(List<EvaluationResult> results) {
        int total = results.size();
        long correct = results.stream().filter(r -> r.correct).count();
        double accuracy = (double) correct / total * 100.0;

        String separator = "=".repeat(80);
        String thinLine  = "-".repeat(80);

        System.out.println("\n" + separator);
        System.out.println("  CLASSIFICATION ACCURACY EVALUATION REPORT");
        System.out.println(separator);

        // ── Per-sample results ────────────────────────────────────────────────
        System.out.println("\n  SAMPLE RESULTS");
        System.out.println(thinLine);
        System.out.printf("  %-4s %-10s %-10s  %s%n", "№", "EXPECTED", "PREDICTED", "INPUT (truncated)");
        System.out.println(thinLine);

        for (int i = 0; i < results.size(); i++) {
            EvaluationResult r = results.get(i);
            String status    = r.correct ? "✓" : "✗";
            String predicted = r.predicted != null ? r.predicted.name() : "ERROR";
            String snippet   = r.text.length() > 55 ? r.text.substring(0, 52) + "..." : r.text;
            System.out.printf("  %s %-3d %-10s %-10s  %s%n",
                status, i + 1, r.expected.name(), predicted, snippet);
        }

        // ── Per-type breakdown ────────────────────────────────────────────────
        System.out.println("\n" + thinLine);
        System.out.println("  PER-TYPE ACCURACY");
        System.out.println(thinLine);
        System.out.printf("  %-12s  %8s  %9s  %9s%n", "TYPE", "SAMPLES", "CORRECT", "ACCURACY");
        System.out.println(thinLine);

        Map<DiagramType, long[]> perType = new EnumMap<>(DiagramType.class);
        for (DiagramType t : DiagramType.values()) {
            perType.put(t, new long[]{0, 0}); // [total, correct]
        }

        for (EvaluationResult r : results) {
            long[] counts = perType.get(r.expected);
            if (counts != null) {
                counts[0]++;
                if (r.correct) counts[1]++;
            }
        }

        for (DiagramType t : DiagramType.values()) {
            long[] counts = perType.get(t);
            if (counts[0] == 0) continue;
            double typeAccuracy = (double) counts[1] / counts[0] * 100.0;
            System.out.printf("  %-12s  %8d  %9d  %8.1f%%%n",
                t.name(), counts[0], counts[1], typeAccuracy);
        }

        // ── Overall summary ───────────────────────────────────────────────────
        System.out.println("\n" + separator);
        System.out.printf("  TOTAL SAMPLES : %d%n", total);
        System.out.printf("  CORRECT       : %d%n", correct);
        System.out.printf("  INCORRECT     : %d%n", total - correct);
        System.out.printf("  ACCURACY      : %.1f%%%n", accuracy);
        System.out.println(separator + "\n");
    }
}
