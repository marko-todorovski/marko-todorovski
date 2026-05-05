package com.example.aidiagramgenerator.config;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.OllamaService;
import com.example.aidiagramgenerator.ai.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Configuration for AI provider selection.
 * 
 * <p>This configuration enables switching between AI providers for research experiments
 * without modifying service logic. Only one provider is active at runtime.
 * 
 * <p>Configuration:
 * <pre>
 * ai.provider=openai   # Use OpenAI GPT-4o (cloud)
 * ai.provider=ollama   # Use Ollama Llama 3 (local)
 * </pre>
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Value("${ai.provider:openai}")
    private String aiProvider;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openAiApiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${openai.diagram.model:gpt-4o}")
    private String openAiDiagramModel;

    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String ollamaApiUrl;

    @Value("${ollama.model:llama3}")
    private String ollamaModel;

    /**
     * Creates the AI model service based on the configured provider.
     * 
     * <p>Only one provider is instantiated at runtime, determined by
     * the {@code ai.provider} property.
     * 
     * @param restClientBuilder builder for REST client (OpenAI)
     * @param webClientBuilder builder for WebClient (Ollama)
     * @return the configured AI model service
     * @throws IllegalArgumentException if an unknown provider is specified
     */
    @Bean
    @Primary
    public AiModelService aiModelService(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        
        log.info("Configuring AI provider: {}", aiProvider);
        
        return switch (aiProvider.toLowerCase()) {
            case "openai" -> {
                log.info("Initializing OpenAI service — classification model: {}, diagram model: {}", openAiModel, openAiDiagramModel);
                var requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(Duration.ofSeconds(3));
                requestFactory.setReadTimeout(Duration.ofSeconds(5));
                restClientBuilder.requestFactory(requestFactory);
                yield new OpenAiService(restClientBuilder, objectMapper, openAiApiKey, openAiApiUrl, openAiModel, openAiDiagramModel);
            }
            case "ollama" -> {
                log.info("Initializing Ollama service with model: {} at {}", ollamaModel, ollamaApiUrl);
                yield new OllamaService(webClientBuilder, ollamaApiUrl, ollamaModel);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown AI provider: " + aiProvider + ". Supported values: openai, ollama");
        };
    }
}
