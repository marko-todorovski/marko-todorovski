package com.example.aidiagramgenerator.dto.response;

import java.time.Instant;

public record WorkspaceErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
    public static WorkspaceErrorResponse of(String code, String message) {
        return new WorkspaceErrorResponse(code, message, Instant.now());
    }
}
