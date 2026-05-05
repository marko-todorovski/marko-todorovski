package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates PlantUML object diagram syntax from parsed input.
 *
 * <p>Entities become named instances; relationships are rendered with {@code -->}. Format:</p>
 * <pre>
 * {@code
 * @startuml
 * object User1
 * object Order1
 * User1 --> Order1
 * @enduml
 * }
 * </pre>
 */
@Component
public class ObjectDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.OBJECT;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> instances = resolveInstances(parsedInput);
        List<String> relationships = sanitizeAll(parsedInput.getRelationships());

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (instances.isEmpty()) {
            sb.append("object User1\n");
            sb.append("object Order1\n");
            sb.append("object Product1\n");
            sb.append("\n");
            sb.append("User1 --> Order1\n");
            sb.append("Order1 --> Product1\n");
        } else {
            for (String instance : instances) {
                sb.append("object ").append(instance).append("\n");
            }
            sb.append("\n");
            if (relationships.isEmpty()) {
                // Auto-chain instances with -->
                for (int i = 0; i < instances.size() - 1; i++) {
                    sb.append(instances.get(i)).append(" --> ").append(instances.get(i + 1)).append("\n");
                }
            } else {
                for (String rel : relationships) {
                    sb.append(rel.replace("->", "-->")).append("\n");
                }
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Resolves instance names from entities.
     * Appends a numeric suffix ("1") to each entity name to form a valid instance identifier.
     */
    private List<String> resolveInstances(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(entities.size());
        for (String entity : entities) {
            String sanitized = entity.replaceAll("[^a-zA-Z0-9_]", "_").trim();
            if (!sanitized.isBlank()) {
                // Append "1" to create a concrete instance name (e.g. User → User1)
                result.add(sanitized + "1");
            }
        }
        return result;
    }

    private List<String> sanitizeAll(List<String> items) {
        if (items == null) return List.of();
        List<String> result = new ArrayList<>(items.size());
        for (String item : items) {
            String sanitized = item.replaceAll("[;]", "").trim();
            if (!sanitized.isBlank()) {
                result.add(sanitized);
            }
        }
        return result;
    }
}
