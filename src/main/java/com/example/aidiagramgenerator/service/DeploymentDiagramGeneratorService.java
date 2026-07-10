package com.example.aidiagramgenerator.service;

/**
 * Service for generating PlantUML deployment diagrams from natural language or
 * semi-structured text.
 *
 * <p>Supports academically correct UML deployment diagram notation:
 * <ul>
 *   <li>Nodes: {@code node "AppServer"}</li>
 *   <li>Nested artifacts: {@code node "AppServer" { artifact "App.war" }}</li>
 *   <li>Databases: {@code database "MySQL"}</li>
 *   <li>Artifacts: {@code artifact "config.xml"}</li>
 *   <li>Communication paths: {@code "Client" --> "Server" : HTTP}</li>
 * </ul>
 *
 * <p>All output starts with {@code @startuml} and ends with {@code @enduml}.
 */
public interface DeploymentDiagramGeneratorService {

    /**
     * Generate a PlantUML deployment diagram from the given text.
     *
     * @param text natural language description or semi-structured notation
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateDeploymentDiagram(String text);
}
