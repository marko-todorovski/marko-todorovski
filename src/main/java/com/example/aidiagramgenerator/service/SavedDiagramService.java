package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;

import java.util.List;
import java.util.UUID;

public interface SavedDiagramService {

    Diagram saveGeneratedDiagram(
            UUID ownerId,
            UUID projectId,
            String name,
            String description,
            String originalPrompt,
            DiagramType diagramType,
            DiagramSourceFormat sourceFormat,
            String sourceCode,
            String modelUsed);

    Diagram attachExistingGeneratedDiagram(
            UUID ownerId,
            UUID projectId,
            UUID diagramId,
            String name,
            String description);

    Diagram getDiagramForOwner(UUID diagramId, UUID ownerId);

    List<Diagram> getProjectDiagrams(UUID projectId, UUID ownerId);

    Diagram updateDiagramMetadata(UUID diagramId, UUID ownerId, String name, String description);

    void deleteDiagram(UUID diagramId, UUID ownerId);
}
