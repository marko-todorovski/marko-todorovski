package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates a Mermaid-compatible C4-style diagram from parsed input.
 *
 * <p>Mermaid supports C4 diagrams via the {@code C4Context} / {@code C4Container} blocks
 * (since Mermaid v10.x). This generator produces C4 Context-level diagrams.</p>
 */
@Component
public class C4DiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.C4;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        String title = parsedInput.getMetadata().getOrDefault("title", "System Context Diagram");

        StringBuilder sb = new StringBuilder("C4Context\n");
        sb.append("    title ").append(title).append("\n\n");

        if (entities.isEmpty()) {
            sb.append("    Person(user, \"User\", \"A user of the system\")\n")
              .append("    System(system, \"System\", \"The main application\")\n")
              .append("    System_Ext(extApi, \"External API\", \"Third-party service\")\n")
              .append("    SystemDb(db, \"Database\", \"Stores data\")\n\n")
              .append("    Rel(user, system, \"Uses\")\n")
              .append("    Rel(system, extApi, \"Calls\")\n")
              .append("    Rel(system, db, \"Reads/Writes\")\n");
        } else {
            // First entity is a Person, rest are Systems
            sb.append("    Person(actor, \"").append(entities.getFirst()).append("\", \"Primary actor\")\n");
            for (int i = 1; i < entities.size(); i++) {
                String alias = "sys" + i;
                sb.append("    System(").append(alias).append(", \"")
                  .append(entities.get(i)).append("\", \"Component\")\n");
            }
            sb.append("\n");
            // Relationships
            if (entities.size() >= 2) {
                sb.append("    Rel(actor, sys1, \"Uses\")\n");
                for (int i = 1; i < entities.size() - 1; i++) {
                    sb.append("    Rel(sys").append(i).append(", sys").append(i + 1).append(", \"Calls\")\n");
                }
            }
        }

        return sb.toString().stripTrailing();
    }
}
