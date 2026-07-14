package com.example.aidiagramgenerator.dto.response;

import java.util.List;

public record DiagramSuggestionsResponse(
        String summary,
        List<DiagramSuggestionItemResponse> suggestions
) {
}
