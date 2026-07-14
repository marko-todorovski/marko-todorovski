package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramShare;

import java.time.Instant;
import java.util.UUID;

public record RevokedDiagramShareResponse(
        UUID shareId,
        String status,
        Instant revokedAt
) {
    public static RevokedDiagramShareResponse from(DiagramShare share) {
        return new RevokedDiagramShareResponse(share.getId(), share.getStatus().name(), share.getRevokedAt());
    }
}
