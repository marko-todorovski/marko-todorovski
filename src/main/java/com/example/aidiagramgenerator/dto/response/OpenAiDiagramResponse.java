package com.example.aidiagramgenerator.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Response DTO for OpenAI diagram generation.
 * 
 * <p>Contains the generated diagram type, Mermaid code, and explanation
 * from the LLM response.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiDiagramResponse {

    private String diagramType;
    private String plantUmlCode;
    private String mermaidCode;
    private String explanation;
    private boolean fallbackUsed;
    private String modelUsed;
    private Long generationTimeMs;

    public OpenAiDiagramResponse() {
    }

    public OpenAiDiagramResponse(String diagramType, String mermaidCode, String explanation) {
        this.diagramType = diagramType;
        this.mermaidCode = mermaidCode;
        this.explanation = explanation;
        this.fallbackUsed = false;
    }

    /**
     * Creates a builder for OpenAiDiagramResponse.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public String getPlantUmlCode() {
        return plantUmlCode;
    }

    public void setPlantUmlCode(String plantUmlCode) {
        this.plantUmlCode = plantUmlCode;
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

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public Long getGenerationTimeMs() {
        return generationTimeMs;
    }

    public void setGenerationTimeMs(Long generationTimeMs) {
        this.generationTimeMs = generationTimeMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiDiagramResponse that = (OpenAiDiagramResponse) o;
        return fallbackUsed == that.fallbackUsed &&
               Objects.equals(diagramType, that.diagramType) &&
               Objects.equals(plantUmlCode, that.plantUmlCode) &&
               Objects.equals(mermaidCode, that.mermaidCode) &&
               Objects.equals(explanation, that.explanation) &&
               Objects.equals(modelUsed, that.modelUsed) &&
               Objects.equals(generationTimeMs, that.generationTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diagramType, plantUmlCode, mermaidCode, explanation, fallbackUsed, modelUsed, generationTimeMs);
    }

    @Override
    public String toString() {
        return "OpenAiDiagramResponse{" +
               "diagramType='" + diagramType + '\'' +
               ", plantUmlCode='" + (plantUmlCode != null ? plantUmlCode.substring(0, Math.min(50, plantUmlCode.length())) + "..." : null) + '\'' +
               ", mermaidCode='" + (mermaidCode != null ? mermaidCode.substring(0, Math.min(50, mermaidCode.length())) + "..." : null) + '\'' +
               ", explanation='" + explanation + '\'' +
               ", fallbackUsed=" + fallbackUsed +
               ", modelUsed='" + modelUsed + '\'' +
               ", generationTimeMs=" + generationTimeMs +
               '}';
    }

    /**
     * Builder for OpenAiDiagramResponse.
     */
    public static class Builder {
        private String diagramType;
        private String plantUmlCode;
        private String mermaidCode;
        private String explanation;
        private boolean fallbackUsed;
        private String modelUsed;
        private Long generationTimeMs;

        public Builder diagramType(String diagramType) {
            this.diagramType = diagramType;
            return this;
        }

        public Builder plantUmlCode(String plantUmlCode) {
            this.plantUmlCode = plantUmlCode;
            return this;
        }

        public Builder mermaidCode(String mermaidCode) {
            this.mermaidCode = mermaidCode;
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public Builder fallbackUsed(boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public Builder generationTimeMs(Long generationTimeMs) {
            this.generationTimeMs = generationTimeMs;
            return this;
        }

        public OpenAiDiagramResponse build() {
            OpenAiDiagramResponse response = new OpenAiDiagramResponse();
            response.diagramType = this.diagramType;
            response.plantUmlCode = this.plantUmlCode;
            response.mermaidCode = this.mermaidCode;
            response.explanation = this.explanation;
            response.fallbackUsed = this.fallbackUsed;
            response.modelUsed = this.modelUsed;
            response.generationTimeMs = this.generationTimeMs;
            return response;
        }
    }
}
