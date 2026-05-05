package com.example.aidiagramgenerator.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * Structured request for diagram generation via LLM.
 * 
 * <p>Contains entities, actions, and relationships that describe
 * the system to be visualized as a diagram.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredDiagramRequest {

    private List<Entity> entities;
    private List<Action> actions;
    private List<Relationship> relationships;
    private String context;
    private String preferredDiagramType;

    public StructuredDiagramRequest() {
    }

    public StructuredDiagramRequest(List<Entity> entities, List<Action> actions, 
                                    List<Relationship> relationships, String context,
                                    String preferredDiagramType) {
        this.entities = entities;
        this.actions = actions;
        this.relationships = relationships;
        this.context = context;
        this.preferredDiagramType = preferredDiagramType;
    }

    // Getters and Setters

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = entities;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public List<Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getPreferredDiagramType() {
        return preferredDiagramType;
    }

    public void setPreferredDiagramType(String preferredDiagramType) {
        this.preferredDiagramType = preferredDiagramType;
    }

    /**
     * Converts the structured request to a natural language description.
     * 
     * @return natural language representation of the request
     */
    public String toNaturalLanguage() {
        StringBuilder sb = new StringBuilder();
        
        if (context != null && !context.isBlank()) {
            sb.append("Context: ").append(context).append("\n\n");
        }
        
        if (entities != null && !entities.isEmpty()) {
            sb.append("Entities:\n");
            for (Entity entity : entities) {
                sb.append("- ").append(entity.getName());
                if (entity.getType() != null) {
                    sb.append(" (").append(entity.getType()).append(")");
                }
                if (entity.getAttributes() != null && !entity.getAttributes().isEmpty()) {
                    sb.append(" with attributes: ").append(String.join(", ", entity.getAttributes()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        if (actions != null && !actions.isEmpty()) {
            sb.append("Actions:\n");
            for (Action action : actions) {
                sb.append("- ").append(action.getSubject()).append(" ")
                  .append(action.getVerb()).append(" ").append(action.getObject());
                if (action.getDescription() != null) {
                    sb.append(": ").append(action.getDescription());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        if (relationships != null && !relationships.isEmpty()) {
            sb.append("Relationships:\n");
            for (Relationship rel : relationships) {
                sb.append("- ").append(rel.getFrom()).append(" ")
                  .append(rel.getType()).append(" ").append(rel.getTo());
                if (rel.getLabel() != null) {
                    sb.append(" (").append(rel.getLabel()).append(")");
                }
                sb.append("\n");
            }
        }
        
        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StructuredDiagramRequest that = (StructuredDiagramRequest) o;
        return Objects.equals(entities, that.entities) &&
               Objects.equals(actions, that.actions) &&
               Objects.equals(relationships, that.relationships) &&
               Objects.equals(context, that.context) &&
               Objects.equals(preferredDiagramType, that.preferredDiagramType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entities, actions, relationships, context, preferredDiagramType);
    }

    // Nested classes for structured data

    /**
     * Represents an entity in the diagram.
     */
    public static class Entity {
        private String name;
        private String type;
        private List<String> attributes;
        private List<String> methods;

        public Entity() {
        }

        public Entity(String name, String type, List<String> attributes, List<String> methods) {
            this.name = name;
            this.type = type;
            this.attributes = attributes;
            this.methods = methods;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getAttributes() {
            return attributes;
        }

        public void setAttributes(List<String> attributes) {
            this.attributes = attributes;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entity entity = (Entity) o;
            return Objects.equals(name, entity.name) &&
                   Objects.equals(type, entity.type) &&
                   Objects.equals(attributes, entity.attributes) &&
                   Objects.equals(methods, entity.methods);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, attributes, methods);
        }
    }

    /**
     * Represents an action between entities.
     */
    public static class Action {
        private String subject;
        private String verb;
        private String object;
        private String description;

        public Action() {
        }

        public Action(String subject, String verb, String object, String description) {
            this.subject = subject;
            this.verb = verb;
            this.object = object;
            this.description = description;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getVerb() {
            return verb;
        }

        public void setVerb(String verb) {
            this.verb = verb;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Action action = (Action) o;
            return Objects.equals(subject, action.subject) &&
                   Objects.equals(verb, action.verb) &&
                   Objects.equals(object, action.object) &&
                   Objects.equals(description, action.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, verb, object, description);
        }
    }

    /**
     * Represents a relationship between entities.
     */
    public static class Relationship {
        private String from;
        private String to;
        private String type;
        private String label;
        private String cardinality;

        public Relationship() {
        }

        public Relationship(String from, String to, String type, String label, String cardinality) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.label = label;
            this.cardinality = cardinality;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getCardinality() {
            return cardinality;
        }

        public void setCardinality(String cardinality) {
            this.cardinality = cardinality;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Relationship that = (Relationship) o;
            return Objects.equals(from, that.from) &&
                   Objects.equals(to, that.to) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(label, that.label) &&
                   Objects.equals(cardinality, that.cardinality);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, type, label, cardinality);
        }
    }
}
