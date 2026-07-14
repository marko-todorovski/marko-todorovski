package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramShare;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record DiagramShareSummaryResponse(
        UUID shareId,
        int versionNumber,
        String status,
        boolean expired,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt,
        Instant lastAccessedAt,
        long accessCount,
        boolean allowDownloads,
        String titleOverride,
        String descriptionOverride
) {
    public static DiagramShareSummaryResponse from(DiagramShare share, Clock clock) {
        boolean expired = share.isExpired(clock);
        return new DiagramShareSummaryResponse(
                share.getId(),
                share.getDiagramVersion().getVersionNumber(),
                expired ? "EXPIRED" : share.getStatus().name(),
                expired,
                share.getExpiresAt(),
                share.getCreatedAt(),
                share.getRevokedAt(),
                share.getLastAccessedAt(),
                share.getAccessCount(),
                share.isAllowDownloads(),
                share.getTitleOverride(),
                share.getDescriptionOverride());
    }
}
