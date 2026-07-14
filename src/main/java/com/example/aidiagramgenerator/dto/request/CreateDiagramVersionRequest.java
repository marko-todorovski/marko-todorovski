package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDiagramVersionRequest(
        String prompt,
        @NotBlank String sourceCode,
        @NotNull DiagramSourceFormat sourceFormat,
        DiagramChangeType changeType,
        @Size(max = 100) String modelUsed
) {
}
