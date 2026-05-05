package com.example.aidiagramgenerator.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the semantic model extracted from natural language input.
 * Contains entities, relationships between entities, and identified actions.
 * This model serves as an intermediate representation before diagram generation.
 */
public final class SemanticModel {

    private final List<EntityNode> entities;
    private final List<Relationship> relationships;
    private final List<String> actions;

    /**
     * Creates an empty SemanticModel.
     */
    public SemanticModel() {
        this.entities = new ArrayList<>();
        this.relationships = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    /**
     * Creates a SemanticModel with the specified entities, relationships, and actions.
     *
     * @param entities      the list of entity nodes
     * @param relationships the list of relationships
     * @param actions       the list of actions
     */
    public SemanticModel(List<EntityNode> entities, List<Relationship> relationships, List<String> actions) {
        this.entities = entities != null ? new ArrayList<>(entities) : new ArrayList<>();
        this.relationships = relationships != null ? new ArrayList<>(relationships) : new ArrayList<>();
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    /**
     * Returns an unmodifiable view of the entities list.
     *
     * @return the list of entity nodes
     */
    public List<EntityNode> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * Returns an unmodifiable view of the relationships list.
     *
     * @return the list of relationships
     */
    public List<Relationship> getRelationships() {
        return Collections.unmodifiableList(relationships);
    }

    /**
     * Returns an unmodifiable view of the actions list.
     *
     * @return the list of actions
     */
    public List<String> getActions() {
        return Collections.unmodifiableList(actions);
    }

    /**
     * Adds an entity to the model.
     *
     * @param entity the entity to add
     * @throws IllegalArgumentException if entity is null
     */
    public void addEntity(EntityNode entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        this.entities.add(entity);
    }

    /**
     * Adds a relationship to the model.
     *
     * @param relationship the relationship to add
     * @throws IllegalArgumentException if relationship is null
     */
    public void addRelationship(Relationship relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("Relationship cannot be null");
        }
        this.relationships.add(relationship);
    }

    /**
     * Adds an action to the model.
     *
     * @param action the action to add
     * @throws IllegalArgumentException if action is null or blank
     */
    public void addAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or blank");
        }
        this.actions.add(action.trim());
    }

    /**
     * Finds an entity by name.
     *
     * @param name the entity name to find
     * @return an Optional containing the entity if found, empty otherwise
     */
    public Optional<EntityNode> findEntityByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return entities.stream()
                .filter(e -> e.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Returns all relationships involving the given entity.
     *
     * @param entityName the entity name
     * @return a list of relationships involving the entity
     */
    public List<Relationship> findRelationshipsForEntity(String entityName) {
        if (entityName == null) {
            return Collections.emptyList();
        }
        return relationships.stream()
                .filter(r -> r.involves(entityName))
                .toList();
    }

    /**
     * Checks if the model has any entities.
     *
     * @return true if the model has entities, false otherwise
     */
    public boolean hasEntities() {
        return !entities.isEmpty();
    }

    /**
     * Checks if the model has any relationships.
     *
     * @return true if the model has relationships, false otherwise
     */
    public boolean hasRelationships() {
        return !relationships.isEmpty();
    }

    /**
     * Checks if the model has any actions.
     *
     * @return true if the model has actions, false otherwise
     */
    public boolean hasActions() {
        return !actions.isEmpty();
    }

    /**
     * Checks if the model is empty (no entities, relationships, or actions).
     *
     * @return true if the model is empty, false otherwise
     */
    public boolean isEmpty() {
        return entities.isEmpty() && relationships.isEmpty() && actions.isEmpty();
    }

    /**
     * Returns the total count of entities.
     *
     * @return the number of entities
     */
    public int getEntityCount() {
        return entities.size();
    }

    /**
     * Returns the total count of relationships.
     *
     * @return the number of relationships
     */
    public int getRelationshipCount() {
        return relationships.size();
    }

    /**
     * Returns the total count of actions.
     *
     * @return the number of actions
     */
    public int getActionCount() {
        return actions.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticModel that = (SemanticModel) o;
        return Objects.equals(entities, that.entities) &&
               Objects.equals(relationships, that.relationships) &&
               Objects.equals(actions, that.actions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entities, relationships, actions);
    }

    @Override
    public String toString() {
        return "SemanticModel{" +
                "entities=" + entities.size() +
                ", relationships=" + relationships.size() +
                ", actions=" + actions.size() +
                '}';
    }
}
