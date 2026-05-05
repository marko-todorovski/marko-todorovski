package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.GenerationResult;

/**
 * Service interface for confidence-based diagram generation.
 *
 * <p>Orchestrates the full pipeline: classification → confidence evaluation →
 * conditional generation, returning a unified {@link GenerationResult} whose
 * content depends on the confidence level:
 *
 * <ul>
 *   <li><strong>≥ 70%</strong> — diagram auto-generated (PlantUML + PNG/SVG)</li>
 *   <li><strong>40–69%</strong> — suggestion returned, user confirmation needed</li>
 *   <li><strong>&lt; 40%</strong> — request rejected, more detail required</li>
 * </ul>
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
public interface ConfidenceDiagramService {

    /**
     * Processes a diagram generation request using confidence-based branching.
     *
     * @param text           the natural language description
     * @param diagramType    optional explicit diagram type from the frontend (may be null)
     * @param seed           optional seed for deterministic layout (may be null)
     * @param forceGenerate  when {@code true}, skip classification and generate directly
     *                       using the provided {@code diagramType} (used after user confirms a SUGGEST)
     * @return a {@link GenerationResult} containing either the generated diagram
     *         or an appropriate suggestion / error message
     * @throws IllegalArgumentException if text is null or blank
     */
    GenerationResult process(String text, String diagramType, Long seed, boolean forceGenerate);
}
