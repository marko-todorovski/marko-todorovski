package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;

/**
 * High-level contract for converting raw input into Mermaid diagram code.
 *
 * <p>This is the <b>primary LLM swap point</b>. The default implementation
 * ({@code RuleBasedMermaidCodeGenerator}) orchestrates parsing → classification →
 * generation using strategy beans. To replace the entire pipeline with an LLM call,
 * provide a new implementation annotated with {@code @Primary}.</p>
 *
 * <p><b>Usage in controller / service layer:</b></p>
 * <pre>{@code
 *   String mermaid = mermaidCodeGenerator.generate("Describe a login flow", InputType.TEXT, null);
 * }</pre>
 */
public interface MermaidCodeGenerator {

    /**
     * Generate Mermaid diagram code from raw input.
     *
     * @param rawContent      the raw input (text, XML string, URL, etc.)
     * @param inputType       how the input was provided
     * @param diagramTypeHint optional hint for the desired diagram type; may be {@code null}
     * @return valid Mermaid diagram code
     */
    String generate(String rawContent, InputType inputType, DiagramType diagramTypeHint);
}
