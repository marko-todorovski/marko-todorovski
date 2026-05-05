package com.example.aidiagramgenerator.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Ollama (Llama 3) implementation of {@link AiModelService}.
 * 
 * <p>This service integrates with a local Ollama instance running Llama 3
 * for research experiments comparing local AI performance against cloud-based models.
 * 
 * <p>Configuration:
 * <pre>
 * ollama.api.url=http://localhost:11434/api/generate
 * ollama.model=llama3
 * </pre>
 * 
 * <p>Prerequisites:
 * <ul>
 *   <li>Ollama installed and running locally</li>
 *   <li>Llama 3 model pulled: {@code ollama pull llama3}</li>
 * </ul>
 * 
 * <p>Bean creation is managed by {@link com.example.aidiagramgenerator.config.AiProviderConfig}.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public class OllamaService implements AiModelService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final WebClient webClient;
    private final String model;

    /**
     * Constructs the Ollama service with required dependencies.
     * 
     * @param webClientBuilder WebClient builder for HTTP requests
     * @param apiUrl Ollama API endpoint URL
     * @param model Ollama model identifier (e.g., llama3)
     */
    public OllamaService(
            WebClient.Builder webClientBuilder,
            String apiUrl,
            String model) {
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .build();
        this.model = model;
        
        log.info("OllamaService initialized with model: {} at {}", model, apiUrl);
    }

    @Override
    public String getModelName() {
        return "Llama3";
    }

    /**
     * {@inheritDoc}
     * 
     * <p>Sends a prompt to local Ollama (Llama 3) and returns the raw response content.
     * Response time is logged for research metrics collection.
     */
    @Override
    public String generateStructuredResponse(String prompt) {
        log.debug("Sending prompt to Ollama (length: {} chars)", prompt.length());
        
        Instant start = Instant.now();
        
        try {
            Map<String, Object> requestBody = buildRequestBody(prompt);
            
            String response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("Ollama response received in {} ms", elapsed.toMillis());
            
            String content = extractResponse(response);
            log.debug("Extracted content length: {} chars", content.length());
            
            return content;
            
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("Ollama request failed after {} ms: {}", elapsed.toMillis(), e.getMessage());
            throw new AiServiceException("Failed to get response from Ollama: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the Ollama API request body.
     * 
     * @param prompt the user prompt
     * @return the request body as a map
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        String systemPrompt = "You are a helpful assistant that responds only with valid JSON. " +
                "Do not include markdown code blocks or any other formatting.";
        
        String fullPrompt = systemPrompt + "\n\nUser request:\n" + prompt;
        
        return Map.of(
                "model", model,
                "prompt", fullPrompt,
                "stream", false,
                "format", "json"
        );
    }

    /**
     * Extracts the response field from the Ollama response.
     * 
     * <p>Response structure:
     * <pre>
     * {
     *   "model": "llama3",
     *   "response": "...",
     *   "done": true
     * }
     * </pre>
     * 
     * @param response the raw JSON response from Ollama
     * @return the extracted response string
     */
    private String extractResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new AiServiceException("Empty response from Ollama");
        }
        
        // Find "response" field in JSON
        int responseStart = response.indexOf("\"response\"");
        if (responseStart == -1) {
            throw new AiServiceException("Invalid Ollama response format: missing response field");
        }
        
        // Find the value after "response":
        int colonIndex = response.indexOf(":", responseStart);
        int valueStart = response.indexOf("\"", colonIndex + 1) + 1;
        
        // Find the closing quote, handling escaped quotes
        int valueEnd = valueStart;
        while (valueEnd < response.length()) {
            int nextQuote = response.indexOf("\"", valueEnd);
            if (nextQuote == -1) {
                throw new AiServiceException("Invalid Ollama response format: unclosed response string");
            }
            // Check if the quote is escaped
            int backslashCount = 0;
            int checkIndex = nextQuote - 1;
            while (checkIndex >= valueStart && response.charAt(checkIndex) == '\\') {
                backslashCount++;
                checkIndex--;
            }
            if (backslashCount % 2 == 0) {
                valueEnd = nextQuote;
                break;
            }
            valueEnd = nextQuote + 1;
        }
        
        String content = response.substring(valueStart, valueEnd);
        
        // Unescape JSON string
        return content
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
