package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates PlantUML collaboration diagram syntax from parsed input.
 *
 * <p>A collaboration diagram shows objects interacting via numbered messages.
 * Each entity becomes an {@code object} block; relationships are rendered as
 * numbered messages between instances. Format:</p>
 * <pre>
 * {@code
 * @startuml
 * object Client
 * object Server
 * object Database
 *
 * Client --> Server : 1. request
 * Server --> Database : 2. query
 * Database --> Server : 3. result
 * Server --> Client : 4. response
 * @enduml
 * }
 * </pre>
 */
@Component
public class CollaborationDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.COLLABORATION;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = sanitizeAll(parsedInput.getEntities());
        List<String> actions  = sanitizeAll(parsedInput.getActions());

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (entities.isEmpty()) {
            sb.append("object Client\n");
            sb.append("object Server\n");
            sb.append("object Database\n");
            sb.append("\n");
            sb.append("Client --> Server : 1. request\n");
            sb.append("Server --> Database : 2. query\n");
            sb.append("Database --> Server : 3. result\n");
            sb.append("Server --> Client : 4. response\n");
        } else {
            for (String entity : entities) {
                sb.append("object ").append(entity).append("\n");
            }
            sb.append("\n");
            // Chain objects with numbered messages using actions as labels
            for (int i = 0; i < entities.size() - 1; i++) {
                String label = (i < actions.size()) ? actions.get(i) : "message";
                sb.append(entities.get(i))
                  .append(" --> ")
                  .append(entities.get(i + 1))
                  .append(" : ").append(i + 1).append(". ").append(label)
                  .append("\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private List<String> sanitizeAll(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(s -> s.replaceAll("[^a-zA-Z0-9_]", "_").trim())
                .filter(s -> !s.isBlank())
                .toList();
    }
}
