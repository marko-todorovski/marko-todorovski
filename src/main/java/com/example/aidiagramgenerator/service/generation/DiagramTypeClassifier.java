package com.example.aidiagramgenerator.service.generation;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;

/**
 * Strategy interface for classifying parsed input into a {@link DiagramType}.
 *
 * <p><b>LLM swap point:</b> The default implementation uses keyword-based heuristics.
 * Replace it with an LLM-powered classifier by providing an alternative bean
 * annotated with {@code @Primary}.</p>
 */
public interface DiagramTypeClassifier {

    /**
     * Classify the parsed input into the most suitable diagram type.
     *
     * @param parsedInput the structured input
     * @return the inferred {@link DiagramType}
     */
    DiagramType classify(ParsedInput parsedInput);
}
