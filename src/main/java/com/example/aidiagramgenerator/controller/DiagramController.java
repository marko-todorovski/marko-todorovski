package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.request.DiagramEvaluationRequest;
import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UrlDiagramRequest;
import com.example.aidiagramgenerator.dto.request.XmlDiagramRequest;
import com.example.aidiagramgenerator.dto.response.ApiResponse;
import com.example.aidiagramgenerator.dto.response.DiagramEvaluationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramMetricsResponse;
import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.dto.response.DiagramVersionResponse;
import com.example.aidiagramgenerator.dto.response.GenerationResult;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.entity.DiagramEvaluation;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.exception.DiagramGenerationException;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidDiagramRequestException;
import com.example.aidiagramgenerator.repository.DiagramEvaluationRepository;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.service.export.DrawIoExportService;
import com.example.aidiagramgenerator.service.DiagramAnalyticsService;
import com.example.aidiagramgenerator.service.ConfidenceDiagramService;
import com.example.aidiagramgenerator.service.DiagramCreationService;
import com.example.aidiagramgenerator.service.DiagramGenerationService;
import com.example.aidiagramgenerator.service.DiagramGenerationService.DiagramResult;
import com.example.aidiagramgenerator.service.MermaidRenderer;
import com.example.aidiagramgenerator.service.PdfDiagramClassifier;
import com.example.aidiagramgenerator.service.PdfExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * REST Controller for diagram generation.
 */
@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "Diagram Generation", description = "APIs for generating software engineering diagrams")
public class DiagramController {

    private static final Logger logger = LoggerFactory.getLogger(DiagramController.class);

    private final DiagramGenerationService diagramGenerationService;
    private final DiagramCreationService diagramCreationService;
    private final DiagramRepository diagramRepository;
    private final DiagramEvaluationRepository diagramEvaluationRepository;
    private final DiagramAnalyticsService diagramAnalyticsService;
    private final MermaidRenderer mermaidRenderer;
    private final RestClient restClient;
    private final DrawIoExportService drawIoExportService;
    private final PdfExtractionService pdfExtractionService;
    private final ConfidenceDiagramService confidenceDiagramService;
    private final PdfDiagramClassifier pdfDiagramClassifier;

    public DiagramController(DiagramGenerationService diagramGenerationService,
                             DiagramCreationService diagramCreationService,
                             DiagramRepository diagramRepository,
                             DiagramEvaluationRepository diagramEvaluationRepository,
                             DiagramAnalyticsService diagramAnalyticsService,
                             MermaidRenderer mermaidRenderer,
                             RestClient restClient,
                             DrawIoExportService drawIoExportService,
                             PdfExtractionService pdfExtractionService,
                             ConfidenceDiagramService confidenceDiagramService,
                             PdfDiagramClassifier pdfDiagramClassifier) {
        this.diagramGenerationService = diagramGenerationService;
        this.diagramCreationService = diagramCreationService;
        this.diagramRepository = diagramRepository;
        this.diagramEvaluationRepository = diagramEvaluationRepository;
        this.diagramAnalyticsService = diagramAnalyticsService;
        this.mermaidRenderer = mermaidRenderer;
        this.restClient = restClient;
        this.drawIoExportService = drawIoExportService;
        this.pdfExtractionService = pdfExtractionService;
        this.confidenceDiagramService = confidenceDiagramService;
        this.pdfDiagramClassifier = pdfDiagramClassifier;
    }

    // ---- Endpoints ----

