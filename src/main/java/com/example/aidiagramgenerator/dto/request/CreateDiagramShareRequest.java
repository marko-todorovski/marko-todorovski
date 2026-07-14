package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateDiagramShareRequest(
        @Positive Integer versionNumber,
        @Future Instant expiresAt,
        Boolean allowDownloads,
        @Size(max = 150) String titleOverride,
        @Size(max = 1000) String descriptionOverride
) {
}
