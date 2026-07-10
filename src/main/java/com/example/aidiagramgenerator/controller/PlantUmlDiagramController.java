package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.DiagramEvaluation;
import com.example.aidiagramgenerator.dto.request.EvaluationRequest;
import com.example.aidiagramgenerator.dto.request.GenerationRequest;
import com.example.aidiagramgenerator.dto.response.ApiResponse;
import com.example.aidiagramgenerator.dto.response.DiagramSuggestionResponse;
import com.example.aidiagramgenerator.dto.response.EvaluationResponse;
import com.example.aidiagramgenerator.dto.response.GenerationResult;
import com.example.aidiagramgenerator.exception.DiagramGenerationException;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramEvaluationRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.ConfidenceDiagramService;
import com.example.aidiagramgenerator.service.DiagramSuggestionService;
import com.example.aidiagramgenerator.service.MermaidRenderer;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.example.aidiagramgenerator.exception.InvalidDiagramRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**
 * REST Controller for PlantUML-based diagram generation.
 * Provides endpoints for generating, retrieving, and listing diagrams.
 */
@RestController
@RequestMapping("/api/diagram")
@Tag(name = "PlantUML Diagram Generation", description = "APIs for generating PlantUML diagrams from natural language")
public class PlantUmlDiagramController {

    private static final Logger logger = LoggerFactory.getLogger(PlantUmlDiagramController.class);

    private static final int CONFIDENCE_THRESHOLD = 60;

    private final ConfidenceDiagramService confidenceDiagramService;
    private final DiagramSuggestionService suggestionService;
    private final DiagramRenderingService renderingService;
    private final DomainDiagramRepository diagramRepository;
    private final DiagramRepository mermaidDiagramRepository;
    private final MermaidRenderer mermaidRenderer;
    private final DomainDiagramEvaluationRepository evaluationRepository;

    public PlantUmlDiagramController(
            ConfidenceDiagramService confidenceDiagramService,
            DiagramSuggestionService suggestionService,
            DiagramRenderingService renderingService,
            DomainDiagramRepository diagramRepository,
            DiagramRepository mermaidDiagramRepository,
            MermaidRenderer mermaidRenderer,
            DomainDiagramEvaluationRepository evaluationRepository) {
        this.confidenceDiagramService = confidenceDiagramService;
        this.suggestionService = suggestionService;
        this.renderingService = renderingService;
        this.diagramRepository = diagramRepository;
        this.mermaidDiagramRepository = mermaidDiagramRepository;
        this.mermaidRenderer = mermaidRenderer;
        this.evaluationRepository = evaluationRepository;
    }

