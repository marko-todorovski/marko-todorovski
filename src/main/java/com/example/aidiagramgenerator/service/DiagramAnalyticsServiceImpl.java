package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.DiagramMetricsResponse;
import com.example.aidiagramgenerator.dto.response.GlobalDiagramAnalyticsResponse;
import com.example.aidiagramgenerator.repository.DiagramEvaluationRepository;
import com.example.aidiagramgenerator.repository.DiagramEvaluationRepository.DiagramMetricsProjection;
import com.example.aidiagramgenerator.repository.DiagramEvaluationRepository.GlobalMetricsProjection;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link DiagramAnalyticsService} for diagram evaluation analytics.
 */
@Service
public class DiagramAnalyticsServiceImpl implements DiagramAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramAnalyticsServiceImpl.class);

    private final DiagramRepository diagramRepository;
    private final DiagramEvaluationRepository diagramEvaluationRepository;

    public DiagramAnalyticsServiceImpl(DiagramRepository diagramRepository,
                                       DiagramEvaluationRepository diagramEvaluationRepository) {
        this.diagramRepository = diagramRepository;
        this.diagramEvaluationRepository = diagramEvaluationRepository;
    }

    @Override
    public Optional<DiagramMetricsResponse> getDiagramMetrics(UUID diagramId) {
        logger.info("Fetching metrics for diagram id={}", diagramId);

        // Check if diagram exists
        if (!diagramRepository.existsById(diagramId)) {
            logger.warn("Diagram not found: {}", diagramId);
            return Optional.empty();
        }

        // Get aggregated metrics
        Optional<DiagramMetricsProjection> metricsOpt = 
                diagramEvaluationRepository.findMetricsByDiagramId(diagramId);

        if (metricsOpt.isEmpty() || metricsOpt.get().getTotalEvaluations() == 0) {
            logger.info("No evaluations found for diagram id={}", diagramId);
            return Optional.of(new DiagramMetricsResponse(diagramId, 0.0, 0.0, 0.0, 0));
        }

        DiagramMetricsProjection metrics = metricsOpt.get();
        DiagramMetricsResponse response = new DiagramMetricsResponse(
                diagramId,
                roundToTwoDecimals(metrics.getAverageClarity()),
                roundToTwoDecimals(metrics.getAverageCorrectness()),
                roundToTwoDecimals(metrics.getAverageUsefulness()),
                metrics.getTotalEvaluations()
        );

        return Optional.of(response);
    }

    @Override
    public GlobalDiagramAnalyticsResponse getGlobalAnalytics() {
        logger.info("Fetching global diagram analytics");

        GlobalMetricsProjection metrics = diagramEvaluationRepository.findGlobalMetrics();

        if (metrics.getTotalEvaluations() == null || metrics.getTotalEvaluations() == 0) {
            logger.info("No evaluations found globally");
            return new GlobalDiagramAnalyticsResponse(0.0, 0.0, 0.0, 0, 0);
        }

        return new GlobalDiagramAnalyticsResponse(
                roundToTwoDecimals(metrics.getAverageClarity()),
                roundToTwoDecimals(metrics.getAverageCorrectness()),
                roundToTwoDecimals(metrics.getAverageUsefulness()),
                metrics.getTotalEvaluations(),
                metrics.getTotalEvaluatedDiagrams()
        );
    }

    private double roundToTwoDecimals(Double value) {
        if (value == null) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
