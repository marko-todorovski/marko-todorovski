package com.example.aidiagramgenerator.service;

/**
 * Service for generating PlantUML component diagrams from natural language or
 * semi-structured text.
 *
 * <p>Supports academically correct UML component diagram notation:
 * <ul>
 *   <li>Components: {@code component "Web Browser"}</li>
 *   <li>Databases: {@code database "MySQL"}</li>
 *   <li>Interfaces: {@code interface "ILogin"}</li>
 *   <li>Dependencies: {@code "Web Browser" --> "Sales Software" : SSL}</li>
 *   <li>Usage relationships: {@code "Client" ..> "IService" : use}</li>
 * </ul>
 *
 * <p>All output starts with {@code @startuml} and ends with {@code @enduml}.
 */
public interface ComponentDiagramGeneratorService {

    /**
     * Generate a PlantUML component diagram from the given text.
     *
     * @param text natural language description or semi-structured notation
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateComponentDiagram(String text);
}
