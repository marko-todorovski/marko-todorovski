package com.example.aidiagramgenerator.service;

/**
 * Service for generating PlantUML object diagrams from natural language or
 * semi-structured text.
 *
 * <p>Supports academically correct UML object diagram notation:
 * <ul>
 *   <li>Named instances with typed labels: {@code "varName : ClassName"}</li>
 *   <li>Attribute value slots: {@code attr = value}</li>
 *   <li>Object links with optional role labels: {@code a --> b : role}</li>
 *   <li>Multiple natural language instance-declaration patterns</li>
 * </ul>
 */
public interface ObjectDiagramGeneratorService {

    /**
     * Generate a PlantUML object diagram from the given text.
     *
     * @param text natural language description, explicit typed notation, or a mix of both
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateObjectDiagram(String text);
}
