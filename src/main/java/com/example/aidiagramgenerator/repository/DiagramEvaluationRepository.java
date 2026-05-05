package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.entity.DiagramEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramEvaluationRepository extends JpaRepository<DiagramEvaluation, UUID> {

    List<DiagramEvaluation> findByDiagramId(UUID diagramId);

    /**
     * Get aggregated metrics for a specific diagram.
     */
    @Query("SELECT AVG(e.clarityScore) as averageClarity, " +
           "AVG(e.correctnessScore) as averageCorrectness, " +
           "AVG(e.usefulnessScore) as averageUsefulness, " +
           "COUNT(e) as totalEvaluations " +
           "FROM DiagramEvaluation e WHERE e.diagramId = :diagramId")
    Optional<DiagramMetricsProjection> findMetricsByDiagramId(@Param("diagramId") UUID diagramId);

    /**
     * Get global aggregated metrics across all evaluations.
     */
    @Query("SELECT AVG(e.clarityScore) as averageClarity, " +
           "AVG(e.correctnessScore) as averageCorrectness, " +
           "AVG(e.usefulnessScore) as averageUsefulness, " +
           "COUNT(e) as totalEvaluations, " +
           "COUNT(DISTINCT e.diagramId) as totalEvaluatedDiagrams " +
           "FROM DiagramEvaluation e")
    GlobalMetricsProjection findGlobalMetrics();

    /**
     * Check if any evaluations exist for a diagram.
     */
    boolean existsByDiagramId(UUID diagramId);

    /**
     * Projection interface for diagram-specific metrics.
     */
    interface DiagramMetricsProjection {
        Double getAverageClarity();
        Double getAverageCorrectness();
        Double getAverageUsefulness();
        Long getTotalEvaluations();
    }

    /**
     * Projection interface for global metrics.
     */
    interface GlobalMetricsProjection {
        Double getAverageClarity();
        Double getAverageCorrectness();
        Double getAverageUsefulness();
        Long getTotalEvaluations();
        Long getTotalEvaluatedDiagrams();
    }
}
