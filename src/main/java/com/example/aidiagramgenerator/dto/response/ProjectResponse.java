package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String description,
        long diagramCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project, long diagramCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                diagramCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
