package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation.RelationshipInfo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiagramExplanationServiceImpl implements DiagramExplanationService {

    private static final int HIGH_CONFIDENCE_THRESHOLD = 70;
    private static final int MEDIUM_CONFIDENCE_THRESHOLD = 40;

    @Override
    public DiagramExplanation explain(DiagramType diagramType, int confidence,
                                       SemanticModel semanticModel,
                                       DiagramSuggestion suggestion) {
        List<String> entities = semanticModel != null
                ? semanticModel.getEntities().stream().map(EntityNode::getName).toList()
                : List.of();

        List<RelationshipInfo> relationships = semanticModel != null
                ? semanticModel.getRelationships().stream()
                    .map(r -> new RelationshipInfo(r.getSource(), r.getTarget(), r.getType()))
                    .toList()
                : List.of();

        List<String> actions = semanticModel != null
                ? semanticModel.getActions()
                : List.of();

        String reasoning = buildTypeReasoning(diagramType, confidence, suggestion);
        String confidenceLevel = toConfidenceLevel(confidence);
        String source = suggestion != null ? suggestion.getSource().name() : "EXPLICIT";

        return DiagramExplanation.builder()
                .typeReasoning(reasoning)
                .confidenceScore(confidence)
                .confidenceLevel(confidenceLevel)
                .extractedEntities(entities)
                .detectedRelationships(relationships)
                .detectedActions(actions)
                .classificationSource(source)
                .build();
    }

    private String buildTypeReasoning(DiagramType diagramType, int confidence,
                                       DiagramSuggestion suggestion) {
        if (suggestion != null && suggestion.getReasoningMessage() != null
                && !suggestion.getReasoningMessage().isBlank()) {
            return suggestion.getReasoningMessage();
        }

        String displayName = diagramType.getDisplayName();
        if (confidence == 100) {
            return displayName + " was explicitly requested by the user.";
        }

        return switch (diagramType) {
            case CLASS -> "Input describes entities with attributes and structural relationships, suggesting a " + displayName + ".";
            case SEQUENCE -> "Input describes interactions between actors over time, suggesting a " + displayName + ".";
            case ER -> "Input describes data entities and their associations, suggesting a " + displayName + ".";
            case USE_CASE -> "Input describes user goals and system capabilities, suggesting a " + displayName + ".";
            case COMPONENT -> "Input describes system modules and their dependencies, suggesting a " + displayName + ".";
            case DEPLOYMENT -> "Input describes infrastructure and deployment topology, suggesting a " + displayName + ".";
            case OBJECT -> "Input describes concrete object instances and their states, suggesting a " + displayName + ".";
            case ACTIVITY -> "Input describes a workflow or process with sequential steps, suggesting a " + displayName + ".";
            case STATE -> "Input describes state transitions and lifecycle events, suggesting a " + displayName + ".";
            case COLLABORATION -> "Input describes how objects collaborate to fulfil a responsibility, suggesting a " + displayName + ".";
            case MICROSERVICES -> "Input describes microservice interactions and boundaries, suggesting a " + displayName + ".";
        };
    }

    private String toConfidenceLevel(int confidence) {
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) return "HIGH";
        if (confidence >= MEDIUM_CONFIDENCE_THRESHOLD) return "MEDIUM";
        return "LOW";
    }
}
