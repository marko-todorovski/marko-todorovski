package com.example.aidiagramgenerator.dto.response;

import java.util.List;

public record DiagramExplanationResponse(
        String summary,
        List<DiagramExplanationElementResponse> elements,
        List<DiagramExplanationRelationshipResponse> relationships,
        String flow,
        List<String> risks,
        String explanation
) {
    public static DiagramExplanationResponse plain(String explanation) {
        return new DiagramExplanationResponse(null, List.of(), List.of(), null, List.of(), explanation);
    }
}
