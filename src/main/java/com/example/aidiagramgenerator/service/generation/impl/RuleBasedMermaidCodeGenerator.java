package com.example.aidiagramgenerator.service.generation.impl;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.*;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rule-based implementation that orchestrates: input parsing → type classification → diagram generation.
 *
 * <p><b>LLM swap point:</b> To replace the entire pipeline with an LLM call, create a new
 * {@link MermaidCodeGenerator} implementation annotated {@code @Primary}. Example:</p>
 * <pre>{@code
 * @Primary
 * @Component
 * public class LlmMermaidCodeGenerator implements MermaidCodeGenerator {
 *     public String generate(String raw, InputType src, DiagramType hint) {
 *         return llmClient.generate("Convert to Mermaid: " + raw);
 *     }
 * }
 * }</pre>
 */
@Component
public class RuleBasedMermaidCodeGenerator implements MermaidCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(RuleBasedMermaidCodeGenerator.class);

    private final InputParserRegistry parserRegistry;
    private final DiagramTypeClassifier classifier;
    private final DiagramGeneratorRegistry generatorRegistry;

    public RuleBasedMermaidCodeGenerator(InputParserRegistry parserRegistry,
                                         DiagramTypeClassifier classifier,
                                         DiagramGeneratorRegistry generatorRegistry) {
        this.parserRegistry = parserRegistry;
        this.classifier = classifier;
        this.generatorRegistry = generatorRegistry;
    }

    @Override
    public String generate(String rawContent, InputType inputType, DiagramType diagramTypeHint) {
        logger.info("Generating Mermaid code: type={}, hint={}", inputType, diagramTypeHint);

        // 1. Parse raw input into structured form
        InputParser parser = parserRegistry.getParser(inputType);
        ParsedInput parsedInput = parser.parse(rawContent);

        // Apply explicit hint if provided
        if (diagramTypeHint != null) {
            parsedInput.setDiagramTypeHint(diagramTypeHint);
        }

        // 2. Classify the diagram type
        DiagramType diagramType = classifier.classify(parsedInput);
        logger.info("Classified diagram type: {}", diagramType);

        // 3. Generate Mermaid code
        DiagramGenerator generator = generatorRegistry.getGenerator(diagramType);
        String mermaidCode = generator.generate(parsedInput);

        logger.debug("Generated Mermaid code ({} chars)", mermaidCode.length());
        return mermaidCode;
    }
}
