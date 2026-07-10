package com.example.aidiagramgenerator.service;

/**
 * Service for generating PlantUML state diagrams from natural language or
 * semi-structured text.
 *
 * <p>Supports academically correct UML state diagram notation:
 * <ul>
 *   <li>Initial pseudo-state: {@code [*] --> FirstState}</li>
 *   <li>Final pseudo-state: {@code LastState --> [*]}</li>
 *   <li>Named states with optional stereotypes</li>
 *   <li>Transitions with labels: {@code State1 --> State2 : event / action [guard]}</li>
 *   <li>Entry, do, and exit actions: {@code entry / action}, {@code do / action},
 *       {@code exit / action}</li>
 * </ul>
 *
 * <p>All output starts with {@code @startuml} and ends with {@code @enduml}.
 */
public interface StateDiagramGeneratorService {

    /**
     * Generate a PlantUML state diagram from the given text.
     *
     * @param text natural language description or semi-structured notation
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateStateDiagram(String text);
}
