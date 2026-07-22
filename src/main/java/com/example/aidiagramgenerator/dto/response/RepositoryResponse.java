package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositorySourceType;
import com.example.aidiagramgenerator.domain.RepositoryStatus;

import java.time.Instant;
import java.util.UUID;

public record RepositoryResponse(
        UUID id,
        String name,
        RepositorySourceType sourceType,
        String sourceUrl,
        String originalFilename,
        RepositoryStatus status,
        Instant lastScannedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static RepositoryResponse from(Repository repository) {
        return new RepositoryResponse(
                repository.getId(),
                repository.getName(),
                repository.getSourceType(),
                repository.getSourceUrl(),
                repository.getOriginalFilename(),
                repository.getStatus(),
                repository.getLastScannedAt(),
                repository.getCreatedAt(),
                repository.getUpdatedAt());
    }
}
