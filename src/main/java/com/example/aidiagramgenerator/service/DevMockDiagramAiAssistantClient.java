package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.ai.assistant.mock-enabled", havingValue = "true")
public class DevMockDiagramAiAssistantClient implements DiagramAiAssistantClient {

    @Override
    public String getModelName() {
        return "dev-mock-ai-assistant";
    }

    @Override
    public String generateStructuredResponse(String prompt) {
        String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
        if (lowerPrompt.contains("timeout-mode")) {
            throw new AiServiceException("mock timeout");
        }
        if (lowerPrompt.contains("invalid-output")) {
            return "{\"sourceCode\":\"class MissingWrapper\",\"summary\":\"Invalid mock proposal\",\"warnings\":[\"Mock invalid output\"]}";
        }
        if (lowerPrompt.contains("suggest focused improvements")) {
            return """
                    {
                      "summary": "Mock suggestions for the current editor source.",
                      "suggestions": [
                        {"title": "Add service boundary", "description": "Introduce a service class to clarify responsibilities.", "priority": "HIGH"},
                        {"title": "Label relationships", "description": "Add relationship labels so readers understand direction and intent.", "priority": "MEDIUM"},
                        {"title": "Group external systems", "description": "Use a package or boundary for external collaborators.", "priority": "LOW"}
                      ]
                    }
                    """;
        }
        if (lowerPrompt.contains("modify this")) {
            return """
                    {
                      "sourceCode": "@startuml\\nclass Account\\nclass AiAddedService\\nAccount --> AiAddedService : uses\\n@enduml",
                      "summary": "Added AiAddedService and connected it to Account.",
                      "warnings": []
                    }
                    """;
        }
        String elementName = lowerPrompt.contains("unsaved") ? "Unsaved" : "Account";
        return """
                {
                  "summary": "Mock explanation based on the current editor source.",
                  "elements": [
                    {"name": "%s", "type": "class", "description": "Detected in the PlantUML source."}
                  ],
                  "relationships": [],
                  "flow": "The diagram shows the static structure currently in the editor.",
                  "risks": []
                }
                """.formatted(elementName);
    }
}
