package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramSuggestion.ClassificationSource;
import com.example.aidiagramgenerator.domain.DiagramType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implementation of {@link DiagramSuggestionService} that evaluates input text
 * using a layered strategy and computes confidence scores.
 *
 * <p>Confidence score ranges:
 * <ul>
 *   <li><strong>95–100</strong> — Explicit type mention (e.g. "sequence diagram")</li>
 *   <li><strong>80–90</strong> — AI provider classification</li>
 *   <li><strong>50–75</strong> — Semantic pattern detection (score-dependent)</li>
 *   <li><strong>20–55</strong> — Keyword scoring (score-dependent)</li>
 *   <li><strong>0–15</strong> — No meaningful signals found</li>
 * </ul>
 *
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Service
public class DiagramSuggestionServiceImpl implements DiagramSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramSuggestionServiceImpl.class);

    private final AiModelService aiModelService;

    // ─── Explicit type mention patterns ───────────────────────────────────────

    private static final Map<DiagramType, String> TYPE_DISPLAY_NAMES = Map.of(
            DiagramType.CLASS, "Class Diagram",
            DiagramType.ER, "Entity-Relationship Diagram",
            DiagramType.SEQUENCE, "Sequence Diagram",
            DiagramType.USE_CASE, "Use Case Diagram",
            DiagramType.COMPONENT, "Component Diagram",
            DiagramType.DEPLOYMENT, "Deployment Diagram"
    );

    private static final List<Map.Entry<Pattern, DiagramType>> EXPLICIT_TYPE_PATTERNS = List.of(
            Map.entry(Pattern.compile("\\buse[\\s-]?case\\b", Pattern.CASE_INSENSITIVE), DiagramType.USE_CASE),
            Map.entry(Pattern.compile("\\bentity[\\s-]?relationship\\b", Pattern.CASE_INSENSITIVE), DiagramType.ER),
            Map.entry(Pattern.compile("\\ber\\s+diagram\\b", Pattern.CASE_INSENSITIVE), DiagramType.ER),
            Map.entry(Pattern.compile("\\bsequence\\b", Pattern.CASE_INSENSITIVE), DiagramType.SEQUENCE),
            Map.entry(Pattern.compile("\\bclass\\s+diagram\\b", Pattern.CASE_INSENSITIVE), DiagramType.CLASS),
            Map.entry(Pattern.compile("\\bcomponent\\s+diagram\\b", Pattern.CASE_INSENSITIVE), DiagramType.COMPONENT),
            Map.entry(Pattern.compile("\\bdeployment\\b", Pattern.CASE_INSENSITIVE), DiagramType.DEPLOYMENT)
    );

    // ─── Semantic pattern categories ──────────────────────────────────────────

    private static final Map<DiagramType, Set<String>> SEMANTIC_CATEGORIES = Map.of(
            DiagramType.SEQUENCE, Set.of(
                    "sends", "calls", "returns", "requests", "responds",
                    "validates", "processes", "invokes", "notifies", "triggers",
                    "receives", "forwards", "delegates", "acknowledges", "subscribes"
            ),
            DiagramType.DEPLOYMENT, Set.of(
                    "server", "node", "deploy", "cloud", "container",
                    "docker", "kubernetes", "infrastructure", "host",
                    "virtual machine", "vm", "cluster", "environment", "artifact",
                    "aws", "azure", "load balancer", "firewall"
            ),
            DiagramType.COMPONENT, Set.of(
                    "module", "package", "library", "dependency", "port",
                    "connector", "subsystem", "layer", "api", "microservice",
                    "bundle", "service layer", "data access"
            ),
            DiagramType.USE_CASE, Set.of(
                    "actor", "scenario", "goal", "system boundary",
                    "stakeholder", "requirement", "functional", "behavior"
            ),
            DiagramType.ER, Set.of(
                    "table", "column", "primary key", "foreign key", "schema",
                    "database", "one-to-many", "many-to-many", "one-to-one",
                    "cardinality", "normalization", "record", "relation"
            ),
            DiagramType.CLASS, Set.of(
                    "has", "contains", "entity", "attribute", "field", "property",
                    "inherits", "extends", "implements", "abstract", "interface",
                    "method", "member", "constructor", "encapsulation", "polymorphism"
            )
    );

    // ─── Keyword sets for scoring ─────────────────────────────────────────────

    private static final Map<DiagramType, Set<String>> KEYWORD_SETS = Map.of(
            DiagramType.CLASS, Set.of(
                    "class", "object", "inheritance", "extends", "implements", "interface",
                    "abstract", "polymorphism", "encapsulation", "method", "attribute",
                    "property", "field", "member", "constructor", "instance"
            ),
            DiagramType.ER, Set.of(
                    "entity", "relationship", "database", "table", "column", "primary key",
                    "foreign key", "schema", "attribute", "cardinality", "one-to-many",
                    "many-to-many", "one-to-one", "normalization", "record"
            ),
            DiagramType.SEQUENCE, Set.of(
                    "sequence", "message", "call", "return", "async", "synchronous",
                    "request", "response", "interaction", "timeline", "actor", "lifeline",
                    "activation", "flow", "step"
            ),
            DiagramType.USE_CASE, Set.of(
                    "use case", "actor", "user", "scenario", "goal", "system boundary",
                    "include", "extend", "generalization", "stakeholder", "requirement",
                    "functional", "behavior", "action"
            ),
            DiagramType.COMPONENT, Set.of(
                    "component", "module", "package", "library", "dependency",
                    "port", "connector", "subsystem", "layer", "api", "service",
                    "microservice", "bundle"
            ),
            DiagramType.DEPLOYMENT, Set.of(
                    "deployment", "server", "node", "device", "artifact", "container",
                    "docker", "kubernetes", "cloud", "infrastructure", "environment",
                    "instance", "host", "virtual machine", "vm", "cluster"
            )
    );

    public DiagramSuggestionServiceImpl(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
        logger.info("DiagramSuggestionService initialized with AI provider: {}",
                aiModelService.getClass().getSimpleName());
    }

    @Override
    public DiagramSuggestion suggest(String inputText) {
        logger.debug("Generating suggestion for input (length: {} chars)",
                inputText != null ? inputText.length() : 0);

        // Validate input - throws exception if null or blank
        if (inputText == null || inputText.isBlank()) {
            logger.error("Suggestion failed: input text is null or blank");
            throw new IllegalArgumentException("Input text cannot be null or empty");
        }

        String normalizedText = inputText.toLowerCase().trim();

        // Layer 1: Explicit type mention → highest confidence
        DiagramSuggestion explicit = checkExplicitMention(normalizedText);
        if (explicit != null) {
            logger.info("Suggestion: {} (confidence: {}, source: EXPLICIT)",
                    explicit.getSuggestedDiagramType(), explicit.getConfidenceScore());
            return explicit;
        }

        // Layer 2: AI-powered classification → high confidence
        DiagramSuggestion aiSuggestion = attemptAiSuggestion(normalizedText);
        if (aiSuggestion != null) {
            logger.info("Suggestion: {} (confidence: {}, source: AI)",
                    aiSuggestion.getSuggestedDiagramType(), aiSuggestion.getConfidenceScore());
            return aiSuggestion;
        }

        // Layer 3: Semantic pattern detection → medium confidence
        DiagramSuggestion semantic = evaluateSemanticPatterns(normalizedText);
        if (semantic != null) {
            logger.info("Suggestion: {} (confidence: {}, source: SEMANTIC)",
                    semantic.getSuggestedDiagramType(), semantic.getConfidenceScore());
            return semantic;
        }

        // Layer 4: Keyword scoring → low-to-medium confidence
        DiagramSuggestion keyword = evaluateKeywords(normalizedText);
        logger.info("Suggestion: {} (confidence: {}, source: KEYWORD)",
                keyword.getSuggestedDiagramType(), keyword.getConfidenceScore());
        return keyword;
    }

    // ─── Layer 1: Explicit mention ────────────────────────────────────────────

    private DiagramSuggestion checkExplicitMention(String text) {
        for (Map.Entry<Pattern, DiagramType> entry : EXPLICIT_TYPE_PATTERNS) {
            if (entry.getKey().matcher(text).find()) {
                DiagramType type = entry.getValue();
                String displayName = TYPE_DISPLAY_NAMES.getOrDefault(type, type.getDisplayName());
                logger.debug("Explicit mention detected: {} via pattern '{}'", type, entry.getKey().pattern());

                return new DiagramSuggestion(
                        type, 95,
                        "Your description explicitly mentions a " + displayName + ". Proceeding with generation.",
                        ClassificationSource.EXPLICIT_MENTION
                );
            }
        }
        return null;
    }

    // ─── Layer 2: AI classification ───────────────────────────────────────────

    private DiagramSuggestion attemptAiSuggestion(String text) {
        Instant start = Instant.now();
        String providerName = aiModelService.getClass().getSimpleName();

        try {
            String prompt = buildSuggestionPrompt(text);
            logger.debug("Sending suggestion request to {} (prompt length: {} chars)",
                    providerName, prompt.length());

            String response = aiModelService.generateStructuredResponse(prompt);
            Duration elapsed = Duration.between(start, Instant.now());
            logger.info("AI suggestion completed via {} in {} ms", providerName, elapsed.toMillis());

            return parseAiSuggestionResponse(response);

        } catch (AiServiceException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.warn("AI suggestion failed via {} after {} ms: {}. Falling back to pattern detection.",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Unexpected error during AI suggestion via {} after {} ms: {}",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        }
    }

    private String buildSuggestionPrompt(String text) {
        return """
                You are a software modeling expert.
                Classify the following description into one of:
                CLASS, ER, SEQUENCE, USE_CASE, COMPONENT, DEPLOYMENT.
                
                Also provide a confidence score (0-100) and a brief reasoning.
                
                Return ONLY valid JSON:
                {
                  "diagramType": "CLASS",
                  "confidence": 85,
                  "reasoning": "The description mentions classes and inheritance relationships."
                }
                
                Text:
                """ + text;
    }

    private DiagramSuggestion parseAiSuggestionResponse(String response) {
        if (response == null || response.isBlank()) {
            logger.warn("AI returned empty response for suggestion");
            return null;
        }

        try {
            DiagramType type = extractDiagramType(response);
            if (type == null) return null;

            int confidence = extractConfidence(response);
            String reasoning = extractReasoning(response);

            // AI confidence is capped at 90 — only explicit mentions get 95+
            int adjustedConfidence = Math.min(confidence, 90);

            String displayName = TYPE_DISPLAY_NAMES.getOrDefault(type, type.getDisplayName());
            String message = reasoning != null && !reasoning.isBlank()
                    ? reasoning
                    : "AI analysis suggests a " + displayName + ".";

            return new DiagramSuggestion(type, adjustedConfidence, message, ClassificationSource.AI_PROVIDER);

        } catch (Exception e) {
            logger.error("Failed to parse AI suggestion response: {}", e.getMessage());
            return null;
        }
    }

    // ─── Layer 3: Semantic patterns ───────────────────────────────────────────

    private DiagramSuggestion evaluateSemanticPatterns(String text) {
        DiagramType bestType = null;
        int bestScore = 0;

        for (Map.Entry<DiagramType, Set<String>> entry : SEMANTIC_CATEGORIES.entrySet()) {
            int score = countMatches(text, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        if (bestScore < 2 || bestType == null) {
            logger.debug("No strong semantic pattern detected (best score: {})", bestScore);
            return null;
        }

        // Map semantic score to confidence: 2 matches → 50, 3 → 57, 4 → 64, 5+ → 70+
        int confidence = Math.min(75, 50 + (bestScore - 2) * 7);

        String displayName = TYPE_DISPLAY_NAMES.getOrDefault(bestType, bestType.getDisplayName());
        String reasoning = buildSemanticReasoning(bestType, displayName);

        logger.debug("Semantic pattern: {} with score {} → confidence {}", bestType, bestScore, confidence);
        return new DiagramSuggestion(bestType, confidence, reasoning, ClassificationSource.SEMANTIC_PATTERN);
    }

    private String buildSemanticReasoning(DiagramType type, String displayName) {
        return switch (type) {
            case SEQUENCE -> "Your description appears to describe interactions. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case DEPLOYMENT -> "Your description mentions infrastructure and deployment concepts. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case COMPONENT -> "Your description references software components and modules. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case USE_CASE -> "Your description outlines user scenarios and system behaviors. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case ER -> "Your description involves database entities and relationships. " +
                    "An " + displayName + " may be appropriate. Do you want to proceed?";
            case CLASS -> "Your description references object-oriented structures. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case OBJECT -> "Your description references concrete object instances. " +
                    "An " + displayName + " may be appropriate. Do you want to proceed?";
            case ACTIVITY -> "Your description outlines a workflow or process with steps. " +
                    "An " + displayName + " may be appropriate. Do you want to proceed?";
            case STATE -> "Your description describes state transitions or lifecycle. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case COLLABORATION -> "Your description depicts how objects collaborate. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
            case MICROSERVICES -> "Your description references microservice boundaries and interactions. " +
                    "A " + displayName + " may be appropriate. Do you want to proceed?";
        };
    }

    // ─── Layer 4: Keyword scoring ─────────────────────────────────────────────

    private DiagramSuggestion evaluateKeywords(String text) {
        DiagramType bestType = null;
        int bestScore = 0;

        for (Map.Entry<DiagramType, Set<String>> entry : KEYWORD_SETS.entrySet()) {
            int score = countMatches(text, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        if (bestScore == 0 || bestType == null) {
            logger.warn("No keywords matched — returning lowest confidence suggestion");
            return new DiagramSuggestion(
                    DiagramType.CLASS, 10,
                    "Your description is too vague to determine a diagram type. " +
                            "Please describe system structure or interactions more specifically.",
                    ClassificationSource.KEYWORD_SCORING
            );
        }

        // Map keyword score to confidence: 1 → 25, 2 → 35, 3 → 45, 4+ → 55
        int confidence = Math.min(55, 25 + (bestScore - 1) * 10);

        String displayName = TYPE_DISPLAY_NAMES.getOrDefault(bestType, bestType.getDisplayName());
        String reasoning = "Based on keyword analysis, a " + displayName +
                " seems most appropriate. Do you want to proceed?";

        logger.debug("Keyword scoring: {} with score {} → confidence {}", bestType, bestScore, confidence);
        return new DiagramSuggestion(bestType, confidence, reasoning, ClassificationSource.KEYWORD_SCORING);
    }

    // ─── Utility methods ──────────────────────────────────────────────────────

    private int countMatches(String text, Set<String> terms) {
        int count = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                count++;
                if (term.contains(" ")) {
                    count++; // extra weight for multi-word matches
                }
            }
        }
        return count;
    }

    private DiagramType extractDiagramType(String response) {
        String searchKey = "\"diagramType\"";
        int keyIndex = response.indexOf(searchKey);
        if (keyIndex == -1) {
            searchKey = "diagramType";
            keyIndex = response.indexOf(searchKey);
        }
        if (keyIndex == -1) return null;

        int colonIndex = response.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        int valueStart = colonIndex + 1;
        while (valueStart < response.length() &&
                (response.charAt(valueStart) == ' ' || response.charAt(valueStart) == '"')) {
            valueStart++;
        }

        int valueEnd = valueStart;
        while (valueEnd < response.length() &&
                response.charAt(valueEnd) != '"' &&
                response.charAt(valueEnd) != ',' &&
                response.charAt(valueEnd) != '}' &&
                response.charAt(valueEnd) != ' ') {
            valueEnd++;
        }

        String value = response.substring(valueStart, valueEnd).trim().toUpperCase();
        try {
            return DiagramType.fromCode(value);
        } catch (IllegalArgumentException e) {
            logger.warn("AI returned invalid diagram type: {}", value);
            return null;
        }
    }

    private int extractConfidence(String response) {
        String searchKey = "\"confidence\"";
        int keyIndex = response.indexOf(searchKey);
        if (keyIndex == -1) {
            searchKey = "confidence";
            keyIndex = response.indexOf(searchKey);
        }
        if (keyIndex == -1) return 80; // default AI confidence

        int colonIndex = response.indexOf(":", keyIndex);
        if (colonIndex == -1) return 80;

        int valueStart = colonIndex + 1;
        while (valueStart < response.length() &&
                (response.charAt(valueStart) == ' ' || response.charAt(valueStart) == '"')) {
            valueStart++;
        }

        int valueEnd = valueStart;
        while (valueEnd < response.length() && Character.isDigit(response.charAt(valueEnd))) {
            valueEnd++;
        }

        try {
            return Integer.parseInt(response.substring(valueStart, valueEnd).trim());
        } catch (NumberFormatException e) {
            return 80;
        }
    }

    private String extractReasoning(String response) {
        String searchKey = "\"reasoning\"";
        int keyIndex = response.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = response.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        int quoteStart = response.indexOf("\"", colonIndex + 1);
        if (quoteStart == -1) return null;

        int quoteEnd = response.indexOf("\"", quoteStart + 1);
        // Handle escaped quotes
        while (quoteEnd > 0 && response.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = response.indexOf("\"", quoteEnd + 1);
        }
        if (quoteEnd == -1) return null;

        return response.substring(quoteStart + 1, quoteEnd);
    }
}
