package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.ActivityDiagramGeneratorService;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates PlantUML activity diagram syntax by delegating to
 * {@link ActivityDiagramGeneratorService}.
 *
 * <p>The raw input text is preferred for rich structured parsing (decisions,
 * loops, fork/join, swimlanes). When the raw content is absent, a synthetic
 * text is assembled from the parsed actions or entity list so the service
 * can still produce a meaningful sequential diagram.
 *
 * <p>Example output:</p>
 * <pre>
 * {@code
 * @startuml
 * start
 * :Put clothes on;
 * :Drive to college;
 * stop
 * @enduml
 * }
 * </pre>
 */
@Component
public class ActivityDiagramGenerator implements DiagramGenerator {

    private final ActivityDiagramGeneratorService generatorService;

    public ActivityDiagramGenerator(ActivityDiagramGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public DiagramType supports() {
        return DiagramType.ACTIVITY;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        String raw = parsedInput.getRawContent();

        // Prefer raw text for full structural parsing
        if (raw != null && !raw.isBlank()) {
            return generatorService.generateActivityDiagram(raw);
        }

        // Fall back to synthesised text from parsed lists
        String synthetic = synthesize(parsedInput);
        return generatorService.generateActivityDiagram(synthetic);
    }

    /**
     * Builds a plain-text description from the parsed actions or entities
     * so the service can apply its full parsing pipeline.
     */
    private String synthesize(ParsedInput parsedInput) {
        List<String> actions = parsedInput.getActions();
        if (actions != null && !actions.isEmpty()) {
            return String.join(". ", actions);
        }
        List<String> entities = parsedInput.getEntities();
        if (entities != null && !entities.isEmpty()) {
            return String.join(". ", entities);
        }
        return "";
    }
}
