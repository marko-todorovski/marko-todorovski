package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.domain.ApplicationUser;

import java.time.Instant;

public record DiagramVersionSummaryResponse(
        int versionNumber,
        DiagramSourceFormat sourceFormat,
        DiagramChangeType changeType,
        String modelUsed,
        String createdByDisplayName,
        Instant createdAt
) {
    public static DiagramVersionSummaryResponse from(DiagramVersion version) {
        return new DiagramVersionSummaryResponse(
                version.getVersionNumber(),
                version.getSourceFormat(),
                version.getChangeType(),
                version.getModelUsed(),
                displayName(version.getCreatedBy()),
                version.getCreatedAt());
    }

    private static String displayName(ApplicationUser user) {
        if (user == null) {
            return null;
        }
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isBlank() ? user.getEmail() : name;
    }
}
