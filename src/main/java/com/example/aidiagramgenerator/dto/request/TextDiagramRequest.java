package com.example.aidiagramgenerator.dto.request;

import com.example.aidiagramgenerator.enums.DiagramType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for generating diagrams from natural language text.
 */
public class TextDiagramRequest {

    @NotBlank(message = "Text description cannot be blank")
    @Size(min = 10, max = 5000, message = "Text must be between 10 and 5000 characters")
    private String text;

    /** Optional diagram type hint. When null the system auto-detects the best type. */
    private DiagramType diagramType;

    public TextDiagramRequest() {
    }

    public TextDiagramRequest(String text, DiagramType diagramType) {
        this.text = text;
        this.diagramType = diagramType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(DiagramType diagramType) {
        this.diagramType = diagramType;
    }
}
