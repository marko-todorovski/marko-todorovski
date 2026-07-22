package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.RepositoryLanguage;
import com.example.aidiagramgenerator.domain.RepositoryScan;
import com.example.aidiagramgenerator.domain.ScanStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record RepositoryScanResponse(
        UUID id,
        UUID repositoryId,
        ScanStatus status,
        String projectName,
        RepositoryLanguage primaryLanguage,
        String framework,
        Integer fileCount,
        Integer folderCount,
        List<String> topLevelFolders,
        String branch,
        String commitHash,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public static RepositoryScanResponse from(RepositoryScan scan, UUID repositoryId) {
        String raw = scan.getTopLevelFolders();
        List<String> folders = (raw == null || raw.isBlank()) ? List.of() : Arrays.asList(raw.split(","));
        return new RepositoryScanResponse(
                scan.getId(),
                repositoryId,
                scan.getStatus(),
                scan.getProjectName(),
                scan.getPrimaryLanguage(),
                scan.getFramework(),
                scan.getFileCount(),
                scan.getFolderCount(),
                folders,
                scan.getBranch(),
                scan.getCommitHash(),
                scan.getErrorMessage(),
                scan.getStartedAt(),
                scan.getCompletedAt());
    }
}
