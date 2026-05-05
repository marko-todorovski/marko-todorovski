package com.example.aidiagramgenerator.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing a generated software engineering diagram.
 * Stores the input text, classified diagram type, and generated PlantUML code.
 */
@Entity(name = "DomainDiagram")
@Table(name = "domain_diagrams")
public class Diagram {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "input_text", columnDefinition = "TEXT", nullable = false)
    private String inputText;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type", nullable = false)
    private DiagramType diagramType;

    @Column(name = "plant_uml_code", columnDefinition = "TEXT", nullable = false)
    private String plantUmlCode;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Default constructor required by JPA.
     */
    protected Diagram() {
    }

    /**
     * Creates a new Diagram with the specified parameters.
     *
     * @param inputText    the natural language input text
     * @param diagramType  the classified diagram type
     * @param plantUmlCode the generated PlantUML code
     * @throws IllegalArgumentException if any required field is null or blank
     */
    public Diagram(String inputText, DiagramType diagramType, String plantUmlCode) {
        validateInputText(inputText);
        validateDiagramType(diagramType);
        validatePlantUmlCode(plantUmlCode);
        
        this.inputText = inputText;
        this.diagramType = diagramType;
        this.plantUmlCode = plantUmlCode;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Validation methods

    private void validateInputText(String inputText) {
        if (inputText == null || inputText.isBlank()) {
            throw new IllegalArgumentException("Input text cannot be null or blank");
        }
    }

    private void validateDiagramType(DiagramType diagramType) {
        if (diagramType == null) {
            throw new IllegalArgumentException("Diagram type cannot be null");
        }
    }

    private void validatePlantUmlCode(String plantUmlCode) {
        if (plantUmlCode == null || plantUmlCode.isBlank()) {
            throw new IllegalArgumentException("PlantUML code cannot be null or blank");
        }
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public String getInputText() {
        return inputText;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public String getPlantUmlCode() {
        return plantUmlCode;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters with validation

    public void setInputText(String inputText) {
        validateInputText(inputText);
        this.inputText = inputText;
    }

    public void setDiagramType(DiagramType diagramType) {
        validateDiagramType(diagramType);
        this.diagramType = diagramType;
    }

    public void setPlantUmlCode(String plantUmlCode) {
        validatePlantUmlCode(plantUmlCode);
        this.plantUmlCode = plantUmlCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Diagram diagram = (Diagram) o;
        return Objects.equals(id, diagram.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Diagram{" +
                "id=" + id +
                ", diagramType=" + diagramType +
                ", modelUsed='" + modelUsed + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