    /**
     * Generate a diagram from natural language text.
     */
    @PostMapping("/from-text")
    @Operation(summary = "Generate diagram from natural language text",
               description = "Analyzes natural language description and generates appropriate diagram type")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Diagram generated successfully",
                     content = @Content(schema = @Schema(implementation = DiagramResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<DiagramResponse>> generateFromText(@Valid @RequestBody TextDiagramRequest request) {
        logger.info("Received manual diagram generation request — diagramType={}, descriptionLength={}, description='{}'",
                request.getDiagramType(),
                request.getText() != null ? request.getText().length() : 0,
                request.getText());

        DiagramResponse response = diagramCreationService.generateAndSave(
                InputType.TEXT, request.getText(), request.getText(), request.getDiagramType());
        return diagramCreated(response);
    }

    /**
     * Generate a diagram from an XML string.
     * Extracts text content from the XML and passes it to the generation service.
     */
    @PostMapping("/from-xml")
    @Operation(summary = "Generate diagram from XML",
               description = "Extracts text from XML and generates appropriate diagram type")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Diagram generated successfully",
                     content = @Content(schema = @Schema(implementation = DiagramResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid XML input"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<DiagramResponse>> generateFromXml(@Valid @RequestBody XmlDiagramRequest request) {
        logger.info("Received request to generate diagram from XML");

        String extractedText = extractTextFromXml(request.getXml());
        DiagramResponse response = diagramCreationService.generateAndSave(
                InputType.XML, request.getXml(), extractedText, null);
        return diagramCreated(response);
    }

    /**
     * Generate a diagram from a URL.
     * Fetches the page content and passes it to the generation service.
     */
    @PostMapping("/from-url")
    @Operation(summary = "Generate diagram from URL",
               description = "Fetches content from URL and generates appropriate diagram type")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Diagram generated successfully",
                     content = @Content(schema = @Schema(implementation = DiagramResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid URL"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<DiagramResponse>> generateFromUrl(@Valid @RequestBody UrlDiagramRequest request) {
        logger.info("Received request to generate diagram from URL: {}", request.getUrl());

        String pageContent = fetchUrlContent(request.getUrl());
        DiagramResponse response = diagramCreationService.generateAndSave(
                InputType.URL, request.getUrl(), pageContent, null);
        return diagramCreated(response);
    }

    /**
     * Generate a diagram from an uploaded PDF file.
     * Extracts text from the PDF and passes it to the diagram generation pipeline.
     */
    @PostMapping(value = "/from-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Generate diagram from PDF",
               description = "Extracts text from an uploaded PDF and generates an appropriate diagram")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Diagram generated successfully",
                     content = @Content(schema = @Schema(implementation = GenerationResult.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or empty PDF"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiResponse<GenerationResult>> generateFromPdf(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            logger.warn("PDF upload rejected: file is null or empty");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No file was uploaded or the file is empty"));
        }

        logger.info("Received PDF upload — file: '{}', size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            String extractedText = pdfExtractionService.extractText(file);

            // Trim to 2000 chars before sending to generation pipeline
            String textForGeneration = extractedText.length() > 2000
                    ? extractedText.substring(0, 2000)
                    : extractedText;

            // PDF-specific pre-classification — runs before the normal classifier
            // to prevent overlapping keywords causing misclassification.
            com.example.aidiagramgenerator.domain.DiagramType pdfDetectedType =
                    pdfDiagramClassifier.detect(extractedText);

            GenerationResult result;
            if (pdfDetectedType != null) {
                logger.info("[PDF] Pre-classifier detected type={}, confidence=100 — skipping normal classifier",
                        pdfDetectedType);
                result = confidenceDiagramService.process(textForGeneration, pdfDetectedType.name(), null, true);
            } else {
                // Auto-detect type and generate; if confidence is too low for auto-generation,
                // re-run with the suggested type forced so PDFs always produce a diagram.
                result = confidenceDiagramService.process(textForGeneration, null, null, false);
                if (!"AUTO".equals(result.getDecision())) {
                    String fallbackType = result.getDiagramType() != null
                            ? result.getDiagramType().name()
                            : "SEQUENCE";
                    logger.info("PDF auto-detect returned decision='{}' — forcing generation with type={}",
                            result.getDecision(), fallbackType);
                    result = confidenceDiagramService.process(textForGeneration, fallbackType, null, true);
                }
            }

            String preview = extractedText.length() > 500
                    ? extractedText.substring(0, 500)
                    : extractedText;
            result.setExtractedTextPreview(preview);

            logger.info("PDF diagram generated successfully (id: {}, type: {}, confidence: {}, mode: {})",
                    result.getId(), result.getDiagramType(), result.getConfidenceScore(), result.getGenerationMode());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Diagram generated successfully", result));

        } catch (InvalidDiagramRequestException e) {
            logger.warn("PDF upload rejected (400) — file: '{}': {}",
                    file.getOriginalFilename(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (DiagramGenerationException e) {
            logger.error("Diagram generation failed for PDF '{}': {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate diagram from PDF"));

        } catch (Exception e) {
            logger.error("Unexpected error processing PDF '{}': {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An unexpected error occurred while processing the PDF"));
        }
    }

    /**
     * Generate a diagram from text and download it directly as a Draw.io file.
     */
    @PostMapping("/download")
    @Operation(summary = "Generate and download diagram as Draw.io file",
               description = "Generates a diagram from text and returns it as a downloadable Draw.io XML file")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draw.io XML file generated successfully",
                     content = @Content(mediaType = "application/xml")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<byte[]> generateAndDownload(@Valid @RequestBody TextDiagramRequest request) {
        logger.info("Received request to generate and download diagram as Draw.io");

        DiagramResult result = diagramGenerationService.generateFromText(
                request.getText(), request.getDiagramType());

        // Create a temporary diagram entity for conversion
        Diagram diagram = new Diagram(
                InputType.TEXT,
                request.getText(),
                result.getDiagramType(),
                result.getMermaidCode(),
                result.getExplanation());

        String xmlContent = drawIoExportService.convertToDrawIoXml(diagram);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagram.drawio\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xmlContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get a diagram by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get diagram by ID",
               description = "Retrieves a previously generated Mermaid diagram by its ID")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diagram found",
                     content = @Content(schema = @Schema(implementation = DiagramResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<DiagramResponse>> getDiagramById(@PathVariable UUID id) {
        logger.info("Fetching diagram id={}", id);

        return diagramRepository.findById(id)
                .map(diagram -> {
                    DiagramResponse response = new DiagramResponse(
                            diagram.getDiagramType(),
                            diagram.getMermaidCode(),
                            diagram.getExplanation());
                    response.setId(diagram.getId());
                    return ResponseEntity.ok(ApiResponse.success(response));
                })
                .orElseGet(() -> {
                    logger.warn("Diagram not found: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Diagram not found with ID: " + id));
                });
    }

    /**
     * Evaluate a previously generated diagram.
     */
    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Evaluate a diagram",
               description = "Submit clarity, correctness, and usefulness scores (1–5) for a generated diagram")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Evaluation saved",
                     content = @Content(schema = @Schema(implementation = DiagramEvaluationResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid scores"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<DiagramEvaluationResponse>> evaluate(
            @PathVariable UUID id,
            @Valid @RequestBody DiagramEvaluationRequest request) {

        logger.info("Received evaluation for diagram id={}", id);

        // Check diagram exists
        if (!diagramRepository.existsById(id)) {
            logger.warn("Diagram not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Diagram not found with ID: " + id));
        }

        // Persist evaluation
        DiagramEvaluation evaluation = new DiagramEvaluation(
                id,
                request.getClarityScore(),
                request.getCorrectnessScore(),
                request.getUsefulnessScore());
        diagramEvaluationRepository.save(evaluation);

        // Calculate average
        double avg = BigDecimal.valueOf(
                (request.getClarityScore() + request.getCorrectnessScore() + request.getUsefulnessScore()) / 3.0)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        DiagramEvaluationResponse response = new DiagramEvaluationResponse(
                id,
                request.getClarityScore(),
                request.getCorrectnessScore(),
                request.getUsefulnessScore(),
                avg);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evaluation saved", response));
    }

    /**
     * Render a diagram as a PNG-equivalent image.
     * Uses the Mermaid renderer to produce an SVG served as {@code image/svg+xml},
     * which is displayable directly in {@code <img>} tags.
     */
    @GetMapping(value = "/{id}/png", produces = "image/svg+xml")
    @Operation(summary = "Render diagram as image",
               description = "Renders the stored Mermaid code and returns it as a displayable image")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image returned",
                     content = @Content(mediaType = "image/svg+xml")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Rendering failed")
    })
    public ResponseEntity<byte[]> getDiagramPng(@PathVariable UUID id) {
        Diagram diagram = diagramRepository.findById(id).orElse(null);
        if (diagram == null) {
            logger.warn("Diagram not found for PNG render: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        logger.info("Rendering diagram id={} as image", id);
        try {
            String svg = mermaidRenderer.renderToSvg(diagram.getMermaidCode());
            byte[] svgBytes = svg.getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("image/svg+xml"));
            headers.setContentLength(svgBytes.length);
            return new ResponseEntity<>(svgBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Failed to render diagram id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Render a diagram as SVG.
     */
    @GetMapping(value = "/{id}/svg", produces = "image/svg+xml")
    @Operation(summary = "Render diagram as SVG",
               description = "Renders the stored Mermaid code and returns it as SVG")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SVG returned",
                     content = @Content(mediaType = "image/svg+xml")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Rendering failed")
    })
    public ResponseEntity<byte[]> getDiagramSvg(@PathVariable UUID id) {
        return renderStoredMermaidSvg(id);
    }

    /**
     * Export a diagram as SVG.
     */
    @GetMapping(value = "/{id}/export", produces = "image/svg+xml")
    @Operation(summary = "Export diagram as SVG",
               description = "Renders the stored Mermaid code to SVG")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SVG returned",
                     content = @Content(mediaType = "image/svg+xml")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported format"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<byte[]> exportDiagram(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "svg") String format) {

        if (!"svg".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().build();
        }

        return renderStoredMermaidSvg(id);
    }

    private ResponseEntity<byte[]> renderStoredMermaidSvg(UUID id) {
        Diagram diagram = diagramRepository.findById(id).orElse(null);
        if (diagram == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        logger.info("Rendering diagram id={} as SVG", id);

        String svg = mermaidRenderer.renderToSvg(diagram.getMermaidCode());
        byte[] svgBytes = svg.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("image/svg+xml"));
        headers.setContentLength(svgBytes.length);

        return new ResponseEntity<>(svgBytes, headers, HttpStatus.OK);
    }

    /**
     * Get aggregated metrics for a diagram's evaluations.
     */
    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get diagram evaluation metrics",
               description = "Returns aggregated evaluation metrics (average clarity, correctness, usefulness) for a diagram")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Metrics returned successfully",
                     content = @Content(schema = @Schema(implementation = DiagramMetricsResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<DiagramMetricsResponse>> getDiagramMetrics(@PathVariable UUID id) {
        logger.info("Fetching metrics for diagram id={}", id);

        return diagramAnalyticsService.getDiagramMetrics(id)
                .map(m -> ResponseEntity.ok(ApiResponse.success(m)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Diagram not found with ID: " + id)));
    }

    /**
     * Get all versions of a diagram.
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "Get diagram versions",
               description = "Returns all versions of a diagram ordered by version number ascending")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Versions returned successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<ApiResponse<List<DiagramVersionResponse>>> getDiagramVersions(@PathVariable UUID id) {
        logger.info("Fetching versions for diagram id={}", id);

        if (!diagramRepository.existsById(id)) {
            logger.warn("Diagram not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Diagram not found with ID: " + id));
        }

        List<Diagram> versions = diagramRepository.findAllVersionsByDiagramId(id);
        List<DiagramVersionResponse> response = versions.stream()
                .map(d -> new DiagramVersionResponse(
                        d.getId(),
                        d.getVersionNumber(),
                        d.getParentDiagramId(),
                        d.getDiagramType(),
                        d.getMermaidCode(),
                        d.getExplanation(),
                        d.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Download a diagram as a Draw.io compatible XML file.
     */
    @GetMapping({"/{id}/download", "/{id}/drawio"})
    @Operation(summary = "Download diagram as Draw.io file",
               description = "Exports the diagram as a Draw.io compatible XML file (.drawio)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draw.io XML file generated successfully",
                     content = @Content(mediaType = "application/xml")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<byte[]> downloadDiagram(@PathVariable UUID id) {
        logger.info("Downloading diagram id={} as Draw.io XML", id);

        Diagram diagram = diagramRepository.findById(id)
                .orElseThrow(() -> new DiagramNotFoundException(
                        "Diagram not found with ID: " + id));

        String xmlContent = drawIoExportService.convertToDrawIoXml(diagram);
        String filename = drawIoExportService.generateFilename(diagram);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xmlContent.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Shared helpers ----

    private ResponseEntity<ApiResponse<DiagramResponse>> diagramCreated(DiagramResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Diagram generated successfully", response));
    }

    /**
     * Extract text content from an XML string by stripping all tags.
     */
    private String extractTextFromXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            var document = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String textContent = document.getDocumentElement().getTextContent();
            // If extraction yields little text, fall back to raw XML so keyword detection still works
            return (textContent != null && textContent.trim().length() >= 10)
                    ? textContent.trim()
                    : xml;
        } catch (Exception e) {
            logger.warn("XML parsing failed, falling back to raw content: {}", e.getMessage());
            return xml;
        }
    }

    /**
     * Fetch the text body from the given URL using {@link RestClient}.
     */
    private String fetchUrlContent(String url) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new DiagramGenerationException("Empty response from URL: " + url);
            }
            // Trim to a reasonable length for keyword analysis
            return body.length() > 5000 ? body.substring(0, 5000) : body;
        } catch (DiagramGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new DiagramGenerationException("Failed to fetch content from URL: " + url + " — " + e.getMessage(), e);
        }
    }
}
