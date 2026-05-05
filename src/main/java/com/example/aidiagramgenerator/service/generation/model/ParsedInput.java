package com.example.aidiagramgenerator.service.generation.model;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured representation of parsed input content.
 * Serves as the intermediate data model between input parsing and diagram generation.
 *
 * <p>This is deliberately a rich, mutable value object so different parsers can populate
 * whichever fields are relevant to their input type.</p>
 */
public class ParsedInput {

    /** The original raw content before parsing. */
    private final String rawContent;

    /** How the input was provided (TEXT, XML, URL). */
    private final InputType inputType;

    /** Optional hint — when the caller already knows the desired diagram type. */
    private DiagramType diagramTypeHint;

    /** Entities/classes/components extracted from the input. */
    private List<String> entities = new ArrayList<>();

    /** Relationships between entities (e.g. "User -> AuthService"). */
    private List<String> relationships = new ArrayList<>();

    /** Extracted actions/verbs (e.g. "creates", "sends", "enrolls in"). */
    private List<String> actions = new ArrayList<>();

    /** Key-value metadata extracted during parsing (e.g. "title", "description"). */
    private Map<String, String> metadata = new HashMap<>();

    /** Keywords found in the input — useful for rule-based classification. */
    private List<String> keywords = new ArrayList<>();

    public ParsedInput(String rawContent, InputType inputType) {
        this.rawContent = rawContent;
        this.inputType = inputType;
    }

    // --- Getters & Setters ---

    public String getRawContent() {
        return rawContent;
    }

    public InputType getInputType() {
        return inputType;
    }

    public DiagramType getDiagramTypeHint() {
        return diagramTypeHint;
    }

    public void setDiagramTypeHint(DiagramType diagramTypeHint) {
        this.diagramTypeHint = diagramTypeHint;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }

    public void addEntity(String entity) {
        this.entities.add(entity);
    }

    public List<String> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<String> relationships) {
        this.relationships = relationships;
    }

    public void addRelationship(String relationship) {
        this.relationships.add(relationship);
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public void addAction(String action) {
        this.actions.add(action);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public void addKeyword(String keyword) {
        this.keywords.add(keyword);
    }

    @Override
    public String toString() {
        return "ParsedInput{" +
                "inputType=" + inputType +
                ", diagramTypeHint=" + diagramTypeHint +
                ", entities=" + entities.size() +
                ", relationships=" + relationships.size() +
                ", actions=" + actions.size() +
                ", keywords=" + keywords +
                '}';
    }
}
