package com.example.aidiagramgenerator.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagramRequest {

    private String inputText;
    private List<String> entities;
    private List<String> relationships;
    private String detectedIntent;

    public DiagramRequest() {
    }

    public DiagramRequest(String inputText, List<String> entities,
                          List<String> relationships, String detectedIntent) {
        this.inputText = inputText;
        this.entities = entities;
        this.relationships = relationships;
        this.detectedIntent = detectedIntent;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }

    public List<String> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<String> relationships) {
        this.relationships = relationships;
    }

    public String getDetectedIntent() {
        return detectedIntent;
    }

    public void setDetectedIntent(String detectedIntent) {
        this.detectedIntent = detectedIntent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiagramRequest that = (DiagramRequest) o;
        return Objects.equals(inputText, that.inputText)
                && Objects.equals(entities, that.entities)
                && Objects.equals(relationships, that.relationships)
                && Objects.equals(detectedIntent, that.detectedIntent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputText, entities, relationships, detectedIntent);
    }

    @Override
    public String toString() {
        return "DiagramRequest{inputText='" + inputText
                + "', entities=" + entities
                + ", relationships=" + relationships
                + ", detectedIntent='" + detectedIntent + "'}";
    }
}
