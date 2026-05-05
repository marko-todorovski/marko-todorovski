package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.LayoutProfile;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.domain.StyleProfile;

/**
 * Service interface for generating PlantUML diagram code from a semantic model.
 * Applies style rules and layout variation to produce style-aware diagram syntax.
 */
public interface PlantUmlGenerationService {

    /**
     * Generates PlantUML code from the given semantic model and style profile.
     * Uses a random layout profile for visual variation.
     *
     * @param model the semantic model containing entities, relationships, and actions
     * @param style the style profile defining layout and visual rules
     * @return the generated PlantUML code
     * @throws IllegalArgumentException if model or style is null
     */
    String generate(SemanticModel model, StyleProfile style);

    /**
     * Generates PlantUML code with a specific seed for deterministic layout.
     * The same seed always produces the same layout variation.
     *
     * @param model the semantic model containing entities, relationships, and actions
     * @param style the style profile defining layout and visual rules
     * @param seed  the seed for deterministic random layout generation (null for random)
     * @return the generated PlantUML code
     * @throws IllegalArgumentException if model or style is null
     */
    String generate(SemanticModel model, StyleProfile style, Long seed);

    /**
     * Generates PlantUML code with an explicit layout profile.
     * The layout profile controls visual properties like direction, spacing, and grouping style.
     *
     * @param model  the semantic model containing entities, relationships, and actions
     * @param style  the style profile defining layout and visual rules
     * @param layout the layout profile controlling direction, spacing, and visual arrangement
     * @return the generated PlantUML code
     * @throws IllegalArgumentException if model, style, or layout is null
     */
    String generate(SemanticModel model, StyleProfile style, LayoutProfile layout);
}
