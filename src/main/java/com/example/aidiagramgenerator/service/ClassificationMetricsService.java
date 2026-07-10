package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.ClassificationMetricsResult;

/**
 * Evaluates the classification pipeline against all three labelled datasets and
 * computes per-class precision, recall, F1-score, and macro-averaged metrics.
 */
public interface ClassificationMetricsService {

    /**
     * Runs classification on every entry in all three evaluation datasets, computes
     * accuracy / precision / recall / F1, prints a summary to the console, and
     * returns the full structured report.
     *
     * @return a {@link ClassificationMetricsResult} with per-class and macro metrics
     */
    ClassificationMetricsResult evaluate();
}
