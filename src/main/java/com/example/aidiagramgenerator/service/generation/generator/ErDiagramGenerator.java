package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Mermaid ER-diagram syntax from parsed input.
 * 
 * <p>Converts entities and relationships into ER diagram notation with proper
 * cardinality markers (||, o{, |{, etc.).</p>
 */
@Component
public class ErDiagramGenerator implements DiagramGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ErDiagramGenerator.class);

    /** Pattern to extract relationship info: "EntityA -> EntityB" or "EntityA - EntityB" */
    private static final Pattern RELATIONSHIP_PATTERN = 
            Pattern.compile("(\\w+)\\s*(?:->|--|-)\\s*(\\w+)(?:\\s*:\\s*(\\w+))?");

    @Override
    public DiagramType supports() {
        return DiagramType.ER;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        List<String> relationships = parsedInput.getRelationships();

        logger.debug("Generating ER diagram with {} entities and {} relationships",
                entities.size(), relationships.size());

        StringBuilder sb = new StringBuilder("erDiagram\n");

        if (entities.isEmpty() && relationships.isEmpty()) {
            // Default example diagram when no input is provided
            sb.append("    USER ||--o{ SESSION : creates\n")
              .append("    USER {\n")
              .append("        string id\n")
              .append("        string username\n")
              .append("        string email\n")
              .append("    }\n")
              .append("    SESSION {\n")
              .append("        string id\n")
              .append("        string userId\n")
              .append("        datetime createdAt\n")
              .append("    }\n");
        } else {
            // Track which entities have been defined
            Set<String> definedEntities = new HashSet<>();

            // Generate relationships from the parsed input
            generateRelationships(relationships, entities, sb, definedEntities);

            // Generate entity definitions
            for (String entity : entities) {
                String upper = entity.toUpperCase();
                if (!definedEntities.contains(upper)) {
                    definedEntities.add(upper);
                }
                sb.append("    ").append(upper).append(" {\n")
                  .append("        string id PK\n")
                  .append("        string name\n")
                  .append("    }\n");
            }

            // If we have entities but no explicit relationships, create default connections
            if (relationships.isEmpty() && entities.size() >= 2) {
                logger.debug("No explicit relationships found, creating default connections");
                for (int i = 0; i < entities.size() - 1; i++) {
                    String from = entities.get(i).toUpperCase();
                    String to = entities.get(i + 1).toUpperCase();
                    sb.append("    ").append(from)
                      .append(" ||--o{ ").append(to)
                      .append(" : has\n");
                }
            }
        }

        String result = sb.toString().stripTrailing();
        logger.debug("Generated ER diagram:\n{}", result);
        return result;
    }

    /**
     * Generates PlantUML/Mermaid ER relationship edges from parsed relationships.
     * 
     * @param relationships the list of relationship strings
     * @param entities the list of known entities
     * @param sb the StringBuilder to append to
     * @param definedEntities set of already defined entities
     */
    private void generateRelationships(List<String> relationships, List<String> entities, 
                                        StringBuilder sb, Set<String> definedEntities) {
        Set<String> entitySet = new HashSet<>();
        for (String entity : entities) {
            entitySet.add(entity.toUpperCase());
        }

        for (String rel : relationships) {
            Matcher matcher = RELATIONSHIP_PATTERN.matcher(rel);
            if (matcher.find()) {
                String from = matcher.group(1).toUpperCase();
                String to = matcher.group(2).toUpperCase();
                String relationshipName = matcher.group(3);

                // Ensure both entities exist
                if (!entitySet.contains(from)) {
                    entitySet.add(from);
                }
                if (!entitySet.contains(to)) {
                    entitySet.add(to);
                }

                // Determine cardinality based on relationship name or default
                String cardinality = determineCardinality(relationshipName);
                String relLabel = relationshipName != null ? relationshipName : "relates_to";

                // Generate the relationship line
                // Format: EntityA ||--o{ EntityB : relationshipName
                sb.append("    ").append(from)
                  .append(" ").append(cardinality).append(" ")
                  .append(to).append(" : ").append(relLabel).append("\n");

                definedEntities.add(from);
                definedEntities.add(to);

                logger.debug("Generated relationship: {} {} {} : {}",
                        from, cardinality, to, relLabel);
            }
        }
    }

    /**
     * Determines the ER cardinality notation based on relationship semantics.
     * 
     * @param relationshipName the name of the relationship (nullable)
     * @return the Mermaid ER cardinality notation
     */
    private String determineCardinality(String relationshipName) {
        if (relationshipName == null) {
            return "||--o{";  // Default: one-to-many
        }

        String lower = relationshipName.toLowerCase();

        // One-to-one relationships
        if (lower.contains("has_one") || lower.contains("belongs_to") || 
            lower.contains("is") || lower.contains("owns")) {
            return "||--||";
        }

        // Many-to-many relationships
        if (lower.contains("has_many") || lower.contains("many") || 
            lower.contains("contains_many") || lower.contains("associated")) {
            return "}o--o{";
        }

        // One-to-many relationships (default)
        if (lower.contains("has") || lower.contains("contains") || 
            lower.contains("creates") || lower.contains("manages")) {
            return "||--o{";
        }

        // Zero-or-one relationships
        if (lower.contains("may_have") || lower.contains("optional")) {
            return "|o--o|";
        }

        // Default to one-to-many
        return "||--o{";
    }
}
