package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates PlantUML state diagram syntax from parsed input.
 *
 * <p>Uses {@code [*]} as the start marker. Entities become states;
 * actions become transition labels. Format:</p>
 * <pre>
 * {@code
 * @startuml
 * [*] --> State1
 * State1 --> State2 : action
 * State2 --> [*]
 * @enduml
 * }
 * </pre>
 */
@Component
public class StateDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.STATE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> states = resolveStates(parsedInput);
        List<String> actions = sanitizeAll(parsedInput.getActions());

        StringBuilder sb = new StringBuilder("@startuml\n");

        if (states.isEmpty()) {
            sb.append("[*] --> Idle\n");
            sb.append("Idle --> Processing : start\n");
            sb.append("Processing --> Completed : finish\n");
            sb.append("Processing --> Failed : error\n");
            sb.append("Completed --> [*]\n");
            sb.append("Failed --> [*]\n");
        } else {
            // [*] --> first state
            sb.append("[*] --> ").append(states.get(0)).append("\n");

            // chain states with action labels
            for (int i = 0; i < states.size() - 1; i++) {
                String label = (i < actions.size()) ? actions.get(i) : "transition";
                sb.append(states.get(i))
                  .append(" --> ")
                  .append(states.get(i + 1))
                  .append(" : ")
                  .append(label)
                  .append("\n");
            }

            // last state --> [*]
            sb.append(states.get(states.size() - 1)).append(" --> [*]\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Resolves ordered states from entities; falls back to empty list.
     */
    private List<String> resolveStates(ParsedInput parsedInput) {
        List<String> entities = parsedInput.getEntities();
        if (entities != null && !entities.isEmpty()) {
            return sanitizeAll(entities);
        }
        return List.of();
    }

    private List<String> sanitizeAll(List<String> items) {
        if (items == null) return List.of();
        List<String> result = new ArrayList<>(items.size());
        for (String item : items) {
            // State names must be valid PlantUML identifiers (no spaces, no semicolons)
            String sanitized = item.replaceAll("[^a-zA-Z0-9_]", "_").trim();
            if (!sanitized.isBlank()) {
                result.add(sanitized);
            }
        }
        return result;
    }
}
