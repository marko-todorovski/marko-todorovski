package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramVersion;

import java.time.Instant;

public record WorkspaceDiagramVersionResponse(
        int versionNumber,
        String prompt,
        String sourceCode,
        DiagramSourceFormat sourceFormat,
        DiagramChangeType changeType,
        String modelUsed,
        Instant createdAt
) {
    public static WorkspaceDiagramVersionResponse from(DiagramVersion version) {
        return new WorkspaceDiagramVersionResponse(
                version.getVersionNumber(),
                version.getPrompt(),
                version.getSourceCode(),
                version.getSourceFormat(),
                version.getChangeType(),
                version.getModelUsed(),
                version.getCreatedAt());
    }
}
