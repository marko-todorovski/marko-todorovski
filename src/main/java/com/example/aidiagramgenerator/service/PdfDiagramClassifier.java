package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pre-classifier for PDF documentation files.
 *
 * <p>Inspects the extracted PDF text for diagram-type-specific title phrases
 * and domain keywords. Returns the matched {@link DiagramType} (confidence 100),
 * or {@code null} if no rule fires, in which case the normal classifier takes over.
 *
 * <p>Priority order (more specific types are checked first to prevent overlapping
 * keywords causing misclassification):
 * <ol>
 *   <li>ACTIVITY  — before USE_CASE</li>
 *   <li>STATE     — before SEQUENCE</li>
 *   <li>OBJECT    — before CLASS</li>
 *   <li>COLLABORATION — before USE_CASE and SEQUENCE</li>
 *   <li>MICROSERVICES — before COMPONENT</li>
 *   <li>COMPONENT</li>
 *   <li>DEPLOYMENT</li>
 *   <li>ER</li>
 *   <li>SEQUENCE</li>
 *   <li>USE_CASE</li>
 *   <li>CLASS</li>
 * </ol>
 */
@Component
public class PdfDiagramClassifier {

    private static final Logger logger = LoggerFactory.getLogger(PdfDiagramClassifier.class);

    /**
     * Analyses extracted PDF text and returns the matching {@link DiagramType}
     * if a PDF-specific rule fires, or {@code null} if no rule matched.
     *
     * <p>When a rule matches, the result carries:
     * <ul>
     *   <li>confidence = 100</li>
     *   <li>reasoning = "Detected from PDF documentation title and diagram-specific keywords"</li>
     * </ul>
     *
     * @param extractedText text extracted from the uploaded PDF
     * @return the matched {@link DiagramType}, or {@code null} if no rule matched
     */
    public DiagramType detect(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return null;
        }

        String preview = extractedText.length() > 500
                ? extractedText.substring(0, 500)
                : extractedText;
        logger.info("[PdfClassifier] Extracted PDF text (first 500 chars): {}", preview);

        String lower = extractedText.toLowerCase();

        // 1. ACTIVITY — checked before USE_CASE
        if (containsAny(lower,
                "activity diagram documentation",
                "activity diagram",
                "workflow",
                "activities",
                "decision points")) {
            return matched(DiagramType.ACTIVITY, "ACTIVITY");
        }

        // 2. STATE — checked before SEQUENCE
        if (containsAny(lower,
                "state diagram documentation",
                "state diagram",
                "states",
                "transitions",
                "state behavior",
                "events")) {
            return matched(DiagramType.STATE, "STATE");
        }

        // 3. OBJECT — checked before CLASS
        if (containsAny(lower,
                "object diagram documentation",
                "object diagram",
                "object instances",
                "attribute values",
                "object snapshot")) {
            return matched(DiagramType.OBJECT, "OBJECT");
        }

        // 4. COLLABORATION — checked before USE_CASE and SEQUENCE
        if (containsAny(lower,
                "collaboration diagram documentation",
                "collaboration diagram",
                "message number",
                "object communication",
                "collaboration logic")) {
            return matched(DiagramType.COLLABORATION, "COLLABORATION");
        }

        // 5. MICROSERVICES — checked before COMPONENT
        if (containsAny(lower,
                "microservices architecture documentation",
                "microservices architecture",
                "api gateway",
                "service registry",
                "message broker",
                "independent services")) {
            return matched(DiagramType.MICROSERVICES, "MICROSERVICES");
        }

        // 6. COMPONENT
        if (containsAny(lower,
                "component diagram documentation",
                "component diagram",
                "components",
                "interfaces")) {
            return matched(DiagramType.COMPONENT, "COMPONENT");
        }

        // 7. DEPLOYMENT
        if (containsAny(lower,
                "deployment diagram documentation",
                "deployment diagram",
                "nodes",
                "deployed artifacts")) {
            return matched(DiagramType.DEPLOYMENT, "DEPLOYMENT");
        }

        // 8. ER
        if (containsAny(lower,
                "er diagram documentation",
                "entity-relationship",
                "entities",
                "cardinality",
                "primary key",
                "foreign key")) {
            return matched(DiagramType.ER, "ER");
        }

        // 9. SEQUENCE
        if (containsAny(lower,
                "sequence diagram documentation",
                "sequence diagram",
                "participants",
                "messages",
                "interaction flow")) {
            return matched(DiagramType.SEQUENCE, "SEQUENCE");
        }

        // 10. USE_CASE
        if (containsAny(lower,
                "use case diagram documentation",
                "use case diagram",
                "actors",
                "use cases",
                "system boundary")) {
            return matched(DiagramType.USE_CASE, "USE_CASE");
        }

        // 11. CLASS
        if (containsAny(lower,
                "class diagram documentation",
                "class diagram",
                "classes",
                "attributes and methods")) {
            return matched(DiagramType.CLASS, "CLASS");
        }

        logger.info("[PdfClassifier] No PDF-specific rule matched — delegating to normal classifier");
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DiagramType matched(DiagramType type, String ruleName) {
        logger.info("[PdfClassifier] Matched rule: {} → diagramType={}, confidence=100, " +
                "reasoning=Detected from PDF documentation title and diagram-specific keywords",
                ruleName, type);
        return type;
    }

    private boolean containsAny(String lower, String... keywords) {
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
