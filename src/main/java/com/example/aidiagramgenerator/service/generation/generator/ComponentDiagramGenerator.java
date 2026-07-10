package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.ComponentDiagramGeneratorService;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

/**
 * Generates PlantUML component diagram syntax by delegating to
 * {@link ComponentDiagramGeneratorService}.
 *
 * <p>Example output:</p>
 * <pre>
 * {@code
 * @startuml
 *
 * component "Web Browser"
 * component "Sales Software"
 * database "MySQL"
 *
 * "Web Browser" --> "Sales Software" : SSL
 * "Sales Software" --> "MySQL" : JDBC
 * @enduml
 * }
 * </pre>
 */
@Component
public class ComponentDiagramGenerator implements DiagramGenerator {

    private final ComponentDiagramGeneratorService generatorService;

    public ComponentDiagramGenerator(ComponentDiagramGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public DiagramType supports() {
        return DiagramType.COMPONENT;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        return generatorService.generateComponentDiagram(parsedInput.getRawContent());
    }
}
