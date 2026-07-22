package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(
        UUID id,
        String name,
        String description,
        long diagramCount,
        ProjectRole currentUserRole,
        long memberCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectSummaryResponse from(Project project, long diagramCount) {
        return from(project, diagramCount, ProjectRole.OWNER, 1);
    }

    public static ProjectSummaryResponse from(Project project, long diagramCount, ProjectRole currentUserRole, long memberCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                diagramCount,
                currentUserRole,
                memberCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
