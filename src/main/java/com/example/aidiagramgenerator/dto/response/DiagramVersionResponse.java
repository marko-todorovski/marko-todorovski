package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.enums.DiagramType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO representing a diagram version.
 */
public class DiagramVersionResponse {

    private UUID id;
    private int versionNumber;
    private UUID parentDiagramId;
    private DiagramType diagramType;
    private String mermaidCode;
    private String explanation;
    private LocalDateTime createdAt;

    public DiagramVersionResponse() {
    }

    public DiagramVersionResponse(UUID id, int versionNumber, UUID parentDiagramId,
                                  DiagramType diagramType, String mermaidCode,
                                  String explanation, LocalDateTime createdAt) {
        this.id = id;
        this.versionNumber = versionNumber;
        this.parentDiagramId = parentDiagramId;
        this.diagramType = diagramType;
        this.mermaidCode = mermaidCode;
        this.explanation = explanation;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public UUID getParentDiagramId() { return parentDiagramId; }
    public void setParentDiagramId(UUID parentDiagramId) { this.parentDiagramId = parentDiagramId; }

    public DiagramType getDiagramType() { return diagramType; }
    public void setDiagramType(DiagramType diagramType) { this.diagramType = diagramType; }

    public String getMermaidCode() { return mermaidCode; }
    public void setMermaidCode(String mermaidCode) { this.mermaidCode = mermaidCode; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
