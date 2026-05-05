package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.EvaluationResult;

/**
 * Runs a batch evaluation of the classification pipeline against the
 * evaluation dataset and returns accuracy metrics.
 */
public interface EvaluationService {

    /**
     * Loads the evaluation dataset, runs each item through
     * {@link DiagramClassificationService}, and computes accuracy metrics.
     *
     * @return an {@link EvaluationResult} with total, correct, accuracy %, and per-item details
     */
    EvaluationResult evaluate();
}
