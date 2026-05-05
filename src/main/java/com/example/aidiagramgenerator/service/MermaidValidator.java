package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.DiagramGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates Mermaid diagram syntax.
 *
 * <p>Performs basic syntax checks to ensure generated Mermaid code
 * starts with a valid diagram type declaration.</p>
 */
@Component
public class MermaidValidator {

    private static final Logger logger = LoggerFactory.getLogger(MermaidValidator.class);

    /**
     * Valid Mermaid diagram type prefixes.
     */
    private static final Set<String> VALID_DIAGRAM_PREFIXES = Set.of(
            "classDiagram",
            "sequenceDiagram",
            "erDiagram",
            "C4Context",
            "C4Container",
            "C4Component",
            "C4Dynamic",
            "C4Deployment",
            "graph",
            "flowchart",
            "stateDiagram",
            "stateDiagram-v2",
            "gantt",
            "pie",
            "gitGraph",
            "mindmap",
            "timeline",
            "journey"
    );

    /**
     * Pattern to match the first word/identifier in the Mermaid code.
     */
    private static final Pattern FIRST_TOKEN_PATTERN = Pattern.compile("^\\s*(\\S+)");

    /**
     * Validates that the given Mermaid code has valid syntax.
     *
     * @param mermaidCode the Mermaid diagram code to validate
     * @throws DiagramGenerationException if the syntax is invalid
     */
    public void validate(String mermaidCode) {
        if (mermaidCode == null || mermaidCode.isBlank()) {
            throw new DiagramGenerationException("Generated Mermaid code is empty");
        }

        String trimmed = mermaidCode.trim();
        String firstToken = extractFirstToken(trimmed);

        if (firstToken == null) {
            throw new DiagramGenerationException("Generated Mermaid code is empty or contains only whitespace");
        }

        // Check if the first token matches a valid diagram type
        boolean isValid = VALID_DIAGRAM_PREFIXES.stream()
                .anyMatch(prefix -> firstToken.equals(prefix) || firstToken.startsWith(prefix));

        if (!isValid) {
            logger.error("Invalid Mermaid syntax. Code starts with '{}', expected one of: {}",
                    firstToken, VALID_DIAGRAM_PREFIXES);
            throw new DiagramGenerationException(
                    String.format("Invalid Mermaid syntax: diagram must start with a valid type declaration " +
                            "(e.g., classDiagram, sequenceDiagram, erDiagram, graph). Found: '%s'", firstToken));
        }

        // Additional validation: check for basic structure
        validateBasicStructure(trimmed, firstToken);

        logger.debug("Mermaid code validation passed for diagram type: {}", firstToken);
    }

    /**
     * Extracts the first token (word) from the Mermaid code.
     */
    private String extractFirstToken(String code) {
        var matcher = FIRST_TOKEN_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Performs additional structural validation based on diagram type.
     */
    private void validateBasicStructure(String code, String diagramType) {
        // Check that the code has some content beyond just the type declaration
        String[] lines = code.split("\n");
        if (lines.length < 1) {
            throw new DiagramGenerationException(
                    "Invalid Mermaid syntax: diagram contains no content after type declaration");
        }

        // For sequence diagrams, check for participant or arrow syntax
        if (diagramType.equals("sequenceDiagram")) {
            if (!code.contains("participant") && !code.contains("->>") && !code.contains("-->>")) {
                logger.warn("Sequence diagram may be incomplete: no participants or interactions found");
            }
        }

        // For class diagrams, check for class definitions or relationships
        if (diagramType.equals("classDiagram")) {
            if (!code.contains("class ") && !code.contains("-->") && !code.contains("--")) {
                logger.warn("Class diagram may be incomplete: no classes or relationships found");
            }
        }

        // For ER diagrams, check for entity definitions
        if (diagramType.equals("erDiagram")) {
            if (!code.contains("{") && !code.contains("||") && !code.contains("}|")) {
                logger.warn("ER diagram may be incomplete: no entities or relationships found");
            }
        }
    }

    /**
     * Checks if the given Mermaid code is valid without throwing an exception.
     *
     * @param mermaidCode the Mermaid diagram code to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(String mermaidCode) {
        try {
            validate(mermaidCode);
            return true;
        } catch (DiagramGenerationException e) {
            return false;
        }
    }
}
