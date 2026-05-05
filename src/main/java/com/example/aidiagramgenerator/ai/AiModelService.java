package com.example.aidiagramgenerator.ai;

/**
 * Abstraction layer for AI model providers.
 * 
 * <p>This interface enables interchangeable AI providers for experimental evaluation
 * in the research project "A Hybrid AI System for Automatic Classification and 
 * Style-Aware Generation of Software Engineering Diagrams from Natural Language".
 * 
 * <p>By abstracting the AI provider, researchers can:
 * <ul>
 *   <li>Compare performance between cloud-based (GPT-4o) and local (Llama 3) models</li>
 *   <li>Evaluate response quality, latency, and accuracy across providers</li>
 *   <li>Switch providers without modifying business logic</li>
 *   <li>Run offline experiments using local models when needed</li>
 * </ul>
 * 
 * <p>Implementations:
 * <ul>
 *   <li>{@code OpenAiService} - Cloud-based GPT-4o integration</li>
 *   <li>{@code OllamaService} - Local Llama 3 integration via Ollama</li>
 * </ul>
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public interface AiModelService {

    /**
     * Returns the display name of the AI model used by this provider.
     * Used for tracking which model generated a given diagram.
     * 
     * @return the model name (e.g., "GPT-4o", "Llama3")
     */
    String getModelName();

    /**
     * Calls the LLM with a plain-text prompt and returns a structured {@link LlmResult}.
     *
     * <p>Implementations must never throw — use {@link LlmResult#failure()} on error.
     * The default implementation wraps {@link #generateStructuredResponse}.
     *
     * @param prompt the text prompt
     * @return {@link LlmResult#success(String)} with content, or {@link LlmResult#failure()}
     */
    default LlmResult callLLM(String prompt) {
        try {
            String response = generateStructuredResponse(prompt);
            if (response == null || response.isBlank()) {
                return LlmResult.failure();
            }
            return LlmResult.success(response);
        } catch (Exception e) {
            return LlmResult.failure();
        }
    }

    /**
     * Generates a structured response from the AI model based on the given prompt.
     * 
     * <p>The response is expected to be in a structured format (e.g., JSON) that can
     * be parsed by downstream services for diagram classification and generation.
     * 
     * @param prompt the natural language prompt to send to the AI model
     * @return the structured response from the AI model
     * @throws AiServiceException if the AI provider fails to generate a response
     */
    String generateStructuredResponse(String prompt);
}
