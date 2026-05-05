package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;

/**
 * Strategy interface for generating Mermaid diagram code for a specific {@link DiagramType}.
 *
 * <p>Each diagram type (CLASS, SEQUENCE, ER, BPMN, C4, …) has its own generator that
 * knows how to produce the right Mermaid syntax.</p>
 *
 * <p><b>Extension point:</b> To add a new diagram type, create an enum value in
 * {@link DiagramType}, implement this interface, and register the bean. The
 * {@link DiagramGeneratorRegistry} will auto-discover it.</p>
 */
public interface DiagramGenerator {

    /**
     * @return the {@link DiagramType} this generator produces.
     */
    DiagramType supports();

    /**
     * Generate Mermaid diagram code from parsed input.
     *
     * @param parsedInput the structured, parsed input
     * @return valid Mermaid diagram code
     */
    String generate(ParsedInput parsedInput);
}
