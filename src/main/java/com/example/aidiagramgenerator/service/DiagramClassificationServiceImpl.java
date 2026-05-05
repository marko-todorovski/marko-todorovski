package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.ClassificationResponse;
import com.example.aidiagramgenerator.domain.ClassificationResult;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.exception.InvalidDiagramRequestException;
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
 * Implementation of DiagramClassificationService using a layered classification strategy.
 * 
 * <p>Classification priority:
 * <ol>
 *   <li><strong>Explicit mention</strong> — user directly names a diagram type</li>
 *   <li><strong>AI-powered classification</strong> — via the configured AI provider (structured JSON)</li>
 *   <li><strong>Semantic pattern detection</strong> — interaction verbs, structural words, infrastructure terms</li>
 *   <li><strong>Keyword scoring</strong> — weighted keyword matching across all diagram types</li>
 *   <li><strong>AI fallback</strong> — if keyword confidence &lt; 50, call LLM with a simple plain-text prompt</li>
 * </ol>
 * 
 * <p>If all layers fail, an {@link InvalidDiagramRequestException} is thrown.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Service
public class DiagramClassificationServiceImpl implements DiagramClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramClassificationServiceImpl.class);

    private final AiModelService aiModelService;

    // ─── Explicit type mention patterns ───────────────────────────────────────

    /**
     * Patterns that detect an explicit diagram type mention in user input.
     * Ordered so more specific multi-word patterns are checked first.
     */
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

    /**
     * Interaction verbs that strongly suggest a SEQUENCE diagram.
     */
    private static final Set<String> INTERACTION_VERBS = Set.of(
            "sends", "calls", "returns", "requests", "responds",
            "validates", "processes", "invokes", "notifies", "triggers",
            "receives", "forwards", "delegates", "acknowledges", "subscribes"
    );

    /**
     * Structural words that suggest a CLASS or ER diagram.
     */
    private static final Set<String> STRUCTURAL_WORDS = Set.of(
            "has", "contains", "entity", "attribute", "field", "property",
            "inherits", "extends", "implements", "abstract", "interface",
            "method", "member", "constructor", "encapsulation", "polymorphism"
    );

    /**
     * Database/relational words that disambiguate ER from CLASS.
     */
    private static final Set<String> ER_INDICATORS = Set.of(
            "table", "column", "primary key", "foreign key", "schema",
            "database", "one-to-many", "many-to-many", "one-to-one",
            "cardinality", "normalization", "record", "relation"
    );

    /**
     * Infrastructure terms that strongly suggest a DEPLOYMENT diagram.
     */
    private static final Set<String> INFRASTRUCTURE_TERMS = Set.of(
            "server", "node", "deploy", "cloud", "container",
            "docker", "kubernetes", "infrastructure", "host",
            "virtual machine", "vm", "cluster", "environment", "artifact",
            "aws", "azure", "load balancer", "firewall"
    );

    /**
     * Component/architecture terms for COMPONENT diagrams.
     */
    private static final Set<String> COMPONENT_TERMS = Set.of(
            "module", "package", "library", "dependency", "port",
            "connector", "subsystem", "layer", "api", "microservice",
            "bundle", "service layer", "data access"
    );

    /**
     * Use-case terms for USE_CASE diagrams.
     */
    private static final Set<String> USE_CASE_TERMS = Set.of(
            "actor", "use case", "usecase", "scenario", "goal", "system boundary",
            "stakeholder", "requirement", "functional", "behavior",
            "user can", "admin can", "can perform", "can manage", "can view"
    );

    /**
     * Minimum keyword score threshold to accept a classification.
     * Below this threshold, input is considered too vague.
     */
    private static final int VAGUE_INPUT_THRESHOLD = 1;

    /**
     * Confidence threshold below which rule-based classification triggers AI fallback.
     * Confidence is computed as: min(100, keywordScore * 10).
     */
    private static final int AI_FALLBACK_CONFIDENCE_THRESHOLD = 50;

    /**
     * Maximum number of keywords per type used for confidence normalization.
     */
    private static final int KEYWORDS_PER_TYPE = 16;

    /**
     * Constructs the classification service with AI model dependency.
     * 
     * @param aiModelService the AI provider for intelligent classification
     */
    public DiagramClassificationServiceImpl(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
        logger.info("DiagramClassificationService initialized with AI provider: {}", 
                aiModelService.getClass().getSimpleName());
    }

    @Override
    public DiagramType classify(String text) {
        logger.debug("Classifying text input for diagram type (length: {} chars)",
                text != null ? text.length() : 0);

        // Validate input - throws exception if null or blank
        if (text == null || text.isBlank()) {
            logger.error("Classification failed: input text is null or blank");
            throw new IllegalArgumentException("Input text cannot be null or empty");
        }

        String normalizedText = text.toLowerCase().trim();

        // Layer 1: Check for explicit diagram type mention
        DiagramType explicit = detectExplicitType(normalizedText);
        if (explicit != null) {
            logger.info("Classification decision: EXPLICIT mention → {}", explicit);
            return explicit;
        }

        // Layer 2: Attempt AI-powered classification
        DiagramType aiResult = attemptAiClassification(normalizedText);
        if (aiResult != null) {
            logger.info("Classification decision: AI provider → {}", aiResult);
            return aiResult;
        }

        // Layer 3: Semantic pattern detection
        DiagramType semantic = detectSemanticPattern(normalizedText);
        if (semantic != null) {
            logger.info("Classification decision: SEMANTIC pattern → {}", semantic);
            return semantic;
        }

        // Layer 4: Keyword scoring fallback
        return classifyByKeywordsOrReject(normalizedText);
    }

    // ─── Layer 1: Explicit type detection ─────────────────────────────────────

    /**
     * Detects if the user explicitly mentions a diagram type.
     * 
     * @param text the normalized input text
     * @return the explicitly mentioned DiagramType, or null if none found
     */
    private DiagramType detectExplicitType(String text) {
        for (Map.Entry<Pattern, DiagramType> entry : EXPLICIT_TYPE_PATTERNS) {
            if (entry.getKey().matcher(text).find()) {
                logger.debug("Explicit type detected via pattern '{}': {}",
                        entry.getKey().pattern(), entry.getValue());
                return entry.getValue();
            }
        }
        logger.debug("No explicit diagram type mentioned");
        return null;
    }

    // ─── Layer 2: AI classification ───────────────────────────────────────────

    /**
     * Attempts to classify using the configured AI provider.
     *
     * @param text the normalized text to classify
     * @return the classified DiagramType, or null if AI classification fails
     */
    private DiagramType attemptAiClassification(String text) {
        Instant start = Instant.now();
        String providerName = aiModelService.getClass().getSimpleName();
        
        try {
            String prompt = buildClassificationPrompt(text);
            logger.debug("Sending classification request to {} (prompt length: {} chars)", 
                    providerName, prompt.length());
            
            String response = aiModelService.generateStructuredResponse(prompt);
            
            Duration elapsed = Duration.between(start, Instant.now());
            logger.info("AI classification completed via {} in {} ms", providerName, elapsed.toMillis());
            
            return parseClassificationResponse(response);
            
        } catch (AiServiceException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.warn("AI classification failed via {} after {} ms: {}. Falling back to pattern detection.",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Unexpected error during AI classification via {} after {} ms: {}", 
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        }
    }

    /**
     * Builds the classification prompt for the AI provider.
     */
    private String buildClassificationPrompt(String text) {
        return """
                You are a software modeling expert.
                Classify the following description into one of:
                CLASS, ER, SEQUENCE, USE_CASE, COMPONENT, DEPLOYMENT.
                
                Return ONLY valid JSON:
                {
                  "diagramType": "CLASS"
                }
                
                Text:
                """ + text;
    }

    /**
     * Parses the JSON response from the AI provider.
     */
    private DiagramType parseClassificationResponse(String response) {
        if (response == null || response.isBlank()) {
            logger.warn("AI returned empty response");
            return null;
        }
        
        logger.debug("Parsing AI response: {}", response);
        
        try {
            String searchKey = "\"diagramType\"";
            int keyIndex = response.indexOf(searchKey);
            if (keyIndex == -1) {
                searchKey = "diagramType";
                keyIndex = response.indexOf(searchKey);
            }
            
            if (keyIndex == -1) {
                logger.warn("AI response missing diagramType field: {}", response);
                return null;
            }
            
            int colonIndex = response.indexOf(":", keyIndex);
            if (colonIndex == -1) {
                logger.warn("AI response malformed (no colon after diagramType): {}", response);
                return null;
            }
            
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
            
            String diagramTypeValue = response.substring(valueStart, valueEnd).trim().toUpperCase();
            logger.debug("Extracted diagram type value: {}", diagramTypeValue);
            
            DiagramType result = DiagramType.fromCode(diagramTypeValue);
            logger.info("AI classification result: {}", result);
            return result;
            
        } catch (IllegalArgumentException e) {
            logger.warn("AI returned invalid diagram type in response: {}", response);
            return null;
        } catch (Exception e) {
            logger.error("Failed to parse AI classification response: {}", e.getMessage());
            return null;
        }
    }

    // ─── Layer 3: Semantic pattern detection ──────────────────────────────────

    /**
     * Detects the diagram type based on semantic categories of words.
     * 
     * <p>Priority:
     * <ul>
     *   <li>Interaction verbs → SEQUENCE</li>
     *   <li>Infrastructure terms → DEPLOYMENT</li>
     *   <li>Component terms → COMPONENT</li>
     *   <li>Use-case terms → USE_CASE</li>
     *   <li>ER indicators → ER</li>
     *   <li>Structural words → CLASS</li>
     * </ul>
     * 
     * @param text the normalized input text
     * @return the detected DiagramType, or null if no strong signal
     */
    private DiagramType detectSemanticPattern(String text) {
        int interactionScore = countMatches(text, INTERACTION_VERBS);
        int infrastructureScore = countMatches(text, INFRASTRUCTURE_TERMS);
        int componentScore = countMatches(text, COMPONENT_TERMS);
        int useCaseScore = countMatches(text, USE_CASE_TERMS);
        int erScore = countMatches(text, ER_INDICATORS);
        int structuralScore = countMatches(text, STRUCTURAL_WORDS);

        logger.debug("Semantic scores — SEQUENCE:{}, DEPLOYMENT:{}, COMPONENT:{}, USE_CASE:{}, ER:{}, CLASS:{}",
                interactionScore, infrastructureScore, componentScore, useCaseScore, erScore, structuralScore);

        // Find the category with the strongest signal (minimum 2 matches for confidence)
        int threshold = 2;
        // USE_CASE is lowered to 1 — each matched multi-word pattern ("user can", "can view") already
        // scores +2 from countMatches(), so a single phrase is a strong enough signal.
        int useCaseThreshold = 1;

        if (interactionScore >= threshold && interactionScore >= infrastructureScore
                && interactionScore >= componentScore) {
            logger.debug("Semantic pattern: interaction verbs dominate → SEQUENCE");
            return DiagramType.SEQUENCE;
        }

        if (infrastructureScore >= threshold && infrastructureScore >= componentScore) {
            logger.debug("Semantic pattern: infrastructure terms dominate → DEPLOYMENT");
            return DiagramType.DEPLOYMENT;
        }

        if (componentScore >= threshold) {
            logger.debug("Semantic pattern: component terms dominate → COMPONENT");
            return DiagramType.COMPONENT;
        }

        if (useCaseScore >= useCaseThreshold && useCaseScore >= interactionScore
                && useCaseScore >= infrastructureScore && useCaseScore >= componentScore) {
            logger.debug("Semantic pattern: use-case terms dominate → USE_CASE");
            return DiagramType.USE_CASE;
        }

        // Distinguish ER from CLASS: if ER-specific words are stronger, choose ER
        if (erScore >= threshold) {
            logger.debug("Semantic pattern: ER indicators dominate → ER");
            return DiagramType.ER;
        }

        if (structuralScore >= threshold) {
            // If ER indicators are also present, lean towards ER
            if (erScore > 0) {
                logger.debug("Semantic pattern: structural + ER signals → ER");
                return DiagramType.ER;
            }
            logger.debug("Semantic pattern: structural words dominate → CLASS");
            return DiagramType.CLASS;
        }

        logger.debug("No strong semantic pattern detected");
        return null;
    }

    /**
     * Counts how many terms from a keyword set appear in the text.
     * Multi-word terms receive double weight.
     * 
     * @param text the input text
     * @param terms the set of terms to check
     * @return the weighted match count
     */
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

    // ─── Layer 4: Keyword scoring ─────────────────────────────────────────────

    /**
     * Full keyword scoring across all diagram types.
     * If no keywords match above the threshold, the input is rejected as too vague.
     * 
     * @param text the normalized input text
     * @return the classified DiagramType
     * @throws InvalidDiagramRequestException if input is too vague
     */
    private DiagramType classifyByKeywordsOrReject(String text) {
        Map<DiagramType, Set<String>> allKeywords = Map.of(
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
                        "functional", "behavior", "action",
                        "user can", "admin can", "can perform", "can manage", "can view"
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

        DiagramType bestMatch = null;
        int highestScore = 0;

        for (Map.Entry<DiagramType, Set<String>> entry : allKeywords.entrySet()) {
            int score = countMatches(text, entry.getValue());
            logger.trace("Keyword score for {}: {}", entry.getKey(), score);

            if (score > highestScore) {
                highestScore = score;
                bestMatch = entry.getKey();
            }
        }

        // Compute confidence as a percentage (each match contributes ~6%)
        int confidence = Math.min(100, (int) ((highestScore / (double) KEYWORDS_PER_TYPE) * 100));
        logger.debug("Keyword confidence: {}% (score: {}, best match: {})", confidence, highestScore, bestMatch);

        // If confidence is sufficient, return the keyword-based result
        if (confidence >= AI_FALLBACK_CONFIDENCE_THRESHOLD && bestMatch != null) {
            logger.info("Classification decision: KEYWORD scoring → {} (confidence: {}%)", bestMatch, confidence);
            return bestMatch;
        }

        // Layer 5: AI fallback — rule-based confidence too low
        logger.info("Rule-based confidence {}% < {}% threshold — invoking AI fallback",
                confidence, AI_FALLBACK_CONFIDENCE_THRESHOLD);
        DiagramType aiFallback = attemptAiFallbackClassification(text);
        if (aiFallback != null) {
            logger.info("Classification decision: AI FALLBACK → {}", aiFallback);
            return aiFallback;
        }

        // If AI fallback also fails and we had some keyword match, use it anyway
        if (bestMatch != null && highestScore >= VAGUE_INPUT_THRESHOLD) {
            logger.warn("AI fallback failed, using low-confidence keyword result: {} (confidence: {}%)",
                    bestMatch, confidence);
            return bestMatch;
        }

        // Nothing worked — reject
        logger.warn("All classification layers exhausted — input too vague (score: {})", highestScore);
        throw new InvalidDiagramRequestException(
                "Please describe system structure or interactions. " +
                "Your input is too vague to determine a diagram type.");
    }

    // ─── Layer 5: AI Fallback Classification ──────────────────────────────────

    /**
     * Fallback AI classification using a simple plain-text prompt.
     * Called when rule-based confidence is below {@value #AI_FALLBACK_CONFIDENCE_THRESHOLD}%.
     *
     * @param text the normalized input text
     * @return the classified DiagramType, or null if the AI returns an invalid or unparseable value
     */
    private DiagramType attemptAiFallbackClassification(String text) {
        Instant start = Instant.now();
        String providerName = aiModelService.getClass().getSimpleName();

        try {
            String prompt = "Classify this text into one of: CLASS, SEQUENCE, ER, COMPONENT, DEPLOYMENT, USE_CASE. "
                    + "Return only the type. Text: " + text;

            logger.debug("Sending AI fallback classification to {} (prompt length: {} chars)",
                    providerName, prompt.length());

            String response = aiModelService.generateStructuredResponse(prompt);

            Duration elapsed = Duration.between(start, Instant.now());
            logger.info("AI fallback completed via {} in {} ms", providerName, elapsed.toMillis());

            return parsePlainTypeResponse(response);

        } catch (AiServiceException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.warn("AI fallback failed via {} after {} ms: {}",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Unexpected error during AI fallback via {} after {} ms: {}",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        }
    }

    /**
     * Parses a plain-text AI response that should contain only a diagram type name.
     * Handles whitespace, quotes, punctuation, and JSON wrappers gracefully.
     *
     * @param response the raw AI response
     * @return the parsed DiagramType, or null if the response is invalid
     */
    private DiagramType parsePlainTypeResponse(String response) {
        if (response == null || response.isBlank()) {
            logger.warn("AI fallback returned empty response");
            return null;
        }

        // Clean the response: strip whitespace, quotes, punctuation, code fences
        String cleaned = response.strip()
                .replaceAll("```[a-z]*", "")  // remove code fences
                .replaceAll("[\"'`{}\\[\\]]", "") // remove quotes and braces
                .replaceAll("[.,;:!?]", "")     // remove punctuation
                .strip();

        // If AI returned JSON-like "diagramType: CLASS", extract the value
        if (cleaned.toLowerCase().contains("diagramtype")) {
            int colonIdx = cleaned.indexOf(':');
            if (colonIdx != -1 && colonIdx < cleaned.length() - 1) {
                cleaned = cleaned.substring(colonIdx + 1).strip();
            }
        }

        // Extract the first word (the type name)
        String typeName = cleaned.split("\\s+")[0].toUpperCase();
        logger.debug("AI fallback cleaned response: '{}' → type candidate: '{}'", response.strip(), typeName);

        try {
            DiagramType result = DiagramType.fromCode(typeName);
            logger.info("AI fallback classification result: {}", result);
            return result;
        } catch (IllegalArgumentException e) {
            logger.warn("AI fallback returned invalid diagram type '{}' (raw: '{}')",
                    typeName, response.strip());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SemanticModel-based confidence classification
    // ═══════════════════════════════════════════════════════════════════════════

    // ── Confidence constants ──────────────────────────────────────────────────

    // Explicit match band (keyword/pattern names the type directly)
    private static final double EXPLICIT_CONFIDENCE_HIGH = 100.0;
    private static final double EXPLICIT_CONFIDENCE_BASE = 90.0;

    // Semantic match band (indirect but strong signals)
    private static final double SEMANTIC_CONFIDENCE_HIGH  = 80.0;
    private static final double SEMANTIC_CONFIDENCE_BASE  = 60.0;

    // Weak match band (few scattered signals)
    private static final double WEAK_CONFIDENCE_HIGH = 50.0;
    private static final double WEAK_CONFIDENCE_BASE = 30.0;

    // Decision tier thresholds
    private static final double AUTO_THRESHOLD    = 70.0;
    private static final double SUGGEST_THRESHOLD = 40.0;

    // ── Explicit signal keyword sets ──────────────────────────────────────────

    /** Relationship types that explicitly indicate a CLASS diagram. */
    private static final Set<String> CLASS_REL_EXPLICIT = Set.of(
            "inherits", "extends", "implements", "realizes", "generalization",
            "abstraction", "realization"
    );

    /** Relationship types / actions that explicitly indicate an ER diagram. */
    private static final Set<String> ER_REL_EXPLICIT = Set.of(
            "one-to-many", "many-to-many", "one-to-one", "foreign-key",
            "primary-key", "has-foreign-key", "references"
    );

    /** Actions or relationship types that explicitly indicate a SEQUENCE diagram. */
    private static final Set<String> SEQUENCE_ACTION_EXPLICIT = Set.of(
            "sends", "calls", "returns", "requests", "responds", "invokes",
            "notifies", "triggers", "receives", "forwards", "delegates",
            "acknowledges", "subscribes", "replies"
    );

    /** Entity name fragments or relationship types indicating USE_CASE. */
    private static final Set<String> USE_CASE_EXPLICIT = Set.of(
            "actor", "use case", "usecase", "goal", "scenario",
            "system boundary", "stakeholder", "include", "extend",
            "user can", "admin can", "can perform", "can manage", "can view"
    );

    /** Entity names / relationship types indicating COMPONENT. */
    private static final Set<String> COMPONENT_EXPLICIT = Set.of(
            "component", "module", "package", "port", "connector",
            "subsystem", "interface", "library", "bundle", "service",
            "microservice", "api"
    );

    /** Entity names / relationship types indicating DEPLOYMENT. */
    private static final Set<String> DEPLOYMENT_EXPLICIT = Set.of(
            "server", "node", "container", "docker", "kubernetes", "k8s",
            "cloud", "cluster", "virtual machine", "vm", "host", "device",
            "artifact", "environment", "aws", "azure", "gcp"
    );

    // ── Semantic signal keyword sets (used on the full composite text) ─────────

    private static final Set<String> SEMANTIC_ER = Set.of(
            "table", "column", "schema", "database", "record", "cardinality",
            "normalization", "row", "relation"
    );

    private static final Set<String> SEMANTIC_CLASS = Set.of(
            "class", "object", "attribute", "property", "method", "constructor",
            "field", "member", "encapsulation", "polymorphism", "abstract", "instance"
    );

    private static final Set<String> SEMANTIC_SEQUENCE = Set.of(
            "request", "response", "message", "interaction", "timeline",
            "lifeline", "activation", "flow", "step", "synchronous", "async"
    );

    private static final Set<String> SEMANTIC_USE_CASE = Set.of(
            "user", "requirement", "functional", "behavior", "action",
            "permission", "role", "interaction", "feature",
            "can perform", "can manage", "can view", "user can", "admin can"
    );

    private static final Set<String> SEMANTIC_COMPONENT = Set.of(
            "dependency", "layer", "tier", "gateway", "proxy", "middleware",
            "framework", "integration", "adapter", "plugin"
    );

    private static final Set<String> SEMANTIC_DEPLOYMENT = Set.of(
            "deploy", "infrastructure", "instance", "network", "subnet",
            "firewall", "load balancer", "replica", "pod", "namespace"
    );

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public ClassificationResponse classify(SemanticModel model) {
        if (model == null) {
            throw new IllegalArgumentException("SemanticModel must not be null");
        }

        // Flatten all text signals from the semantic model into lower-case token lists
        List<String> entityNames = model.getEntities().stream()
                .map(e -> e.getName().toLowerCase())
                .toList();
        List<String> attributes = model.getEntities().stream()
                .flatMap(e -> e.getAttributes().stream())
                .map(String::toLowerCase)
                .toList();
        List<String> relTypes = model.getRelationships().stream()
                .map(r -> r.getType().toLowerCase())
                .toList();
        List<String> actions = model.getActions().stream()
                .map(String::toLowerCase)
                .toList();

        String compositeText = String.join(" ", entityNames) + " "
                + String.join(" ", attributes) + " "
                + String.join(" ", relTypes) + " "
                + String.join(" ", actions);

        logger.debug("classify(SemanticModel): entities={}, rels={}, actions={}",
                entityNames.size(), relTypes.size(), actions.size());

        // Layer 1 – explicit keyword signals → 90–100 %
        ClassificationResult explicit = detectExplicitFromModel(entityNames, relTypes, actions, compositeText);
        if (explicit != null) {
            logger.info("SemanticModel classification: EXPLICIT → {} ({:.1f}%)", explicit.getType(), explicit.getConfidence());
            return toResponse(explicit);
        }

        // Layer 2 – semantic pattern signals → 60–80 %
        ClassificationResult semantic = detectSemanticFromModel(compositeText, relTypes, actions);
        if (semantic != null) {
            logger.info("SemanticModel classification: SEMANTIC → {} ({:.1f}%)", semantic.getType(), semantic.getConfidence());
            return toResponse(semantic);
        }

        // Layer 3 – weak keyword scoring → 30–50 %
        ClassificationResult weak = detectWeakFromModel(compositeText);
        logger.info("SemanticModel classification: WEAK → {} ({}%)", weak.getType(), weak.getConfidence());
        return toResponse(weak);
    }

    // ── Layer 1: Explicit detection from SemanticModel ───────────────────────

    /**
     * Assigns confidence 90–100 when the model's tokens contain explicit type-naming keywords.
     *
     * <ul>
     *   <li>100 if a type keyword appears in ≥ 2 distinct signal sources (entity name + rel type, etc.)</li>
     *   <li>90  if a type keyword appears in exactly one signal source</li>
     * </ul>
     */
    private ClassificationResult detectExplicitFromModel(
            List<String> entityNames, List<String> relTypes,
            List<String> actions, String compositeText) {

        // Map each diagram type to its explicit keyword sets and the token lists to inspect
        record TypeSignal(DiagramType type, Set<String> keywords, List<List<String>> sources) {}

        List<TypeSignal> candidates = List.of(
                new TypeSignal(DiagramType.SEQUENCE,   SEQUENCE_ACTION_EXPLICIT,
                        List.of(actions, relTypes)),
                new TypeSignal(DiagramType.CLASS,      CLASS_REL_EXPLICIT,
                        List.of(relTypes, actions)),
                new TypeSignal(DiagramType.ER,         ER_REL_EXPLICIT,
                        List.of(relTypes, actions)),
                new TypeSignal(DiagramType.USE_CASE,   USE_CASE_EXPLICIT,
                        List.of(entityNames, actions, relTypes)),
                new TypeSignal(DiagramType.COMPONENT,  COMPONENT_EXPLICIT,
                        List.of(entityNames, relTypes)),
                new TypeSignal(DiagramType.DEPLOYMENT, DEPLOYMENT_EXPLICIT,
                        List.of(entityNames, relTypes))
        );

        DiagramType bestType = null;
        double bestConfidence = 0;
        int bestHits = 0;
        String bestExplanation = "";

        for (TypeSignal ts : candidates) {
            int sourceMatches = 0;
            int totalMatches = 0;
            StringBuilder matchedKeywords = new StringBuilder();

            for (List<String> source : ts.sources()) {
                boolean sourceHit = false;
                for (String token : source) {
                    for (String kw : ts.keywords()) {
                        if (token.contains(kw)) {
                            totalMatches++;
                            if (!sourceHit) { sourceMatches++; sourceHit = true; }
                            if (!matchedKeywords.isEmpty()) matchedKeywords.append(", ");
                            matchedKeywords.append(kw);
                        }
                    }
                }
            }

            if (totalMatches == 0) continue;

            double confidence = sourceMatches >= 2
                    ? EXPLICIT_CONFIDENCE_HIGH
                    : EXPLICIT_CONFIDENCE_BASE + Math.min(9, totalMatches) * 1.0;

            if (totalMatches > bestHits || (totalMatches == bestHits && confidence > bestConfidence)) {
                bestType = ts.type();
                bestConfidence = confidence;
                bestHits = totalMatches;
                bestExplanation = "Explicit keyword match (" + matchedKeywords + ")";
            }
        }

        if (bestType == null) return null;
        return new ClassificationResult(bestType, bestConfidence, bestExplanation);
    }

    // ── Layer 2: Semantic detection from SemanticModel ───────────────────────

    /**
     * Assigns confidence 60–80 when semantic category keywords appear in the composite text.
     *
     * <p>Confidence within the band scales linearly with the number of matched keywords,
     * up to a maximum of 80.
     */
    private ClassificationResult detectSemanticFromModel(
            String compositeText, List<String> relTypes, List<String> actions) {

        record TypeSemantics(DiagramType type, Set<String> keywords) {}

        List<TypeSemantics> candidates = List.of(
                new TypeSemantics(DiagramType.SEQUENCE,   SEMANTIC_SEQUENCE),
                new TypeSemantics(DiagramType.DEPLOYMENT, SEMANTIC_DEPLOYMENT),
                new TypeSemantics(DiagramType.COMPONENT,  SEMANTIC_COMPONENT),
                new TypeSemantics(DiagramType.USE_CASE,   SEMANTIC_USE_CASE),
                new TypeSemantics(DiagramType.ER,         SEMANTIC_ER),
                new TypeSemantics(DiagramType.CLASS,      SEMANTIC_CLASS)
        );

        DiagramType bestType      = null;
        double      bestConfidence = 0;
        int         bestMatches   = 0;
        String      bestKeywords  = "";

        for (TypeSemantics ts : candidates) {
            int matches = countMatches(compositeText, ts.keywords());
            if (matches == 0) continue;

            // Linear scale: 1 match → 60, each additional match adds up to 4 points, cap at 80
            double confidence = Math.min(SEMANTIC_CONFIDENCE_HIGH,
                    SEMANTIC_CONFIDENCE_BASE + (matches - 1) * 5.0);

            if (matches > bestMatches || (matches == bestMatches && confidence > bestConfidence)) {
                bestType       = ts.type();
                bestConfidence = confidence;
                bestMatches    = matches;
                bestKeywords   = ts.keywords().stream()
                        .filter(compositeText::contains)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
            }
        }

        if (bestType == null || bestMatches < 2) return null;
        return new ClassificationResult(bestType, bestConfidence,
                "Semantic pattern match (" + bestKeywords + ")");
    }

    // ── Layer 3: Weak keyword scoring from SemanticModel ─────────────────────

    /**
     * Assigns confidence 30–50 based on raw keyword frequency.
     * Always returns a result — uses {@link DiagramType#SEQUENCE} as a safe default
     * when no keywords match at all.
     */
    private ClassificationResult detectWeakFromModel(String compositeText) {
        Map<DiagramType, Set<String>> allKeywords = Map.of(
                DiagramType.CLASS, SEMANTIC_CLASS,
                DiagramType.ER,    SEMANTIC_ER,
                DiagramType.SEQUENCE,   SEMANTIC_SEQUENCE,
                DiagramType.USE_CASE,   SEMANTIC_USE_CASE,
                DiagramType.COMPONENT,  SEMANTIC_COMPONENT,
                DiagramType.DEPLOYMENT, SEMANTIC_DEPLOYMENT
        );

        DiagramType bestType = DiagramType.SEQUENCE;
        int         bestScore = 0;

        for (Map.Entry<DiagramType, Set<String>> entry : allKeywords.entrySet()) {
            int score = countMatches(compositeText, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestType  = entry.getKey();
            }
        }

        // 0 matches → minimal confidence at floor; each match adds up to cap of 50
        double confidence = bestScore == 0
                ? WEAK_CONFIDENCE_BASE
                : Math.min(WEAK_CONFIDENCE_HIGH, WEAK_CONFIDENCE_BASE + bestScore * 4.0);

        String explanation = bestScore == 0
                ? "No clear signals found; defaulting to " + bestType.getDisplayName()
                : "Weak keyword match (score: " + bestScore + ")";

        return new ClassificationResult(bestType, confidence, explanation);
    }

    // ── Response builder ──────────────────────────────────────────────────────

    /**
     * Converts a {@link ClassificationResult} into a {@link ClassificationResponse}
     * by applying the decision-tier thresholds.
     */
    private ClassificationResponse toResponse(ClassificationResult result) {
        double confidence    = result.getConfidence();
        DiagramType type     = result.getType();
        String explanation   = result.getExplanation();

        if (confidence >= AUTO_THRESHOLD) {
            String message = String.format(
                    "Generating a %s automatically (confidence: %.0f%%). %s",
                    type.getDisplayName(), confidence, explanation);
            return ClassificationResponse.auto(type, confidence, message);
        }

        if (confidence >= SUGGEST_THRESHOLD) {
            String message = String.format(
                    "Based on your input, a %s seems most appropriate (confidence: %.0f%%). "
                            + "Would you like to proceed? %s",
                    type.getDisplayName(), confidence, explanation);
            return ClassificationResponse.suggest(type, confidence, message);
        }

        String message = String.format(
                "Your input is ambiguous (confidence: %.0f%%). "
                        + "Could you provide more details about the entities, relationships, "
                        + "or the type of diagram you need? %s",
                confidence, explanation);
        return ClassificationResponse.clarify(type, confidence, message);
    }
}
