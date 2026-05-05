package com.example.aidiagramgenerator.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a user evaluation of a generated diagram.
 */
@Entity
@Table(name = "diagram_evaluations")
public class DiagramEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID diagramId;

    @Column(nullable = false)
    private int clarityScore;

    @Column(nullable = false)
    private int correctnessScore;

    @Column(nullable = false)
    private int usefulnessScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public DiagramEvaluation() {
    }

    public DiagramEvaluation(UUID diagramId, int clarityScore, int correctnessScore, int usefulnessScore) {
        this.diagramId = diagramId;
        this.clarityScore = clarityScore;
        this.correctnessScore = correctnessScore;
        this.usefulnessScore = usefulnessScore;
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDiagramId() { return diagramId; }
    public void setDiagramId(UUID diagramId) { this.diagramId = diagramId; }

    public int getClarityScore() { return clarityScore; }
    public void setClarityScore(int clarityScore) { this.clarityScore = clarityScore; }

    public int getCorrectnessScore() { return correctnessScore; }
    public void setCorrectnessScore(int correctnessScore) { this.correctnessScore = correctnessScore; }

    public int getUsefulnessScore() { return usefulnessScore; }
    public void setUsefulnessScore(int usefulnessScore) { this.usefulnessScore = usefulnessScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
