package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.dto.response.EvaluationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the classification pipeline against a labelled dataset.
 *
 * <p>The dataset is a JSON array where each element has:
 * <ul>
 *   <li>{@code text}         — the input description</li>
 *   <li>{@code expectedType} — the expected {@link DiagramType} name (e.g. {@code "SEQUENCE"})</li>
 * </ul>
 *
 * <p>Each item is passed to {@link DiagramClassificationService#classify(String)}.
 * The predicted type is compared case-insensitively to {@code expectedType}.
 * Aggregate and per-item results are logged, then returned as an {@link EvaluationResult}.
 */
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationServiceImpl.class);

    private final DiagramClassificationService classificationService;
    private final ObjectMapper objectMapper;
    private final Resource datasetResource;

    public EvaluationServiceImpl(
            DiagramClassificationService classificationService,
            ObjectMapper objectMapper,
            @Value("${diagram.evaluation.dataset:classpath:evaluation-dataset.json}") Resource datasetResource) {
        this.classificationService = classificationService;
        this.objectMapper = objectMapper;
        this.datasetResource = datasetResource;
    }

    @Override
    public EvaluationResult evaluate() {
        logger.info("Starting classification evaluation from dataset: {}", datasetResource.getDescription());

        List<Map<String, String>> dataset = loadDataset();
        List<EvaluationResult.ItemResult> items = new ArrayList<>(dataset.size());
        int correct = 0;

        for (int i = 0; i < dataset.size(); i++) {
            Map<String, String> entry = dataset.get(i);
            String text = entry.get("text");
            String expectedType = entry.get("expectedType");

            if (text == null || text.isBlank() || expectedType == null || expectedType.isBlank()) {
                logger.warn("Skipping malformed dataset entry at index {}: {}", i, entry);
                continue;
            }

            EvaluationResult.ItemResult result = classify(i, text, expectedType);
            items.add(result);
            if (result.isCorrect()) correct++;
        }

        int total = items.size();
        double accuracy = total == 0 ? 0.0 : (correct * 100.0) / total;

        logSummary(total, correct, accuracy);
        logMisclassifications(items);

        return new EvaluationResult(total, correct, accuracy, items, LocalDateTime.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Map<String, String>> loadDataset() {
        try {
            return objectMapper.readValue(
                    datasetResource.getInputStream(),
                    new TypeReference<List<Map<String, String>>>() {}
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load evaluation dataset from " + datasetResource.getDescription(), e);
        }
    }

    private EvaluationResult.ItemResult classify(int index, String text, String expectedType) {
        String predictedType;
        try {
            DiagramType predicted = classificationService.classify(text);
            predictedType = predicted.name();
        } catch (Exception e) {
            logger.warn("[{}] Classification failed for text '{}': {}", index, abbreviate(text), e.getMessage());
            predictedType = "ERROR";
        }

        boolean match = expectedType.equalsIgnoreCase(predictedType);
        String status = match ? "PASS" : "FAIL";

        logger.debug("[{}] {} | expected={} predicted={} | \"{}\"",
                index, status, expectedType, predictedType, abbreviate(text));

        return new EvaluationResult.ItemResult(text, expectedType, predictedType, match);
    }

    private void logSummary(int total, int correct, double accuracy) {
        logger.info("══════════════════════════════════════════");
        logger.info("  Evaluation complete");
        logger.info("  Total   : {}", total);
        logger.info("  Correct : {}", correct);
        logger.info("  Wrong   : {}", total - correct);
        logger.info("  Accuracy: {:.1f}%", accuracy);
        logger.info("══════════════════════════════════════════");
    }

    private void logMisclassifications(List<EvaluationResult.ItemResult> items) {
        List<EvaluationResult.ItemResult> wrong = items.stream()
                .filter(r -> !r.isCorrect())
                .toList();

        if (wrong.isEmpty()) {
            logger.info("No misclassifications.");
            return;
        }

        logger.info("Misclassified items ({}):", wrong.size());
        for (EvaluationResult.ItemResult r : wrong) {
            logger.info("  expected={} predicted={} | \"{}\"",
                    r.getExpectedType(), r.getPredictedType(), abbreviate(r.getText()));
        }
    }

    /** Truncates text to 80 characters for readable log output. */
    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