    /**
     * Generates a diagram from natural language text.
     * Flow: classify -> extract -> getStyleProfile -> generate -> render -> save
     *
     * @param request the generation request containing the input text
     * @return the generated diagram result
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate diagram from text",
               description = "Classifies text, extracts semantic model, generates PlantUML code, and renders to PNG/SVG")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diagram generated successfully",
                    content = @Content(schema = @Schema(implementation = GenerationResult.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Generation failed")
    })
    public ResponseEntity<ApiResponse<GenerationResult>> generateDiagram(@Valid @RequestBody GenerationRequest request) {
        logger.info("POST /api/diagram/generate — diagramType='{}', forceGenerate={}, textLength={}, text='{}'",
                request.getDiagramType(),
                request.isForceGenerate(),
                request.getText() != null ? request.getText().length() : 0,
                request.getText() != null ? request.getText().replaceAll("\\s+", " ").substring(0, Math.min(200, request.getText().length())) : null);

        // Text is required only when no explicit diagram type is provided
        boolean hasType = request.getDiagramType() != null && !request.getDiagramType().isBlank();
        boolean hasText = request.getText() != null && !request.getText().isBlank();
        if (!hasType && !hasText) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Text must not be blank when no diagram type is specified"));
        }

        try {
            GenerationResult result = confidenceDiagramService.process(
                    request.getText(), request.getDiagramType(), request.getSeed(), request.isForceGenerate());

            // Determine HTTP status based on confidence outcome
            if (Boolean.TRUE.equals(result.getConfirmationRequired())) {
                // Medium (40-69%) or low (<40%) confidence — return 422 with suggestion
                logger.info("Returning suggestion (confidence: {}, confirmation required)",
                        result.getConfidenceScore());
                return ResponseEntity.status(422).body(ApiResponse.success("Confirmation required", result));
            }

            logger.info("Diagram generated successfully (id: {}, type: {}, mode: {})",
                    result.getId(), result.getDiagramType(), result.getGenerationMode());
            return ResponseEntity.ok(ApiResponse.success("Diagram generated successfully", result));

        } catch (InvalidDiagramRequestException e) {
            logger.warn("Invalid diagram request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (DiagramGenerationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Diagram generation failed", e);
            throw new DiagramGenerationException("Failed to generate diagram: " + e.getMessage());
        }
    }

    /**
     * Suggests a diagram type for the given text without generating the diagram.
     * Returns a suggestion with a confidence score. If confidence is below the threshold,
     * the client should confirm with the user before calling /generate.
     *
     * @param request the generation request containing the input text
     * @return a suggestion with confidence scoring
     */
    @PostMapping("/suggest")
    @Operation(summary = "Suggest diagram type",
               description = "Analyzes text and suggests a diagram type with confidence score. " +
                       "If confidence < 60, confirmation is recommended before generation.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suggestion generated",
                    content = @Content(schema = @Schema(implementation = DiagramSuggestionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<ApiResponse<DiagramSuggestionResponse>> suggestDiagramType(
            @Valid @RequestBody GenerationRequest request) {
        logger.info("Received diagram suggestion request");
        logger.debug("Input text length: {}", request.getText() != null ? request.getText().length() : 0);

        DiagramSuggestion suggestion = suggestionService.suggest(request.getText());

        boolean confirmationRequired = suggestion.getConfidenceScore() < CONFIDENCE_THRESHOLD;

        DiagramSuggestionResponse response = DiagramSuggestionResponse.builder()
                .suggestedDiagramType(suggestion.getSuggestedDiagramType())
                .confidenceScore(suggestion.getConfidenceScore())
                .reasoningMessage(suggestion.getReasoningMessage())
                .confirmationRequired(confirmationRequired)
                .build();

        logger.info("Suggestion: {} (confidence: {}, confirmation required: {})",
                suggestion.getSuggestedDiagramType(), suggestion.getConfidenceScore(), confirmationRequired);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves a diagram by its ID.
     *
     * @param id the diagram UUID
     * @return the diagram result or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get diagram by ID", description = "Retrieves a previously generated diagram")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diagram found",
                    content = @Content(schema = @Schema(implementation = GenerationResult.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<GenerationResult>> getDiagram(@PathVariable UUID id) {
        logger.debug("Fetching diagram with ID: {}", id);

        return diagramRepository.findById(id)
                .map(diagram -> {
                    GenerationResult result = GenerationResult.builder()
                            .id(diagram.getId())
                            .diagramType(diagram.getDiagramType())
                            .plantUmlCode(diagram.getPlantUmlCode())
                            .modelUsed(diagram.getModelUsed())
                            .generatedAt(diagram.getCreatedAt())
                            .message("Diagram retrieved successfully")
                            .build();
                    return ResponseEntity.ok(ApiResponse.success(result));
                })
                .orElseGet(() -> {
                    logger.warn("Diagram not found with ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Diagram not found with ID: " + id));
                });
    }

    /**
     * Returns all available diagram types.
     *
     * @return list of diagram type values
     */
    @GetMapping("/types")
    @Operation(summary = "Get all diagram types", description = "Returns all supported diagram type values")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of diagram types")
    public ResponseEntity<ApiResponse<List<DiagramTypeInfo>>> getDiagramTypes() {
        logger.debug("Fetching all diagram types");

        List<DiagramTypeInfo> types = Arrays.stream(DiagramType.values())
                .map(type -> new DiagramTypeInfo(type.name(), type.getCode(), type.getDisplayName()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(types));
    }

    /**
     * Renders an existing diagram to PNG.
     *
     * @param id the diagram UUID
     * @return PNG image bytes
     */
    @GetMapping(value = "/{id}/png", produces = {MediaType.IMAGE_PNG_VALUE, "image/svg+xml"})
    @Operation(summary = "Get diagram as PNG", description = "Renders the diagram to PNG format")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PNG image",
                    content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE)),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Rendering failed")
    })
    public ResponseEntity<byte[]> getDiagramPng(@PathVariable UUID id) {
        logger.debug("Rendering diagram to PNG, ID: {}", id);

        return diagramRepository.findById(id)
                .map(diagram -> renderPlantUmlPng(id, diagram))
                .orElseGet(() -> renderMermaidSvgImage(id, "PNG"));
    }

    /**
     * Renders an existing diagram to SVG.
     *
     * @param id the diagram UUID
     * @return SVG content
     */
    @GetMapping(value = "/{id}/svg", produces = "image/svg+xml")
    @Operation(summary = "Get diagram as SVG", description = "Renders the diagram to SVG format")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SVG image",
                    content = @Content(mediaType = "image/svg+xml")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Rendering failed")
    })
    public ResponseEntity<byte[]> getDiagramSvg(@PathVariable UUID id) {
        logger.debug("Rendering diagram to SVG, ID: {}", id);

        return diagramRepository.findById(id)
                .map(diagram -> renderPlantUmlSvg(id, diagram))
                .orElseGet(() -> renderMermaidSvgImage(id, "SVG"));
    }

