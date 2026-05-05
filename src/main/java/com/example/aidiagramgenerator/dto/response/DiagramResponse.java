package com.example.aidiagramgenerator.dto.response;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO containing the generated diagram.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagramResponse {

    private UUID id;
    private DiagramType diagramType;
    private String mermaidCode;
    private String explanation;
    private List<String> detectedKeywords;
    private List<String> rulesTriggered;
    private String generationMode;
    private String extractedTextPreview;

    public DiagramResponse() {
    }

    /**
     * Constructor for backward compatibility.
     */
    public DiagramResponse(DiagramType diagramType, String mermaidCode, String explanation) {
        this.diagramType = diagramType;
        this.mermaidCode = mermaidCode;
        this.explanation = explanation;
    }

    /**
     * Full constructor with explainability trace.
     */
    public DiagramResponse(DiagramType diagramType, String mermaidCode, String explanation,
                           List<String> detectedKeywords, List<String> rulesTriggered) {
        this.diagramType = diagramType;
        this.mermaidCode = mermaidCode;
        this.explanation = explanation;
        this.detectedKeywords = detectedKeywords;
        this.rulesTriggered = rulesTriggered;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public List<String> getDetectedKeywords() {
        return detectedKeywords;
    }

    public void setDetectedKeywords(List<String> detectedKeywords) {
        this.detectedKeywords = detectedKeywords;
    }

    public List<String> getRulesTriggered() {
        return rulesTriggered;
    }

    public void setRulesTriggered(List<String> rulesTriggered) {
        this.rulesTriggered = rulesTriggered;
    }

    public String getGenerationMode() {
        return generationMode;
    }

    public void setGenerationMode(String generationMode) {
        this.generationMode = generationMode;
    }

    public String getExtractedTextPreview() {
        return extractedTextPreview;
    }

    public void setExtractedTextPreview(String extractedTextPreview) {
        this.extractedTextPreview = extractedTextPreview;
    }
}
