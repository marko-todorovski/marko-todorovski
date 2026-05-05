package com.example.aidiagramgenerator.service.generation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Container for NLP parsing results.
 * Holds all extracted entities, relationships, and actions from natural language text.
 */
public class NlpParseResult {

    /** List of extracted entities (nouns, multi-word entities). */
    private final List<ExtractedEntity> entities;

    /** List of extracted relationships between entities. */
    private final List<ExtractedRelationship> relationships;

    /** List of extracted actions (verbs with subjects and objects). */
    private final List<ExtractedAction> actions;

    /** The original input text that was parsed. */
    private final String originalText;

    /** Time taken to parse in milliseconds. */
    private long parseTimeMs;

    public NlpParseResult(String originalText) {
        this.originalText = originalText;
        this.entities = new ArrayList<>();
        this.relationships = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    // --- Entity management ---

    public void addEntity(ExtractedEntity entity) {
        if (!entities.contains(entity)) {
            entities.add(entity);
        }
    }

    public List<ExtractedEntity> getEntities() {
        return new ArrayList<>(entities);
    }

    public List<String> getEntityNames() {
        return entities.stream()
                .map(ExtractedEntity::getName)
                .collect(Collectors.toList());
    }

    // --- Relationship management ---

    public void addRelationship(ExtractedRelationship relationship) {
        if (!relationships.contains(relationship)) {
            relationships.add(relationship);
        }
    }

    public List<ExtractedRelationship> getRelationships() {
        return new ArrayList<>(relationships);
    }

    public List<String> getRelationshipStrings() {
        return relationships.stream()
                .map(ExtractedRelationship::toArrowNotation)
                .collect(Collectors.toList());
    }

    // --- Action management ---

    public void addAction(ExtractedAction action) {
        if (!actions.contains(action)) {
            actions.add(action);
        }
    }

    public List<ExtractedAction> getActions() {
        return new ArrayList<>(actions);
    }

    public List<String> getActionVerbs() {
        return actions.stream()
                .map(ExtractedAction::getVerb)
                .distinct()
                .collect(Collectors.toList());
    }

    // --- Metadata ---

    public String getOriginalText() {
        return originalText;
    }

    public long getParseTimeMs() {
        return parseTimeMs;
    }

    public void setParseTimeMs(long parseTimeMs) {
        this.parseTimeMs = parseTimeMs;
    }

    public int getTotalExtractedCount() {
        return entities.size() + relationships.size() + actions.size();
    }

    /**
     * Checks if any meaningful content was extracted.
     */
    public boolean hasContent() {
        return !entities.isEmpty() || !relationships.isEmpty() || !actions.isEmpty();
    }

    @Override
    public String toString() {
        return "NlpParseResult{" +
                "entities=" + entities.size() +
                ", relationships=" + relationships.size() +
                ", actions=" + actions.size() +
                ", parseTimeMs=" + parseTimeMs +
                '}';
    }
}