    private ResponseEntity<byte[]> renderPlantUmlPng(UUID id, com.example.aidiagramgenerator.domain.Diagram diagram) {
        try {
            byte[] pngBytes = renderingService.renderToPng(diagram.getPlantUmlCode());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(pngBytes);
        } catch (DiagramRenderingException e) {
            logger.error("Failed to render PNG for diagram {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<byte[]> renderPlantUmlSvg(UUID id, com.example.aidiagramgenerator.domain.Diagram diagram) {
        try {
            byte[] svgBytes = renderingService.renderToSvg(diagram.getPlantUmlCode());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .body(svgBytes);
        } catch (DiagramRenderingException e) {
            logger.error("Failed to render SVG for diagram {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<byte[]> renderMermaidSvgImage(UUID id, String requestedFormat) {
        return mermaidDiagramRepository.findById(id)
                .map(diagram -> {
                    try {
                        String svg = mermaidRenderer.renderToSvg(diagram.getMermaidCode());
                        return ResponseEntity.ok()
                                .contentType(MediaType.parseMediaType("image/svg+xml"))
                                .body(svg.getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        logger.error("Failed to render Mermaid {} for diagram {}: {}",
                                requestedFormat, id, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<byte[]>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Draw.io download is handled by DiagramDrawIoController which supports
    // both the domain_diagrams table (PlantUML) and the legacy diagrams table (Mermaid/PDF).

    // ==================== Evaluation Endpoints ====================

    /**
     * Submits an evaluation for a diagram.
     *
     * @param request the evaluation request with scores
     * @return the saved evaluation
     */
    @PostMapping("/evaluate")
    @Operation(summary = "Submit diagram evaluation",
               description = "Submits a human evaluation with clarity, correctness, and usefulness scores (1-5)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Evaluation submitted successfully",
                    content = @Content(schema = @Schema(implementation = EvaluationResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid scores or missing diagram"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<EvaluationResponse>> submitEvaluation(@Valid @RequestBody EvaluationRequest request) {
        logger.info("Received evaluation for diagram: {}", request.getDiagramId());
        logger.debug("Scores - clarity: {}, correctness: {}, usefulness: {}",
                request.getClarityScore(), request.getCorrectnessScore(), request.getUsefulnessScore());

        // Verify diagram exists
        if (!diagramRepository.existsById(request.getDiagramId())) {
            logger.warn("Evaluation submitted for non-existent diagram: {}", request.getDiagramId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Diagram not found with ID: " + request.getDiagramId()));
        }

        // Create and save evaluation
        DiagramEvaluation evaluation = new DiagramEvaluation(
                request.getDiagramId(),
                request.getClarityScore(),
                request.getCorrectnessScore(),
                request.getUsefulnessScore(),
                request.getComment()
        );

        evaluation = evaluationRepository.save(evaluation);
        logger.info("Saved evaluation with ID: {}", evaluation.getId());

        EvaluationResponse response = EvaluationResponse.builder()
                .id(evaluation.getId())
                .diagramId(evaluation.getDiagramId())
                .clarityScore(evaluation.getClarityScore())
                .correctnessScore(evaluation.getCorrectnessScore())
                .usefulnessScore(evaluation.getUsefulnessScore())
                .averageScore(evaluation.getAverageScore())
                .comment(evaluation.getComment())
                .evaluatedAt(evaluation.getEvaluatedAt())
                .message("Evaluation submitted successfully")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evaluation submitted successfully", response));
    }

    /**
     * Gets all evaluations for a specific diagram.
     *
     * @param id the diagram UUID
     * @return list of evaluations
     */
    @GetMapping("/{id}/evaluations")
    @Operation(summary = "Get evaluations for diagram", description = "Returns all human evaluations for a diagram")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of evaluations")
    public ResponseEntity<ApiResponse<List<EvaluationResponse>>> getDiagramEvaluations(@PathVariable UUID id) {
        logger.debug("Fetching evaluations for diagram: {}", id);

        List<EvaluationResponse> evaluations = evaluationRepository.findByDiagramId(id).stream()
                .map(eval -> EvaluationResponse.builder()
                        .id(eval.getId())
                        .diagramId(eval.getDiagramId())
                        .clarityScore(eval.getClarityScore())
                        .correctnessScore(eval.getCorrectnessScore())
                        .usefulnessScore(eval.getUsefulnessScore())
                        .averageScore(eval.getAverageScore())
                        .comment(eval.getComment())
                        .evaluatedAt(eval.getEvaluatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(evaluations));
    }

    /**
     * Gets aggregated evaluation metrics for a diagram.
     *
     * @param id the diagram UUID
     * @return evaluation metrics
     */
    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get evaluation metrics", description = "Returns aggregated evaluation metrics for a diagram")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Evaluation metrics")
    public ResponseEntity<ApiResponse<EvaluationMetricsResponse>> getDiagramMetrics(@PathVariable UUID id) {
        logger.debug("Fetching metrics for diagram: {}", id);

        return evaluationRepository.findMetricsByDiagramId(id)
                .map(metrics -> {
                    EvaluationMetricsResponse response = new EvaluationMetricsResponse(
                            id,
                            metrics.getAvgClarity(),
                            metrics.getAvgCorrectness(),
                            metrics.getAvgUsefulness(),
                            metrics.getTotalEvaluations()
                    );
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(
                        new EvaluationMetricsResponse(id, null, null, null, 0L))));
    }

    /**
     * DTO for diagram type information.
     */
    public record DiagramTypeInfo(String name, String code, String displayName) {
    }

    /**
     * DTO for evaluation metrics.
     */
    public record EvaluationMetricsResponse(
            UUID diagramId,
            Double avgClarity,
            Double avgCorrectness,
            Double avgUsefulness,
            Long totalEvaluations
    ) {
    }
}
