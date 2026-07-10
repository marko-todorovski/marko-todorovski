package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.DeploymentDiagramGeneratorService;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

/**
 * Generates PlantUML deployment diagram syntax by delegating to
 * {@link DeploymentDiagramGeneratorService}.
 *
 * <p>Example output:</p>
 * <pre>
 * {@code
 * @startuml
 *
 * node "AppServer" {
 *   artifact "App.war"
 * }
 * database "MySQL"
 *
 * "AppServer" --> "MySQL" : JDBC
 * @enduml
 * }
 * </pre>
 */
@Component
public class DeploymentDiagramGenerator implements DiagramGenerator {

    private final DeploymentDiagramGeneratorService generatorService;

    public DeploymentDiagramGenerator(DeploymentDiagramGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public DiagramType supports() {
        return DiagramType.DEPLOYMENT;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        return generatorService.generateDeploymentDiagram(parsedInput.getRawContent());
    }
}
