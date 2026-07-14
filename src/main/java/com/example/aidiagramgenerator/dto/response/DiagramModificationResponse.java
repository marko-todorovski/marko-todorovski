package com.example.aidiagramgenerator.dto.response;

import java.util.List;

public record DiagramModificationResponse(
        String sourceCode,
        String summary,
        String modelUsed,
        List<String> warnings
) {
}
