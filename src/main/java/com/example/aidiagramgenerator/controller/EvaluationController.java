package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.response.ApiResponse;
import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult;
import com.example.aidiagramgenerator.dto.response.EvaluationResult;
import com.example.aidiagramgenerator.service.ClassificationMetricsService;
import com.example.aidiagramgenerator.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering and reporting classification evaluation runs.
 */
@RestController
@RequestMapping("/api/evaluation")
@Tag(name = "Evaluation", description = "APIs for running classification evaluation against the labelled dataset")
public class EvaluationController {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationService evaluationService;
    private final ClassificationMetricsService classificationMetricsService;

    public EvaluationController(EvaluationService evaluationService,
                                 ClassificationMetricsService classificationMetricsService) {
        this.evaluationService = evaluationService;
        this.classificationMetricsService = classificationMetricsService;
    }

    /**
     * Runs the classification evaluation and returns accuracy metrics.
     *
     * <p>Results are also printed to the console via the logging framework.
     */
    @GetMapping("/run")
    @Operation(
            summary = "Run classification evaluation",
            description = "Evaluates the classification pipeline against the labelled dataset and returns accuracy %"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Evaluation completed successfully"
            )
    })
    public ResponseEntity<ApiResponse<EvaluationResult>> run() {
        logger.info("Classification evaluation requested via GET /api/evaluation/run");

        EvaluationResult result = evaluationService.evaluate();

        logger.info("Evaluation complete — accuracy: {:.1f}% ({}/{} correct)",
                result.getAccuracy(), result.getCorrect(), result.getTotal());

        String message = String.format("Accuracy: %.1f%% (%d/%d correct)",
                result.getAccuracy(), result.getCorrect(), result.getTotal());

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /**
     * Runs the full academic evaluation across all three datasets and returns
     * per-class precision, recall, F1-score, and macro-averaged metrics.
     *
     * <p>Results are also printed to the console via the logging framework.
     */
    @GetMapping("/results")
    @Operation(
            summary = "Run academic classification evaluation",
            description = "Evaluates the classification pipeline against all three labelled datasets " +
                    "and returns accuracy, per-class precision/recall/F1, and macro-averaged metrics"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Evaluation completed successfully"
            )
    })
    public ResponseEntity<ApiResponse<ClassificationMetricsResult>> results() {
        logger.info("Academic classification evaluation requested via GET /api/evaluation/results");

        ClassificationMetricsResult report = classificationMetricsService.evaluate();

        String message = String.format(
                "Accuracy: %.1f%% | Macro-F1: %.1f%% (%d/%d correct across all datasets)",
                report.getAccuracy(), report.getMacroF1(),
                report.getTotalCorrect(), report.getTotalSamples());

        return ResponseEntity.ok(ApiResponse.success(message, report));
    }
}
