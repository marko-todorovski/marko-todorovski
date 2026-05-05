package com.example.aidiagramgenerator.service;

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
}
