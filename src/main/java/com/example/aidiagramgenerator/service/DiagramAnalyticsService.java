package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.DiagramMetricsResponse;
import com.example.aidiagramgenerator.dto.response.GlobalDiagramAnalyticsResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for diagram analytics operations.
 */
public interface DiagramAnalyticsService {

    /**
     * Get aggregated metrics for a specific diagram.
     *
     * @param diagramId the diagram ID
     * @return Optional containing metrics if the diagram exists and has evaluations
     */
    Optional<DiagramMetricsResponse> getDiagramMetrics(UUID diagramId);

    /**
     * Get global analytics across all diagram evaluations.
     *
     * @return GlobalDiagramAnalyticsResponse containing global averages
     */
    GlobalDiagramAnalyticsResponse getGlobalAnalytics();
}
