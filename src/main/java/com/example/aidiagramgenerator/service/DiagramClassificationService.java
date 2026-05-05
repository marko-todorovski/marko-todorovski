package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ClassificationResponse;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.SemanticModel;

/**
 * Service interface for classifying diagram types from user input.
 *
 * <p>Two overloads are provided:
 * <ul>
 *   <li>{@link #classify(String)} — legacy text-based classification, returns a bare
 *       {@link DiagramType} (retained for backward compatibility).</li>
 *   <li>{@link #classify(SemanticModel)} — confidence-based classification over a
 *       structured {@link SemanticModel}; returns a full {@link ClassificationResponse}
 *       with a decision tier (AUTO / SUGGEST / CLARIFY) and a user-facing message.</li>
 * </ul>
 */
public interface DiagramClassificationService {

    /**
     * Classifies the given text input and determines the most appropriate diagram type.
     *
     * @param text the natural language input text to classify
     * @return the classified DiagramType
     * @throws IllegalArgumentException if text is null or blank
     */
    DiagramType classify(String text);

    /**
     * Classifies a structured {@link SemanticModel} and returns a confidence-graded
     * {@link ClassificationResponse}.
     *
     * <p>Confidence tiers:
     * <ul>
     *   <li>Explicit keyword match → 90–100 %</li>
     *   <li>Semantic pattern match → 60–80 %</li>
     *   <li>Weak / keyword-only match → 30–50 %</li>
     * </ul>
     *
     * <p>Decision mapping:
     * <ul>
     *   <li>confidence ≥ 70 → {@code AUTO}   — diagram is generated automatically</li>
     *   <li>confidence 40–69 → {@code SUGGEST} — a suggestion is returned for user confirmation</li>
     *   <li>confidence &lt; 40 → {@code CLARIFY} — more input is requested from the user</li>
     * </ul>
     *
     * @param model the semantic model extracted from user input (must not be null)
     * @return a {@link ClassificationResponse} with decision, message, type, and confidence
     * @throws IllegalArgumentException if model is null
     */
    ClassificationResponse classify(SemanticModel model);
}
