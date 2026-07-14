package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.assistant.mock-enabled", havingValue = "false", matchIfMissing = true)
public class AiModelDiagramAiAssistantClient implements DiagramAiAssistantClient {

    private final AiModelService aiModelService;

    public AiModelDiagramAiAssistantClient(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @Override
    public String getModelName() {
        return aiModelService.getModelName();
    }

    @Override
    public String generateStructuredResponse(String prompt) {
        return aiModelService.generateStructuredResponse(prompt);
    }
}
