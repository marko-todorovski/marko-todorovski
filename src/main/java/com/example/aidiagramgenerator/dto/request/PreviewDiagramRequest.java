package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreviewDiagramRequest(
        @NotBlank
        @Size(max = 100_000)
        String sourceCode,
        String outputFormat
) {
}
