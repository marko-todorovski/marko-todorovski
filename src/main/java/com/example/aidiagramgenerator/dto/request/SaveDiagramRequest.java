package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveDiagramRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        String originalPrompt,
        @NotNull DiagramType diagramType,
        @NotNull DiagramSourceFormat sourceFormat,
        @NotBlank String sourceCode,
        @Size(max = 100) String modelUsed
) {
}
