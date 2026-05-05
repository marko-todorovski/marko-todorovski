package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates Mermaid class-diagram syntax from parsed input.
 */
@Component
public class ClassDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.CLASS;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        List<String> relationships = parsedInput.getRelationships();

        StringBuilder sb = new StringBuilder("classDiagram\n");

        if (entities.isEmpty()) {
            // Fallback mock
            sb.append("    class User {\n")
              .append("        +String username\n")
              .append("        +String email\n")
              .append("        +login()\n")
              .append("        +logout()\n")
              .append("    }\n")
              .append("    class AuthService {\n")
              .append("        +authenticate()\n")
              .append("        +validateToken()\n")
              .append("    }\n")
              .append("    class Database {\n")
              .append("        +query()\n")
              .append("        +save()\n")
              .append("    }\n")
              .append("    User --> AuthService\n")
              .append("    AuthService --> Database\n");
        } else {
            for (String entity : entities) {
                sb.append("    class ").append(entity).append(" {\n")
                  .append("        +String id\n")
                  .append("        +process()\n")
                  .append("    }\n");
            }
            if (relationships.isEmpty() && entities.size() >= 2) {
                for (int i = 0; i < entities.size() - 1; i++) {
                    sb.append("    ").append(entities.get(i))
                      .append(" --> ").append(entities.get(i + 1)).append("\n");
                }
            } else {
                for (String rel : relationships) {
                    sb.append("    ").append(rel.replace("->", "-->")).append("\n");
                }
            }
        }

        return sb.toString().stripTrailing();
    }
}
