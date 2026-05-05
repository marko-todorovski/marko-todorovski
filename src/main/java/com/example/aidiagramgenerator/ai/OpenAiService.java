package com.example.aidiagramgenerator.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OpenAI GPT-4o implementation of {@link AiModelService}.
 * 
 * <p>This service integrates with the OpenAI Chat Completion API using GPT-4o
 * for research experiments comparing cloud-based AI performance.
 * 
 * <p>Configuration:
 * <pre>
 * openai.api.key=your-api-key
 * openai.api.url=https://api.openai.com/v1/chat/completions
 * openai.model=gpt-4o
 * </pre>
 * 
 * <p>Bean creation is managed by {@link com.example.aidiagramgenerator.config.AiProviderConfig}.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public class OpenAiService implements AiModelService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String diagramModel;

    /**
     * Constructs the OpenAI service with required dependencies.
     * 
     * @param restClientBuilder RestClient builder for HTTP requests
     * @param apiKey OpenAI API key from configuration
     * @param apiUrl OpenAI API endpoint URL
     * @param model OpenAI model identifier (e.g., gpt-4o)
     */
    public OpenAiService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            String apiKey,
            String apiUrl,
            String model,
            String diagramModel) {
        this.restClient = restClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.diagramModel = diagramModel;
        
        log.info("OpenAiService initialized — classification model: {}, diagram model: {} at URL: {}", model, diagramModel, apiUrl);
        log.debug("API key configured: {}", apiKey != null && !apiKey.isBlank() ? "yes (length=" + apiKey.length() + ")" : "no");
    }

    /**
     * Returns the configured API URL.
     * 
     * @return the OpenAI API endpoint URL
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * Checks if the API key is configured.
     * 
     * @return true if the API key is non-null and non-blank
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getModelName() {
        return "GPT-4o";
    }

    /**
     * Calls the LLM with a plain-text prompt and returns a structured {@link LlmResult}.
     *
     * <p>This method never throws — any API failure is captured as
     * {@link LlmResult#failure()}.
     *
     * @param prompt the text prompt to send to the LLM
     * @return {@link LlmResult#success(String)} on success, {@link LlmResult#failure()} otherwise
     */
    @Override
    public LlmResult callLLM(String prompt) {
        if (!isApiKeyConfigured()) {
            log.warn("OpenAI API key is not configured — LLM unavailable");
            return LlmResult.failure();
        }

        log.debug("Calling LLM with prompt (length: {} chars) via {}", prompt.length(), apiUrl);
        Instant start = Instant.now();

        try {
            Map<String, Object> requestBody = buildPlainTextRequestBody(prompt);

            String response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("LLM response received in {} ms", elapsed.toMillis());

            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("LLM returned blank content after {} ms", elapsed.toMillis());
                return LlmResult.failure();
            }

            log.info("LLM_USED - AI generated response ({} chars)", content.length());
            return LlmResult.success(content);

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("LLM call failed after {} ms: {}", elapsed.toMillis(), e.getMessage());
            return LlmResult.failure();
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Sends a prompt to OpenAI GPT-4o and returns the raw JSON response content.
     * Response time is logged for research metrics collection.
     */
    @Override
    public String generateStructuredResponse(String prompt) {
        log.debug("Sending prompt to OpenAI (length: {} chars)", prompt.length());
        
        Instant start = Instant.now();
        
        try {
            Map<String, Object> requestBody = buildRequestBody(prompt);
            
            String response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("OpenAI response received in {} ms", elapsed.toMillis());
            
            String content = extractContent(response);
            log.debug("Extracted content length: {} chars", content.length());
            
            return content;
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("OpenAI request failed after {} ms: {}", elapsed.toMillis(), e.getMessage());
            throw new AiServiceException("Failed to get response from OpenAI: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a plain-text request body for {@link #callLLM}.
     * Includes a system message for better semantic extraction quality.
     */
    private Map<String, Object> buildPlainTextRequestBody(String prompt) {
        return Map.of(
                "model", diagramModel,
                "messages", List.of(
                        Map.of("role", "system",
                               "content", "You are an expert software architect. "
                                       + "Extract entities, relationships, and suggest the best diagram type. "
                                       + "Return only valid Mermaid diagram code without markdown fences or explanation."),
                        Map.of("role", "user", "content", prompt)
                )
        );
    }

    /**
     * Builds the OpenAI Chat Completion API request body for structured JSON responses.
     * 
     * @param prompt the user prompt
     * @return the request body as a map
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are a helpful assistant that responds only with valid JSON. Do not include markdown code blocks or any other formatting."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", 0.7,
                "response_format", Map.of("type", "json_object")
        );
    }

    /**
     * Extracts the content field from the OpenAI response.
     * 
     * <p>Response structure:
     * <pre>
     * {
     *   "choices": [{
     *     "message": {
     *       "content": "..."
     *     }
     *   }]
     * }
     * </pre>
     * 
     * @param response the raw JSON response from OpenAI
     * @return the extracted content string
     */
    private String extractContent(String response) {
        if (response == null || response.isBlank()) {
            throw new AiServiceException("Empty response from OpenAI");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
                throw new AiServiceException("Invalid OpenAI response format: missing or empty 'choices'");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new AiServiceException("Invalid OpenAI response format: missing 'content' field");
            }
            return content.asText();
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
}
