package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.DiagramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModifyDiagramRequest(
        @NotBlank @Size(max = 2_000) String instruction,
        @NotBlank @Size(max = 20_000) String sourceCode,
        @NotNull DiagramType diagramType
) {
}
