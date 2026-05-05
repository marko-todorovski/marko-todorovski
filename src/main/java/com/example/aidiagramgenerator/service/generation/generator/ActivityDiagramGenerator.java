package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates PlantUML activity diagram syntax from parsed input.
 *
 * <p>Uses {@code start} / {@code stop} markers with actions connected by {@code -->}.
 * Actions are sourced from the parsed actions list first; if empty, entities are used as steps.</p>
 *
 * <p>Output format:</p>
 * <pre>
 * {@code
 * @startuml
 * start
 * :Step one;
 * :Step two;
 * --> :Step three;
 * stop
 * @enduml
 * }
 * </pre>
 */
@Component
public class ActivityDiagramGenerator implements DiagramGenerator {

    @Override
    public DiagramType supports() {
        return DiagramType.ACTIVITY;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> steps = resolveSteps(parsedInput);

        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append("start\n");

        if (steps.isEmpty()) {
            sb.append(":Initialize;\n");
            sb.append("--> :Process request;\n");
            sb.append("--> :Validate input;\n");
            sb.append("--> :Execute operation;\n");
            sb.append("--> :Return result;\n");
        } else {
            sb.append(":").append(capitalize(steps.get(0))).append(";\n");
            for (int i = 1; i < steps.size(); i++) {
                sb.append("--> :").append(capitalize(steps.get(i))).append(";\n");
            }
        }

        sb.append("stop\n");
        sb.append("@enduml");

        return sb.toString();
    }

    /**
     * Resolves the ordered list of steps.
     * Prefers explicit actions; falls back to entities.
     */
    private List<String> resolveSteps(ParsedInput parsedInput) {
        List<String> actions = parsedInput.getActions();
        if (actions != null && !actions.isEmpty()) {
            return sanitizeAll(actions);
        }
        List<String> entities = parsedInput.getEntities();
        if (entities != null && !entities.isEmpty()) {
            return sanitizeAll(entities);
        }
        return List.of();
    }

    private List<String> sanitizeAll(List<String> items) {
        List<String> result = new ArrayList<>(items.size());
        for (String item : items) {
            String sanitized = item.replaceAll("[;]", "").trim();
            if (!sanitized.isBlank()) {
                result.add(sanitized);
            }
        }
        return result;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
