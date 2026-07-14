package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramShare;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record CreatedDiagramShareResponse(
        UUID shareId,
        String token,
        String publicUrl,
        int versionNumber,
        Instant expiresAt,
        boolean allowDownloads,
        Instant createdAt,
        String status
) {
    public static CreatedDiagramShareResponse from(DiagramShare share, String token, String publicUrl, Clock clock) {
        return new CreatedDiagramShareResponse(
                share.getId(),
                token,
                publicUrl,
                share.getDiagramVersion().getVersionNumber(),
                share.getExpiresAt(),
                share.isAllowDownloads(),
                share.getCreatedAt(),
                share.isExpired(clock) ? "EXPIRED" : share.getStatus().name());
    }
}
