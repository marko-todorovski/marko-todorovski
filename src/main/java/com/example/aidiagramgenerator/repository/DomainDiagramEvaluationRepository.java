package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.DiagramEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for domain DiagramEvaluation entities.
 * Provides methods for querying evaluation metrics for research purposes.
 */
@Repository
public interface DomainDiagramEvaluationRepository extends JpaRepository<DiagramEvaluation, Long> {

    /**
     * Find all evaluations for a specific diagram.
     *
     * @param diagramId the diagram UUID
     * @return list of evaluations
     */
    List<DiagramEvaluation> findByDiagramId(UUID diagramId);

    /**
     * Check if any evaluations exist for a diagram.
     *
     * @param diagramId the diagram UUID
     * @return true if evaluations exist
     */
    boolean existsByDiagramId(UUID diagramId);

    /**
     * Count evaluations for a specific diagram.
     *
     * @param diagramId the diagram UUID
     * @return count of evaluations
     */
    long countByDiagramId(UUID diagramId);

    /**
     * Get average scores for a specific diagram.
     */
    @Query("SELECT AVG(e.clarityScore) as avgClarity, " +
           "AVG(e.correctnessScore) as avgCorrectness, " +
           "AVG(e.usefulnessScore) as avgUsefulness, " +
           "COUNT(e) as totalEvaluations " +
           "FROM DomainDiagramEvaluation e WHERE e.diagramId = :diagramId")
    Optional<EvaluationMetrics> findMetricsByDiagramId(@Param("diagramId") UUID diagramId);

    /**
     * Get global metrics across all evaluations.
     */
    @Query("SELECT AVG(e.clarityScore) as avgClarity, " +
           "AVG(e.correctnessScore) as avgCorrectness, " +
           "AVG(e.usefulnessScore) as avgUsefulness, " +
           "COUNT(e) as totalEvaluations, " +
           "COUNT(DISTINCT e.diagramId) as totalDiagramsEvaluated " +
           "FROM DomainDiagramEvaluation e")
    GlobalEvaluationMetrics findGlobalMetrics();

    /**
     * Find the most recent evaluations.
     */
    @Query("SELECT e FROM DomainDiagramEvaluation e ORDER BY e.evaluatedAt DESC LIMIT 10")
    List<DiagramEvaluation> findRecentEvaluations();

    /**
     * Projection interface for diagram-specific metrics.
     */
    interface EvaluationMetrics {
        Double getAvgClarity();
        Double getAvgCorrectness();
        Double getAvgUsefulness();
        Long getTotalEvaluations();
    }

    /**
     * Projection interface for global metrics.
     */
    interface GlobalEvaluationMetrics {
        Double getAvgClarity();
        Double getAvgCorrectness();
        Double getAvgUsefulness();
        Long getTotalEvaluations();
        Long getTotalDiagramsEvaluated();
    }
}
