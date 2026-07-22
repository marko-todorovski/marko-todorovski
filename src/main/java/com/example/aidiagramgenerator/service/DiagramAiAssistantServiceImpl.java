package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.dto.response.DiagramExplanationElementResponse;
import com.example.aidiagramgenerator.dto.response.DiagramExplanationRelationshipResponse;
import com.example.aidiagramgenerator.dto.response.DiagramExplanationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramModificationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramSuggestionItemResponse;
import com.example.aidiagramgenerator.dto.response.DiagramSuggestionsResponse;
import com.example.aidiagramgenerator.exception.DiagramAiException;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DiagramAiAssistantServiceImpl implements DiagramAiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(DiagramAiAssistantServiceImpl.class);
    private static final int MAX_SOURCE_LENGTH = 20_000;
    private final DomainDiagramRepository diagramRepository;
    private final DiagramAiAssistantClient assistantClient;
    private final DiagramRenderingService renderingService;
    private final DiagramAiAssistantRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final ProjectAccessService projectAccessService;

    public DiagramAiAssistantServiceImpl(
            DomainDiagramRepository diagramRepository,
            DiagramAiAssistantClient assistantClient,
            DiagramRenderingService renderingService,
            DiagramAiAssistantRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            ProjectAccessService projectAccessService) {
        this.diagramRepository = diagramRepository;
        this.assistantClient = assistantClient;
        this.renderingService = renderingService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.projectAccessService = projectAccessService;
    }

    @Override
    @Transactional(readOnly = true)
    public DiagramExplanationResponse explainDiagram(UUID diagramId, UUID ownerId, String sourceCode) {
        Instant start = Instant.now();
        Diagram diagram = requireOwnedPlantUmlDiagram(diagramId, ownerId);
        String source = requirePlantUmlSource(sourceCode == null ? currentSource(diagram) : sourceCode);
        rateLimiter.check(ownerId);
        String prompt = """
                Explain this PlantUML software engineering diagram. Return JSON only with keys:
                summary, elements [{name,type,description}], relationships [{from,to,description}], flow, risks [].
                Do not modify the source and do not include PlantUML in the response.

                PlantUML:
                %s
                """.formatted(source);
        String response = callAi("explain", diagramId, ownerId, prompt, start);
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFences(response));
            return new DiagramExplanationResponse(
                    text(root, "summary"),
                    elements(root.path("elements")),
                    relationships(root.path("relationships")),
                    text(root, "flow"),
                    strings(root.path("risks")),
                    text(root, "explanation"));
        } catch (Exception e) {
            return DiagramExplanationResponse.plain(stripMarkdownFences(response));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DiagramSuggestionsResponse suggestImprovements(UUID diagramId, UUID ownerId, String sourceCode, DiagramType diagramType, String focus) {
        Instant start = Instant.now();
        Diagram diagram = requireOwnedPlantUmlDiagram(diagramId, ownerId);
        String source = requirePlantUmlSource(sourceCode == null ? currentSource(diagram) : sourceCode);
        rateLimiter.check(ownerId);
        DiagramType resolvedType = diagramType == null ? diagram.getDiagramType() : diagramType;
        String resolvedFocus = focus == null || focus.isBlank() ? "general" : focus;
        String prompt = """
                Suggest focused improvements for this %s PlantUML diagram. Focus: %s.
                Return JSON only with keys: summary, suggestions [{title,description,priority}].
                Do not return modified source code.

                PlantUML:
                %s
                """.formatted(resolvedType, resolvedFocus, source);
        String response = callAi("suggestions", diagramId, ownerId, prompt, start);
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFences(response));
            return new DiagramSuggestionsResponse(text(root, "summary"), suggestions(root.path("suggestions")));
        } catch (Exception e) {
            return new DiagramSuggestionsResponse("AI suggestions", List.of(
                    new DiagramSuggestionItemResponse("Review diagram", stripMarkdownFences(response), "MEDIUM")));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DiagramModificationResponse modifyDiagram(UUID diagramId, UUID ownerId, String instruction, String sourceCode, DiagramType diagramType) {
        Instant start = Instant.now();
        requireOwnedPlantUmlDiagram(diagramId, ownerId);
        String source = requirePlantUmlSource(sourceCode);
        if (instruction == null || instruction.isBlank()) {
            throw invalid("AI modification instruction is required");
        }
        rateLimiter.check(ownerId);
        String prompt = """
                Modify this %s PlantUML diagram according to the instruction.
                Return JSON only with keys: sourceCode, summary, warnings.
                The sourceCode value must be valid PlantUML only, include @startuml and @enduml,
                contain no Markdown fences, avoid external includes, retain unrelated content,
                and apply only the requested change.

                Instruction:
                %s

                Current PlantUML:
                %s
                """.formatted(diagramType, instruction, source);
        String response = callAi("modify", diagramId, ownerId, prompt, start);
        ModificationPayload payload = parseModification(response);
        String proposal = requireValidAiProposal(payload.sourceCode());
        validateRenderableProposal(proposal);
        return new DiagramModificationResponse(
                proposal,
                payload.summary() == null || payload.summary().isBlank() ? "AI proposal generated." : payload.summary(),
                assistantClient.getModelName(),
                payload.warnings());
    }

    private Diagram requireOwnedPlantUmlDiagram(UUID diagramId, UUID ownerId) {
        Diagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found"));
        projectAccessService.requireDiagramEditor(diagram, ownerId);
        if (diagram.getSourceFormat() != null && diagram.getSourceFormat() != DiagramSourceFormat.PLANTUML) {
            throw new DiagramAiException(HttpStatus.BAD_REQUEST, "AI_UNSUPPORTED_FORMAT", "AI editing supports PlantUML diagrams only.");
        }
        return diagram;
    }

    private String callAi(String operation, UUID diagramId, UUID ownerId, String prompt, Instant start) {
        try {
            String response = assistantClient.generateStructuredResponse(prompt);
            if (response == null || response.isBlank()) {
                throw new DiagramAiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_INVALID", "AI returned an invalid diagram proposal.");
            }
            log.info("Diagram AI operation succeeded: operation={}, diagramId={}, ownerId={}, model={}, durationMs={}",
                    operation, diagramId, ownerId, assistantClient.getModelName(), Duration.between(start, Instant.now()).toMillis());
            return response;
        } catch (DiagramAiException e) {
            throw e;
        } catch (AiServiceException e) {
            throw providerException(e);
        } catch (RuntimeException e) {
            throw providerException(e);
        }
    }

    private DiagramAiException providerException(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return new DiagramAiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI request timed out. Try again.");
        }
        return new DiagramAiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "AI service is unavailable. Try again later.");
    }

    private void validateRenderableProposal(String proposal) {
        try {
            renderingService.renderToSvg(proposal);
        } catch (IllegalArgumentException | DiagramRenderingException e) {
            throw new DiagramAiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_INVALID", "AI returned an invalid diagram proposal.");
        }
    }

    private String requirePlantUmlSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw invalid("PlantUML source code is required");
        }
        if (sourceCode.length() > MAX_SOURCE_LENGTH) {
            throw invalid("PlantUML source code is too long");
        }
        String source = stripMarkdownFences(sourceCode);
        String trimmed = source.trim();
        if (!trimmed.startsWith("@startuml") || !trimmed.endsWith("@enduml")) {
            throw invalid("PlantUML source must start with @startuml and end with @enduml");
        }
        if (hasUnsafeIncludeDirective(trimmed)) {
            throw new DiagramAiException(HttpStatus.BAD_REQUEST, "UNSAFE_PLANTUML", "PlantUML source contains an unsafe include directive.");
        }
        return source;
    }

    private String requireValidAiProposal(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw invalidAiOutput();
        }
        if (sourceCode.length() > MAX_SOURCE_LENGTH) {
            throw invalidAiOutput();
        }
        String source = stripMarkdownFences(sourceCode);
        String trimmed = source.trim();
        if (!trimmed.startsWith("@startuml") || !trimmed.endsWith("@enduml")) {
            throw invalidAiOutput();
        }
        if (hasUnsafeIncludeDirective(trimmed)) {
            throw invalidAiOutput();
        }
        return source;
    }

    private static boolean hasUnsafeIncludeDirective(String source) {
        for (String line : source.split("\\R")) {
            String normalized = line.strip().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("!includeurl")
                    || normalized.startsWith("!include http://")
                    || normalized.startsWith("!include https://")
                    || normalized.startsWith("!include /")
                    || normalized.startsWith("!include \\")
                    || normalized.contains("../")
                    || normalized.contains("..\\")) {
                return true;
            }
        }
        return false;
    }

    private static DiagramAiException invalid(String message) {
        return new DiagramAiException(HttpStatus.BAD_REQUEST, "INVALID_AI_REQUEST", message);
    }

    private static DiagramAiException invalidAiOutput() {
        return new DiagramAiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_OUTPUT_INVALID", "AI returned an invalid diagram proposal.");
    }

    private static String currentSource(Diagram diagram) {
        if (diagram.getCurrentSourceCode() != null && !diagram.getCurrentSourceCode().isBlank()) {
            return diagram.getCurrentSourceCode();
        }
        return diagram.getPlantUmlCode();
    }

    private ModificationPayload parseModification(String response) {
        String cleaned = stripMarkdownFences(response);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            String source = text(root, "sourceCode");
            if (source == null) source = text(root, "plantUml");
            if (source == null) source = text(root, "plantUmlCode");
            return new ModificationPayload(stripMarkdownFences(source), text(root, "summary"), strings(root.path("warnings")));
        } catch (Exception ignored) {
            return new ModificationPayload(cleaned, "AI proposal generated.", List.of());
        }
    }

    static String stripMarkdownFences(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isNull()) values.add(item.asText());
        });
        return values;
    }

    private static List<DiagramExplanationElementResponse> elements(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<DiagramExplanationElementResponse> values = new ArrayList<>();
        node.forEach(item -> values.add(new DiagramExplanationElementResponse(
                text(item, "name"), text(item, "type"), text(item, "description"))));
        return values;
    }

    private static List<DiagramExplanationRelationshipResponse> relationships(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<DiagramExplanationRelationshipResponse> values = new ArrayList<>();
        node.forEach(item -> values.add(new DiagramExplanationRelationshipResponse(
                text(item, "from"), text(item, "to"), text(item, "description"))));
        return values;
    }

    private static List<DiagramSuggestionItemResponse> suggestions(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<DiagramSuggestionItemResponse> values = new ArrayList<>();
        node.forEach(item -> values.add(new DiagramSuggestionItemResponse(
                text(item, "title"), text(item, "description"), text(item, "priority"))));
        return values;
    }

    private record ModificationPayload(String sourceCode, String summary, List<String> warnings) {
    }
}
