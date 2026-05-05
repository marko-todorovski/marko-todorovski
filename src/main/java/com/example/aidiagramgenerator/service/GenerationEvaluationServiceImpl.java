package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.dto.response.GenerationEvaluationResult;
import com.example.aidiagramgenerator.enums.DiagramType;
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
 * End-to-end generation evaluation: loads a dataset, calls {@link DiagramService#generateFromText},
 * and checks that the returned type matches and the Mermaid code is non-empty.
 *
 * <p>An item is counted as successful when both conditions hold:
 * <ul>
 *   <li>The generated {@link DiagramType} name equals the expected type (case-insensitive).</li>
 *   <li>The returned Mermaid code is non-null and non-blank.</li>
 * </ul>
 *
 * <p>If the expected type is not recognised by {@link DiagramType} the item is marked failed
 * with reason {@code "UNSUPPORTED_TYPE"} so the rest of the run continues unaffected.
 */
@Service
public class GenerationEvaluationServiceImpl implements GenerationEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationEvaluationServiceImpl.class);

    private final DiagramService diagramService;
    private final ObjectMapper objectMapper;
    private final Resource datasetResource;

    public GenerationEvaluationServiceImpl(
            DiagramService diagramService,
            ObjectMapper objectMapper,
            @Value("${diagram.generation-evaluation.dataset:classpath:generation-evaluation-dataset.json}")
            Resource datasetResource) {
        this.diagramService = diagramService;
        this.objectMapper = objectMapper;
        this.datasetResource = datasetResource;
    }

    @Override
    public GenerationEvaluationResult evaluate() {
        logger.info("Starting generation evaluation from dataset: {}", datasetResource.getDescription());

        List<Map<String, String>> dataset = loadDataset();
        List<GenerationEvaluationResult.ItemResult> items = new ArrayList<>(dataset.size());
        int successful = 0;

        for (int i = 0; i < dataset.size(); i++) {
            Map<String, String> entry = dataset.get(i);
            String text = entry.get("text");
            String expectedType = entry.get("expectedType");

            if (text == null || text.isBlank() || expectedType == null || expectedType.isBlank()) {
                logger.warn("Skipping malformed entry at index {}: {}", i, entry);
                continue;
            }

            GenerationEvaluationResult.ItemResult result = generate(i, text, expectedType);
            items.add(result);
            if (result.isSuccess()) successful++;
        }

        int total = items.size();
        double successRate = total == 0 ? 0.0 : (successful * 100.0) / total;

        logSummary(total, successful, successRate);
        logFailures(items);

        return new GenerationEvaluationResult(total, successful, successRate, items, LocalDateTime.now());
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
                    "Failed to load generation evaluation dataset from " + datasetResource.getDescription(), e);
        }
    }

    private GenerationEvaluationResult.ItemResult generate(int index, String text, String expectedType) {
        // Resolve expected type — skip gracefully if not supported by this pipeline
        DiagramType resolvedExpected;
        try {
            resolvedExpected = DiagramType.fromValue(expectedType);
        } catch (IllegalArgumentException e) {
            logger.warn("[{}] SKIP | expectedType='{}' is not supported by the generation pipeline | \"{}\"",
                    index, expectedType, abbreviate(text));
            return new GenerationEvaluationResult.ItemResult(
                    text, expectedType, null, false, false, "UNSUPPORTED_TYPE");
        }

        String generatedType = null;
        boolean typeCorrect = false;
        boolean contentNonEmpty = false;
        String failureReason = null;

        try {
            DiagramResponse response = diagramService.generateFromText(new TextDiagramRequest(text, null));

            generatedType = response.getDiagramType() != null ? response.getDiagramType().name() : null;
            typeCorrect = resolvedExpected.name().equalsIgnoreCase(generatedType);
            contentNonEmpty = response.getMermaidCode() != null && !response.getMermaidCode().isBlank();

            if (!typeCorrect) failureReason = "WRONG_TYPE";
            else if (!contentNonEmpty) failureReason = "EMPTY_CONTENT";

        } catch (Exception e) {
            logger.warn("[{}] FAIL | generation threw exception: {} | \"{}\"",
                    index, e.getMessage(), abbreviate(text));
            failureReason = "EXCEPTION: " + e.getMessage();
        }

        boolean success = typeCorrect && contentNonEmpty;
        String status = success ? "PASS" : "FAIL";

        logger.debug("[{}] {} | expected={} generated={} typeOk={} contentOk={} | \"{}\"",
                index, status, expectedType, generatedType, typeCorrect, contentNonEmpty, abbreviate(text));

        return new GenerationEvaluationResult.ItemResult(
                text, expectedType, generatedType, typeCorrect, contentNonEmpty, failureReason);
    }

    private void logSummary(int total, int successful, double successRate) {
        logger.info("══════════════════════════════════════════");
        logger.info("  Generation evaluation complete");
        logger.info("  Total      : {}", total);
        logger.info("  Successful : {}", successful);
        logger.info("  Failed     : {}", total - successful);
        logger.info("  Success rate: {:.1f}%", successRate);
        logger.info("══════════════════════════════════════════");
    }

    private void logFailures(List<GenerationEvaluationResult.ItemResult> items) {
        List<GenerationEvaluationResult.ItemResult> failed = items.stream()
                .filter(r -> !r.isSuccess())
                .toList();

        if (failed.isEmpty()) {
            logger.info("All items generated successfully.");
            return;
        }

        logger.info("Failed items ({}):", failed.size());
        for (GenerationEvaluationResult.ItemResult r : failed) {
            logger.info("  reason={} expected={} generated={} | \"{}\"",
                    r.getFailureReason(), r.getExpectedType(), r.getGeneratedType(), abbreviate(r.getText()));
        }
    }

    /** Truncates text to 80 characters for readable log output. */
    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() <= 80 ? text : text.substring(0, 77) + "...";
    }
}
