package com.example.aidiagramgenerator.domain;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * JPA entity representing a style profile for diagram generation.
 * Defines layout and visual styling rules for specific diagram types.
 */
@Entity
@Table(name = "style_profiles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"diagram_type", "layout_direction"})
})
public class StyleProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagram_type", nullable = false)
    private DiagramType diagramType;

    @Column(name = "layout_direction", length = 50)
    private String layoutDirection;

    @Column(name = "arrow_style", length = 50)
    private String arrowStyle;

    @Column(name = "spacing_rule", length = 100)
    private String spacingRule;

    /**
     * Default constructor required by JPA.
     */
    protected StyleProfile() {
    }

    /**
     * Creates a new StyleProfile with the specified parameters.
     *
     * @param diagramType     the diagram type this profile applies to
     * @param layoutDirection the layout direction (e.g., "top-down", "left-right")
     * @param arrowStyle      the arrow style (e.g., "solid", "dashed")
     * @param spacingRule     the spacing rule (e.g., "compact", "expanded")
     * @throws IllegalArgumentException if diagramType is null
     */
    public StyleProfile(DiagramType diagramType, String layoutDirection, String arrowStyle, String spacingRule) {
        validateDiagramType(diagramType);
        
        this.diagramType = diagramType;
        this.layoutDirection = layoutDirection;
        this.arrowStyle = arrowStyle;
        this.spacingRule = spacingRule;
    }

    // Validation methods

    private void validateDiagramType(DiagramType diagramType) {
        if (diagramType == null) {
            throw new IllegalArgumentException("Diagram type cannot be null");
        }
    }

    // Getters

    public Long getId() {
        return id;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public String getLayoutDirection() {
        return layoutDirection;
    }

    public String getArrowStyle() {
        return arrowStyle;
    }

    public String getSpacingRule() {
        return spacingRule;
    }

    // Setters with validation

    public void setDiagramType(DiagramType diagramType) {
        validateDiagramType(diagramType);
        this.diagramType = diagramType;
    }

    public void setLayoutDirection(String layoutDirection) {
        this.layoutDirection = layoutDirection;
    }

    public void setArrowStyle(String arrowStyle) {
        this.arrowStyle = arrowStyle;
    }

    public void setSpacingRule(String spacingRule) {
        this.spacingRule = spacingRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StyleProfile that = (StyleProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StyleProfile{" +
                "id=" + id +
                ", diagramType=" + diagramType +
                ", layoutDirection='" + layoutDirection + '\'' +
                ", arrowStyle='" + arrowStyle + '\'' +
                ", spacingRule='" + spacingRule + '\'' +
                '}';
    }
}
