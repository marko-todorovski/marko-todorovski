package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramSuggestion;

/**
 * Service interface for suggesting diagram types with confidence scoring.
 * Unlike {@link DiagramClassificationService} which returns a definitive type,
 * this service returns a suggestion with a confidence score so the caller
 * can decide whether to proceed or ask for user confirmation.
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public interface DiagramSuggestionService {

    /**
     * Analyzes the input text and suggests the most appropriate diagram type
     * along with a confidence score.
     *
     * @param inputText the natural language input to analyze
     * @return a {@link DiagramSuggestion} containing the type, confidence, and reasoning
     * @throws IllegalArgumentException if inputText is null or blank
     */
    DiagramSuggestion suggest(String inputText);
}
