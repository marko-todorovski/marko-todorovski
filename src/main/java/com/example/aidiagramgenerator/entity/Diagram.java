package com.example.aidiagramgenerator.entity;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a generated diagram.
 */
@Entity
@Table(name = "diagrams")
public class Diagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputType inputType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String inputContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagramType diagramType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mermaidCode;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private int versionNumber = 1;

    @Column
    private UUID parentDiagramId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Diagram() {
    }

    public Diagram(InputType inputType, String inputContent, DiagramType diagramType, String mermaidCode, String explanation) {
        this.inputType = inputType;
        this.inputContent = inputContent;
        this.diagramType = diagramType;
        this.mermaidCode = mermaidCode;
        this.explanation = explanation;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public InputType getInputType() {
        return inputType;
    }

    public void setInputType(InputType inputType) {
        this.inputType = inputType;
    }

    public String getInputContent() {
        return inputContent;
    }

    public void setInputContent(String inputContent) {
        this.inputContent = inputContent;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }

    public String getMermaidCode() {
        return mermaidCode;
    }

    public void setMermaidCode(String mermaidCode) {
        this.mermaidCode = mermaidCode;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public UUID getParentDiagramId() {
        return parentDiagramId;
    }

    public void setParentDiagramId(UUID parentDiagramId) {
        this.parentDiagramId = parentDiagramId;
    }

    @Override
    public String toString() {
        return "Diagram{" +
                "id=" + id +
                ", inputType=" + inputType +
                ", diagramType=" + diagramType +
                ", versionNumber=" + versionNumber +
                ", parentDiagramId=" + parentDiagramId +
                ", createdAt=" + createdAt +
                '}';
    }
}
