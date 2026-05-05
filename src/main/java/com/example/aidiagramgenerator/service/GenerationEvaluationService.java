package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.GenerationEvaluationResult;

/**
 * Runs a batch end-to-end evaluation of the full diagram generation pipeline
 * against a labelled dataset and reports a success rate.
 */
public interface GenerationEvaluationService {

    /**
     * Loads the generation evaluation dataset, invokes {@link DiagramService#generateFromText}
     * for each item, and checks whether the result has the correct type and non-empty content.
     *
     * @return a {@link GenerationEvaluationResult} with total, successful, success rate %, and per-item details
     */
    GenerationEvaluationResult evaluate();
}
