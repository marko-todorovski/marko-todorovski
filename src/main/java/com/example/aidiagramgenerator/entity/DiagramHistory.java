package com.example.aidiagramgenerator.entity;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for storing diagram generation history
 */
@Entity
@Table(name = "diagram_history")
public class DiagramHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiagramType diagramType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputType inputType;

    @Column(columnDefinition = "TEXT")
    private String inputContent;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mermaidCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public DiagramHistory() {
    }

    public DiagramHistory(DiagramType diagramType, InputType inputType, String inputContent, String mermaidCode) {
        this.diagramType = diagramType;
        this.inputType = inputType;
        this.inputContent = inputContent;
        this.mermaidCode = mermaidCode;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
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

    public String getMermaidCode() {
        return mermaidCode;
    }

    public void setMermaidCode(String mermaidCode) {
        this.mermaidCode = mermaidCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "DiagramHistory{" +
                "id='" + id + '\'' +
                ", diagramType=" + diagramType +
                ", inputType='" + inputType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
