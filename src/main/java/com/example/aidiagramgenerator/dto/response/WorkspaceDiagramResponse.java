package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record WorkspaceDiagramResponse(
        UUID id,
        UUID projectId,
        String name,
        String description,
        DiagramType diagramType,
        DiagramSourceFormat sourceFormat,
        String originalPrompt,
        String currentSourceCode,
        Integer currentVersionNumber,
        String modelUsed,
        LocalDateTime createdAt,
        Instant updatedAt
) {
    public static WorkspaceDiagramResponse from(Diagram diagram) {
        return new WorkspaceDiagramResponse(
                diagram.getId(),
                diagram.getProject() == null ? null : diagram.getProject().getId(),
                diagram.getName(),
                diagram.getDescription(),
                diagram.getDiagramType(),
                diagram.getSourceFormat(),
                diagram.getOriginalPrompt(),
                diagram.getCurrentSourceCode(),
                diagram.getCurrentVersionNumber(),
                diagram.getModelUsed(),
                diagram.getCreatedAt(),
                diagram.getUpdatedAt());
    }
}
