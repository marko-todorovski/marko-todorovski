package com.example.aidiagramgenerator.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * DTO for diagram generation requests.
 * Contains the natural language text to be converted into a diagram.
 * Text is optional when diagramType is explicitly specified.
 */
public class GenerationRequest {

    @JsonAlias({"description", "inputText"})
    @Size(max = 10000, message = "Text must not exceed 10000 characters")
    private String text;

    /**
     * Optional diagram type specified by the frontend.
     * Accepts values like "class diagram", "sequence diagram", "er diagram",
     * "architecture", "C4 context", or raw enum names like "CLASS", "SEQUENCE".
     * When null, the backend auto-classifies from the text.
     */
    private String diagramType;

    /**
     * Optional seed for deterministic layout generation.
     * When provided, the same seed will always produce the same layout variation.
     * When null, a random layout is generated each time.
     */
    private Long seed;

    /**
     * Default constructor for deserialization.
     */
    public GenerationRequest() {
    }

    /**
     * Creates a new GenerationRequest with the specified text.
     *
     * @param text the natural language text
     */
    public GenerationRequest(String text) {
        this.text = text;
    }

    /**
     * Creates a new GenerationRequest with text and seed.
     *
     * @param text the natural language text
     * @param seed the random seed for deterministic layout
     */
    public GenerationRequest(String text, Long seed) {
        this.text = text;
        this.seed = seed;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    /**
     * When {@code true}, classification is skipped and the diagram is generated
     * directly using the provided {@code diagramType}.  Intended for use after
     * the user confirms a SUGGEST response.
     */
    private boolean forceGenerate;

    public boolean isForceGenerate() {
        return forceGenerate;
    }

    public void setForceGenerate(boolean forceGenerate) {
        this.forceGenerate = forceGenerate;
    }

    @Override
    public String toString() {
        return "GenerationRequest{" +
                "textLength=" + (text != null ? text.length() : 0) +
                ", diagramType='" + diagramType + '\'' +
                ", seed=" + seed +
                ", forceGenerate=" + forceGenerate +
                '}';
    }
}
