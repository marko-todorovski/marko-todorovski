package com.example.aidiagramgenerator.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an entity node in the semantic model.
 * An entity has a name and a list of attributes.
 */
public final class EntityNode {

    private final String name;
    private final List<String> attributes;

    /**
     * Creates a new EntityNode with the specified name and no attributes.
     *
     * @param name the entity name
     * @throws IllegalArgumentException if name is null or blank
     */
    public EntityNode(String name) {
        this(name, new ArrayList<>());
    }

    /**
     * Creates a new EntityNode with the specified name and attributes.
     *
     * @param name       the entity name
     * @param attributes the list of attributes
     * @throws IllegalArgumentException if name is null or blank
     */
    public EntityNode(String name, List<String> attributes) {
        validateName(name);
        this.name = name.trim();
        this.attributes = attributes != null ? new ArrayList<>(attributes) : new ArrayList<>();
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Entity name cannot be null or blank");
        }
    }

    /**
     * Returns the entity name.
     *
     * @return the entity name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns an unmodifiable view of the attributes list.
     *
     * @return the list of attributes
     */
    public List<String> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    /**
     * Adds an attribute to this entity.
     *
     * @param attribute the attribute to add
     * @throws IllegalArgumentException if attribute is null or blank
     */
    public void addAttribute(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("Attribute cannot be null or blank");
        }
        this.attributes.add(attribute.trim());
    }

    /**
     * Removes an attribute from this entity.
     *
     * @param attribute the attribute to remove
     * @return true if the attribute was removed, false otherwise
     */
    public boolean removeAttribute(String attribute) {
        return this.attributes.remove(attribute);
    }

    /**
     * Checks if this entity has any attributes.
     *
     * @return true if the entity has attributes, false otherwise
     */
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityNode that = (EntityNode) o;
        return Objects.equals(name, that.name) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attributes);
    }

    @Override
    public String toString() {
        return "EntityNode{" +
                "name='" + name + '\'' +
                ", attributes=" + attributes +
                '}';
    }
}
