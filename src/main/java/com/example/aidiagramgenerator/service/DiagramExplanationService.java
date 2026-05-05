package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation;

public interface DiagramExplanationService {

    DiagramExplanation explain(DiagramType diagramType, int confidence,
                               SemanticModel semanticModel,
                               DiagramSuggestion suggestion);
}
