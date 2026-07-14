package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.domain.DiagramType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SuggestDiagramImprovementsRequest(
        @Size(max = 20_000) String sourceCode,
        DiagramType diagramType,
        @Pattern(regexp = "readability|architecture|completeness|naming|relationships|security|general",
                message = "Unsupported suggestion focus")
        String focus
) {
}
