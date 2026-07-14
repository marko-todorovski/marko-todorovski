package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(
        UUID id,
        String name,
        String description,
        long diagramCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectSummaryResponse from(Project project, long diagramCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                diagramCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
