package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.RepositoryScan;
import com.example.aidiagramgenerator.domain.ScanStatus;

import java.time.Instant;
import java.util.UUID;

public record ScanProgressResponse(
        UUID repositoryId,
        UUID scanId,
        ScanStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public static ScanProgressResponse from(RepositoryScan scan, UUID repositoryId) {
        return new ScanProgressResponse(
                repositoryId,
                scan.getId(),
                scan.getStatus(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                scan.getErrorMessage());
    }
}
