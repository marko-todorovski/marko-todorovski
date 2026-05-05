package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates Mermaid architecture / graph-TD diagram syntax from parsed input.
 */
@Component
public class ArchitectureDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.ARCHITECTURE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();

        StringBuilder sb = new StringBuilder("graph TD\n");

        if (entities.isEmpty()) {
            sb.append("    A[User Interface] --> B[API Gateway]\n")
              .append("    B --> C[Auth Service]\n")
              .append("    B --> D[Business Logic]\n")
              .append("    C --> E[Database]\n")
              .append("    D --> E\n");
        } else {
            char label = 'A';
            for (int i = 0; i < entities.size() && label <= 'Z'; i++) {
                if (i < entities.size() - 1) {
                    sb.append("    ").append(label).append("[").append(entities.get(i)).append("]")
                      .append(" --> ").append((char) (label + 1))
                      .append("[").append(entities.get(i + 1)).append("]\n");
                }
                label++;
            }
            // If only one entity, generate a simple node
            if (entities.size() == 1) {
                sb.append("    A[").append(entities.getFirst()).append("]\n");
            }
        }

        return sb.toString().stripTrailing();
    }
}
