package com.example.aidiagramgenerator.service.generation.classifier;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.DiagramTypeClassifier;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Keyword-based heuristic classifier.
 *
 * <p><b>LLM swap point:</b> To replace with an LLM classifier, create a new
 * {@link DiagramTypeClassifier} bean annotated {@code @Primary}.</p>
 */
@Component
public class KeywordBasedDiagramTypeClassifier implements DiagramTypeClassifier {

    private static final Logger logger = LoggerFactory.getLogger(KeywordBasedDiagramTypeClassifier.class);

    /** Keyword → DiagramType mapping, checked in priority order. */
    private static final Map<DiagramType, List<String>> TYPE_KEYWORDS = Map.of(
            DiagramType.C4,           List.of("c4", "container", "deployment", "context diagram"),
            DiagramType.SEQUENCE,     List.of("sequence", "call", "message"),
            DiagramType.ER,           List.of("database", "table", "entity", "relationship", "schema", "column"),
            DiagramType.USE_CASE,     List.of("use case", "actor", "actors", "stakeholder", "system boundary",
                                              "<<include>>", "<<extend>>", "include", "extend"),
            DiagramType.ARCHITECTURE, List.of("architecture", "system", "component", "service"),
            DiagramType.ACTIVITY,     List.of("activity", "workflow", "flow", "process", "step", "task", "action",
                                              "procedure", "pipeline", "steps"),
            DiagramType.STATE,        List.of("state", "transition", "lifecycle", "status", "event",
                                              "trigger", "guard", "statechart", "finite"),
            DiagramType.OBJECT,          List.of("object", "instance", "snapshot", "object diagram", "concrete",
                                                 "instantiate", "instance of", "instantiated object",
                                                 "values assigned", "associated objects"),
            DiagramType.MICROSERVICES,    List.of("microservices", "microservice", "distributed system",
                                                 "api gateway", "service mesh", "service registry", "gateway"),
            DiagramType.COLLABORATION,    List.of("collaboration diagram", "communication diagram",
                                                 "objects connected", "numbered messages", "object interaction")
    );

    @Override
    public DiagramType classify(ParsedInput parsedInput) {
        // If a hint was set explicitly, honour it
        if (parsedInput.getDiagramTypeHint() != null) {
            logger.debug("Using explicit diagram type hint: {}", parsedInput.getDiagramTypeHint());
            return parsedInput.getDiagramTypeHint();
        }

        // URL inputs default to architecture diagrams
        if (parsedInput.getInputType() == InputType.URL) {
            logger.debug("URL input — defaulting to ARCHITECTURE");
            return DiagramType.ARCHITECTURE;
        }

        // Score each type by keyword matches
        List<String> keywords = parsedInput.getKeywords();
        String rawLower = parsedInput.getRawContent().toLowerCase();

        DiagramType bestMatch = DiagramType.CLASS; // default
        int bestScore = 0;

        for (var entry : TYPE_KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (keywords.contains(kw) || rawLower.contains(kw)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }

        logger.debug("Classified input as {} (score={})", bestMatch, bestScore);
        return bestMatch;
    }
}
