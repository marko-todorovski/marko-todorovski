package com.example.aidiagramgenerator.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable-ish snapshot of diagram source code at a specific version number.
 */
@Entity
@Table(
        name = "diagram_versions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_diagram_versions_diagram_version",
                        columnNames = {"diagram_id", "version_number"}
                )
        },
        indexes = {
                @Index(name = "idx_diagram_versions_diagram_id", columnList = "diagram_id"),
                @Index(name = "idx_diagram_versions_diagram_id_version", columnList = "diagram_id, version_number")
        }
)
public class DiagramVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    @Positive
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @NotBlank
    @Column(name = "source_code", columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 32)
    private DiagramSourceFormat sourceFormat = DiagramSourceFormat.PLANTUML;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private ApplicationUser createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 32)
    private DiagramChangeType changeType = DiagramChangeType.GENERATED;

    @Size(max = 100)
    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Version
    @Column(nullable = false)
    private Long version;

    protected DiagramVersion() {
    }

    public DiagramVersion(Diagram diagram, int versionNumber, String sourceCode, DiagramSourceFormat sourceFormat) {
        this.diagram = diagram;
        this.versionNumber = versionNumber;
        this.sourceCode = sourceCode;
        this.sourceFormat = sourceFormat == null ? DiagramSourceFormat.PLANTUML : sourceFormat;
    }

    @PrePersist
    protected void onCreate() {
        if (sourceFormat == null) {
            sourceFormat = DiagramSourceFormat.PLANTUML;
        }
        if (changeType == null) {
            changeType = DiagramChangeType.GENERATED;
        }
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    public void setDiagram(Diagram diagram) {
        this.diagram = diagram;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public DiagramSourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(DiagramSourceFormat sourceFormat) {
        this.sourceFormat = sourceFormat == null ? DiagramSourceFormat.PLANTUML : sourceFormat;
    }

    public ApplicationUser getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(ApplicationUser createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public DiagramChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(DiagramChangeType changeType) {
        this.changeType = changeType;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiagramVersion that = (DiagramVersion) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DiagramVersion{" +
                "id=" + id +
                ", versionNumber=" + versionNumber +
                ", sourceFormat=" + sourceFormat +
                ", changeType=" + changeType +
                ", modelUsed='" + modelUsed + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
