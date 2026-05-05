package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.DiagramRequest;
import com.example.aidiagramgenerator.dto.response.OpenAiDiagramResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for generating diagrams using the OpenAI Chat Completions API.
 *
 * <p>Accepts a {@link DiagramRequest} containing input text, extracted entities,
 * relationships, and detected intent, then calls GPT-4o-mini (configurable) to
 * produce PlantUML code, diagram type, and an explanation.
 *
 * <p>On any API failure the service falls back to {@link RuleBasedDiagramService}
 * so diagram generation always succeeds.
 *
 * <p>Configuration properties:
 * <pre>
 * openai.api.url=https://api.openai.com/v1/chat/completions
 * openai.api.key=${OPENAI_API_KEY}
 * openai.diagram.model=gpt-4o-mini
 * </pre>
 */
@Service
public class OpenAiDiagramService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiDiagramService.class);

    private static final String SYSTEM_PROMPT =
            "You are an expert software diagram generator. " +
            "Respond only with valid JSON. " +
            "Do not include markdown code blocks or any other formatting.";

    private static final String PROMPT_TEMPLATE = """
            Based on the following structured input, generate a PlantUML diagram.

            Input text: %s
            Entities: %s
            Relationships: %s
            Detected intent: %s

            Respond with a JSON object containing exactly these fields:
            {
                "diagramType": "<one of: sequence, class, component, usecase, state, activity, object, microservices>",
                "plantUmlCode": "<complete valid PlantUML starting with @startuml and ending with @enduml>",
                "explanation": "<brief 1-2 sentence explanation of the diagram>"
            }
            """;

    private final WebClient webClient;
    private final RuleBasedDiagramService ruleBasedDiagramService;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    public OpenAiDiagramService(
            @Qualifier("openAiWebClient") WebClient webClient,
            RuleBasedDiagramService ruleBasedDiagramService,
            ObjectMapper objectMapper,
            @Value("${openai.diagram.model:gpt-4o-mini}") String model,
            @Value("${openai.api.key:}") String apiKey) {
        this.webClient = webClient;
        this.ruleBasedDiagramService = ruleBasedDiagramService;
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
        log.info("OpenAiDiagramService initialized with AI provider: {}", model);
    }

    /**
     * Generates a diagram from the given structured request.
     *
     * <p>Calls the OpenAI Chat Completions API and parses the JSON response.
     * Falls back to {@link RuleBasedDiagramService} on any failure.
     *
     * @param request the structured diagram request
     * @return the generated diagram response
     */
    public OpenAiDiagramResponse generateDiagram(DiagramRequest request) {
        String prompt = buildPrompt(request);
        log.debug("Sending prompt to LLM:\n{}", prompt);

        Instant start = Instant.now();
        try {
            String rawResponse = callOpenAi(prompt);
            log.debug("Response received from LLM: {}", rawResponse);

            OpenAiDiagramResponse result = parseContent(extractContent(rawResponse));
            result.setModelUsed(model);
            result.setGenerationTimeMs(Duration.between(start, Instant.now()).toMillis());
            return result;
        } catch (Exception e) {
            log.warn("OpenAI call failed, falling back to rule-based generator: {}", e.getMessage());
            log.info("Using rule-based fallback for diagram generation");
            return ruleBasedDiagramService.generate(request);
        }
    }

    /**
     * Returns the name of the configured AI model.
     */
    public String getModelName() {
        return model;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String callOpenAi(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user",   "content", prompt)
                ),
                "temperature", 0.7,
                "response_format", Map.of("type", "json_object")
        );

        return webClient.post()
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String extractContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("Missing or empty 'choices' in OpenAI response");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("Missing 'content' field in OpenAI response");
            }
            return content.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    private OpenAiDiagramResponse parseContent(String content) {
        try {
            JsonNode node = objectMapper.readTree(content);
            String diagramType  = getText(node, "diagramType");
            String plantUmlCode = getText(node, "plantUmlCode");
            String explanation  = getText(node, "explanation");

            if (plantUmlCode == null || plantUmlCode.isBlank()) {
                throw new IllegalStateException("Missing 'plantUmlCode' field in LLM response");
            }

            return OpenAiDiagramResponse.builder()
                    .diagramType(diagramType != null ? diagramType : "unknown")
                    .plantUmlCode(plantUmlCode)
                    .explanation(explanation)
                    .fallbackUsed(false)
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM content as JSON: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(DiagramRequest request) {
        String entities = request.getEntities() != null
                ? String.join(", ", request.getEntities()) : "none";
        String relationships = request.getRelationships() != null
                ? String.join(", ", request.getRelationships()) : "none";
        String intent = request.getDetectedIntent() != null
                ? request.getDetectedIntent() : "unknown";

        return String.format(PROMPT_TEMPLATE,
                request.getInputText(),
                entities,
                relationships,
                intent);
    }

    private String getText(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : null;
    }
}
