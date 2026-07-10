package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.ObjectDiagramGeneratorService;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates PlantUML object diagram syntax by delegating to
 * {@link ObjectDiagramGeneratorService}.
 *
 * <p>When the raw input text does not yield any typed instances but the caller
 * has provided entity hints (via {@link ParsedInput#getEntities()}), the generator
 * synthesises typed instance notation from the entity names before passing the
 * text to the service — preserving the fallback behaviour expected by callers
 * that supply entity lists rather than raw typed text.</p>
 *
 * <p>Example output:</p>
 * <pre>
 * {@code
 * @startuml
 * object "dogD : Dog" as dogD {
 *   name = "Wolfy"
 *   pedigree = true
 * }
 * object "owner1 : Person" as owner1 {
 *   name = "Alice"
 * }
 * dogD --> owner1 : ownedBy
 * @enduml
 * }
 * </pre>
 */
@Component
public class ObjectDiagramGenerator implements DiagramGenerator {

    private final ObjectDiagramGeneratorService generatorService;

    public ObjectDiagramGenerator(ObjectDiagramGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public DiagramType supports() {
        return DiagramType.OBJECT;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        String raw    = parsedInput.getRawContent();
        String result = generatorService.generateObjectDiagram(raw);

        // If the service fell back to the default diagram AND entity hints are
        // available, synthesise typed notation from the entity list.
        if (isDefaultDiagram(result) && hasEntityHints(parsedInput)) {
            result = generatorService.generateObjectDiagram(
                    synthesizeFromEntities(parsedInput.getEntities()));
        }

        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Detects that the service produced its built-in default diagram. */
    private boolean isDefaultDiagram(String uml) {
        return uml != null && uml.contains("dogD : Dog");
    }

    private boolean hasEntityHints(ParsedInput input) {
        return input.getEntities() != null && !input.getEntities().isEmpty();
    }

    /**
     * Converts a list of class names such as {@code ["Dog", "Person"]} into
     * typed instance notation: {@code "dog1 : Dog\nperson1 : Person\n"}.
     */
    private String synthesizeFromEntities(List<String> entities) {
        StringBuilder sb = new StringBuilder();
        for (String entity : entities) {
            if (entity == null || entity.isBlank()) continue;
            String name    = entity.trim();
            String varName = Character.toLowerCase(name.charAt(0)) + name.substring(1) + "1";
            sb.append(varName).append(" : ").append(name).append("\n");
        }
        return sb.toString();
    }
}
