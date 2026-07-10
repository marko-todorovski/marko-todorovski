package com.example.aidiagramgenerator.service;

/**
 * Service for generating PlantUML activity diagrams from natural language or
 * semi-structured text.
 *
 * <p>Supports the following UML activity diagram constructs:
 * <ul>
 *   <li><b>Sequential actions:</b> {@code :Action text;}</li>
 *   <li><b>Decisions:</b> {@code if (condition?) then (yes) ... else (no) ... endif}</li>
 *   <li><b>While loops:</b> {@code while (condition?) is (yes) ... endwhile}</li>
 *   <li><b>Repeat-until loops:</b> {@code repeat ... repeat while (condition?)}</li>
 *   <li><b>Fork/join (parallel):</b> {@code fork ... fork again ... end fork}</li>
 *   <li><b>Swimlanes:</b> {@code |Actor| ... |AnotherActor|}</li>
 * </ul>
 *
 * <p>All output starts with {@code @startuml} and ends with {@code @enduml}.
 */
public interface ActivityDiagramGeneratorService {

    /**
     * Generate a PlantUML activity diagram from the given text.
     *
     * @param text natural language description or semi-structured notation
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateActivityDiagram(String text);
}
