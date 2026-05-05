package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates Mermaid sequence-diagram syntax from parsed input.
 */
@Component
public class SequenceDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.SEQUENCE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        List<String> relationships = parsedInput.getRelationships();

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");

        if (entities.isEmpty()) {
            sb.append("    participant User\n")
              .append("    participant AuthService\n")
              .append("    participant Database\n")
              .append("    User->>AuthService: login(credentials)\n")
              .append("    AuthService->>Database: findUser(username)\n")
              .append("    Database-->>AuthService: userData\n")
              .append("    AuthService-->>User: authToken\n");
        } else {
            for (String entity : entities) {
                sb.append("    participant ").append(entity).append("\n");
            }
            if (relationships.isEmpty() && entities.size() >= 2) {
                for (int i = 0; i < entities.size() - 1; i++) {
                    sb.append("    ").append(entities.get(i))
                      .append("->>").append(entities.get(i + 1))
                      .append(": request\n");
                    sb.append("    ").append(entities.get(i + 1))
                      .append("-->>").append(entities.get(i))
                      .append(": response\n");
                }
            } else {
                for (String rel : relationships) {
                    String[] parts = rel.split("\\s*->\\s*");
                    if (parts.length == 2) {
                        sb.append("    ").append(parts[0]).append("->>").append(parts[1]).append(": call\n");
                    }
                }
            }
        }

        return sb.toString().stripTrailing();
    }
}
