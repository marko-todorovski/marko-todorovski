package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.dto.response.DiagramExplanationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramModificationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramSuggestionsResponse;

import java.util.UUID;

public interface DiagramAiAssistantService {

    DiagramExplanationResponse explainDiagram(UUID diagramId, UUID ownerId, String sourceCode);

    DiagramSuggestionsResponse suggestImprovements(UUID diagramId, UUID ownerId, String sourceCode, DiagramType diagramType, String focus);

    DiagramModificationResponse modifyDiagram(UUID diagramId, UUID ownerId, String instruction, String sourceCode, DiagramType diagramType);
}
