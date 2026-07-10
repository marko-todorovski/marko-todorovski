package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.StateDiagramGeneratorService;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates PlantUML state diagram syntax by delegating to
 * {@link StateDiagramGeneratorService}.
 *
 * <p>The raw input text is preferred for rich NLP parsing (transitions,
 * entry/do/exit actions, lifecycle phrases). When absent, a synthetic
 * description is assembled from the parsed entity and action lists so the
 * service can still produce a meaningful diagram.
 *
 * <p>Example output:</p>
 * <pre>
 * {@code
 * @startuml
 * [*] --> Off
 * Off --> On : switch on
 * On --> Off : switch off
 * On --> [*]
 * @enduml
 * }
 * </pre>
 */
@Component
public class StateDiagramGenerator implements DiagramGenerator {

    private final StateDiagramGeneratorService generatorService;

    public StateDiagramGenerator(StateDiagramGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public DiagramType supports() {
        return DiagramType.STATE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        String raw = parsedInput.getRawContent();

        // Prefer raw text for full structural/NLP parsing
        if (raw != null && !raw.isBlank()) {
            return generatorService.generateStateDiagram(raw);
        }

        // Fall back to synthetic description built from entity/action lists
        return generatorService.generateStateDiagram(synthesize(parsedInput));
    }

    /**
     * Builds a plain-text description from the parsed entity and action lists.
     * Entities are joined as transition targets; actions become transition labels.
     */
    private String synthesize(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        List<String> actions  = parsedInput.getActions();

        if (entities != null && !entities.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entities.size() - 1; i++) {
                String label = (actions != null && i < actions.size())
                        ? actions.get(i) : "transition";
                sb.append(entities.get(i))
                  .append(" transitions to ")
                  .append(entities.get(i + 1))
                  .append(" on ").append(label).append(". ");
            }
            return sb.toString().strip();
        }

        if (actions != null && !actions.isEmpty()) {
            return String.join(". ", actions);
        }

        return "";
    }
}
