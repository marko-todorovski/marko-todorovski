package com.example.aidiagramgenerator.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity representing a generated software engineering diagram.
 * Stores the input text, classified diagram type, and generated PlantUML code.
 */
@Entity(name = "DomainDiagram")
@Table(
        name = "domain_diagrams",
        indexes = {
                @Index(name = "idx_domain_diagrams_project_id", columnList = "project_id"),
                @Index(name = "idx_domain_diagrams_owner_id", columnList = "owner_id")
        }
)
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private ApplicationUser owner;

    @Size(max = 150)
    @Column(length = 150)
    private String name;

    @Size(max = 1000)
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", length = 32)
    private DiagramSourceFormat sourceFormat = DiagramSourceFormat.PLANTUML;

    @Column(name = "original_prompt", columnDefinition = "TEXT")
    private String originalPrompt;

    @Column(name = "current_source_code", columnDefinition = "TEXT")
    private String currentSourceCode;

    @Column(name = "current_version_number")
    private Integer currentVersionNumber;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column
    private Long lockVersion;

    @JsonIgnore
    @OneToMany(mappedBy = "diagram", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private Set<DiagramVersion> versions = new LinkedHashSet<>();

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
        this.sourceFormat = DiagramSourceFormat.PLANTUML;
        this.originalPrompt = inputText;
        this.currentSourceCode = plantUmlCode;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        normalizeNewFields();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeNewFields();
        this.updatedAt = Instant.now();
    }

    private void normalizeNewFields() {
        if (sourceFormat == null) {
            sourceFormat = DiagramSourceFormat.PLANTUML;
        }
        if (currentSourceCode == null || currentSourceCode.isBlank()) {
            currentSourceCode = plantUmlCode;
        }
        if (originalPrompt == null || originalPrompt.isBlank()) {
            originalPrompt = inputText;
        }
        if (name != null) {
            name = name.trim();
        }
        if (description != null) {
            description = description.trim();
        }
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

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ApplicationUser getOwner() {
        return owner;
    }

    public void setOwner(ApplicationUser owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiagramSourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(DiagramSourceFormat sourceFormat) {
        this.sourceFormat = sourceFormat == null ? DiagramSourceFormat.PLANTUML : sourceFormat;
    }

    public String getOriginalPrompt() {
        return originalPrompt;
    }

    public void setOriginalPrompt(String originalPrompt) {
        this.originalPrompt = originalPrompt;
    }

    public String getCurrentSourceCode() {
        return currentSourceCode;
    }

    public void setCurrentSourceCode(String currentSourceCode) {
        this.currentSourceCode = currentSourceCode;
    }

    public Integer getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public void setCurrentVersionNumber(Integer currentVersionNumber) {
        this.currentVersionNumber = currentVersionNumber;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public Set<DiagramVersion> getVersions() {
        return versions;
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
        if (currentSourceCode == null) {
            this.currentSourceCode = plantUmlCode;
        }
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
