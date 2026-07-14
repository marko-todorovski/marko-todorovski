package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramVersion;

import java.time.Instant;

public record PublicDiagramShareResponse(
        String title,
        String description,
        String diagramType,
        String sourceFormat,
        int versionNumber,
        Instant createdAt,
        Instant sharedAt,
        Instant expiresAt,
        boolean allowDownloads
) {
    public static PublicDiagramShareResponse from(DiagramShare share) {
        Diagram diagram = share.getDiagram();
        DiagramVersion version = share.getDiagramVersion();
        String title = firstPresent(share.getTitleOverride(), diagram.getName(), "Shared Diagram");
        String description = firstPresent(share.getDescriptionOverride(), diagram.getDescription(), null);
        return new PublicDiagramShareResponse(
                title,
                description,
                diagram.getDiagramType().name(),
                version.getSourceFormat().name(),
                version.getVersionNumber(),
                version.getCreatedAt(),
                share.getCreatedAt(),
                share.getExpiresAt(),
                share.isAllowDownloads());
    }

    private static String firstPresent(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
