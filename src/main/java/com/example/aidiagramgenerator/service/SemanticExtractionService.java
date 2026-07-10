package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.SemanticModel;

/**
 * Service interface for extracting semantic information from natural language input.
 * This service parses text to identify entities, relationships, and actions.
 */
public interface SemanticExtractionService {

    /**
     * Extracts a semantic model from the given text input.
     * The model contains identified entities, relationships, and actions.
     *
     * @param text the natural language input text to extract from
     * @return the extracted SemanticModel
     * @throws IllegalArgumentException if text is null or blank
     */
    SemanticModel extract(String text);

    /**
     * Extracts a semantic model tailored for a specific diagram type.
     * Uses a type-aware AI prompt so the returned model fields are appropriate
     * for the target diagram (e.g. states/transitions for STATE, actors/messages for SEQUENCE).
     *
     * @param text the natural language input text to extract from
     * @param diagramType the target diagram type (null falls back to CLASS extraction)
     * @return the extracted SemanticModel
     */
    default SemanticModel extract(String text, DiagramType diagramType) {
        return extract(text);
    }
}
