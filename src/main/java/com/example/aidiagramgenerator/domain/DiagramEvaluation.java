package com.example.aidiagramgenerator.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a human evaluation of a generated diagram.
 * Used for research to assess diagram quality across three dimensions:
 * clarity, correctness, and usefulness.
 */
@Entity(name = "DomainDiagramEvaluation")
@Table(name = "domain_diagram_evaluations")
public class DiagramEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "diagram_id", nullable = false)
    private UUID diagramId;

    @NotNull
    @Min(value = 1, message = "Clarity score must be at least 1")
    @Max(value = 5, message = "Clarity score must be at most 5")
    @Column(name = "clarity_score", nullable = false)
    private Integer clarityScore;

    @NotNull
    @Min(value = 1, message = "Correctness score must be at least 1")
    @Max(value = 5, message = "Correctness score must be at most 5")
    @Column(name = "correctness_score", nullable = false)
    private Integer correctnessScore;

    @NotNull
    @Min(value = 1, message = "Usefulness score must be at least 1")
    @Max(value = 5, message = "Usefulness score must be at most 5")
    @Column(name = "usefulness_score", nullable = false)
    private Integer usefulnessScore;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        this.evaluatedAt = LocalDateTime.now();
    }

    /**
     * Default constructor required by JPA.
     */
    protected DiagramEvaluation() {
    }

    /**
     * Creates a new DiagramEvaluation with the specified scores.
     *
     * @param diagramId        the UUID of the evaluated diagram
     * @param clarityScore     the clarity score (1-5)
     * @param correctnessScore the correctness score (1-5)
     * @param usefulnessScore  the usefulness score (1-5)
     * @throws IllegalArgumentException if any score is out of range or diagramId is null
     */
    public DiagramEvaluation(UUID diagramId, Integer clarityScore, Integer correctnessScore, Integer usefulnessScore) {
        validateDiagramId(diagramId);
        validateScore("Clarity", clarityScore);
        validateScore("Correctness", correctnessScore);
        validateScore("Usefulness", usefulnessScore);

        this.diagramId = diagramId;
        this.clarityScore = clarityScore;
        this.correctnessScore = correctnessScore;
        this.usefulnessScore = usefulnessScore;
    }

    /**
     * Creates a new DiagramEvaluation with scores and a comment.
     */
    public DiagramEvaluation(UUID diagramId, Integer clarityScore, Integer correctnessScore, 
                             Integer usefulnessScore, String comment) {
        this(diagramId, clarityScore, correctnessScore, usefulnessScore);
        this.comment = comment;
    }

    // Validation methods

    private void validateDiagramId(UUID diagramId) {
        if (diagramId == null) {
            throw new IllegalArgumentException("Diagram ID cannot be null");
        }
    }

    private void validateScore(String scoreName, Integer score) {
        if (score == null) {
            throw new IllegalArgumentException(scoreName + " score cannot be null");
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException(scoreName + " score must be between 1 and 5, got: " + score);
        }
    }

    // Getters

    public Long getId() {
        return id;
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public Integer getClarityScore() {
        return clarityScore;
    }

    public Integer getCorrectnessScore() {
        return correctnessScore;
    }

    public Integer getUsefulnessScore() {
        return usefulnessScore;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    /**
     * Calculates the average score across all three dimensions.
     *
     * @return the average score
     */
    public double getAverageScore() {
        return (clarityScore + correctnessScore + usefulnessScore) / 3.0;
    }

    // Setters with validation

    public void setClarityScore(Integer clarityScore) {
        validateScore("Clarity", clarityScore);
        this.clarityScore = clarityScore;
    }

    public void setCorrectnessScore(Integer correctnessScore) {
        validateScore("Correctness", correctnessScore);
        this.correctnessScore = correctnessScore;
    }

    public void setUsefulnessScore(Integer usefulnessScore) {
        validateScore("Usefulness", usefulnessScore);
        this.usefulnessScore = usefulnessScore;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiagramEvaluation that = (DiagramEvaluation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiagramEvaluation{" +
                "id=" + id +
                ", diagramId=" + diagramId +
                ", clarityScore=" + clarityScore +
                ", correctnessScore=" + correctnessScore +
                ", usefulnessScore=" + usefulnessScore +
                ", averageScore=" + String.format("%.2f", getAverageScore()) +
                ", evaluatedAt=" + evaluatedAt +
                '}';
    }
}
