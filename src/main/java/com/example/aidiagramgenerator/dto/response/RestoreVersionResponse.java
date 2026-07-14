package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.domain.DiagramVersion;

public record RestoreVersionResponse(
        int versionNumber,
        WorkspaceDiagramVersionResponse restoredVersion
) {
    public static RestoreVersionResponse from(DiagramVersion version) {
        return new RestoreVersionResponse(version.getVersionNumber(), WorkspaceDiagramVersionResponse.from(version));
    }
}
