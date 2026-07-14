package com.example.aidiagramgenerator.dto.request;

import jakarta.validation.constraints.Size;

public record ExplainDiagramRequest(
        @Size(max = 20_000) String sourceCode
) {
}
