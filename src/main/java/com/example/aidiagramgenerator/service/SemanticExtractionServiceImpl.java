package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of SemanticExtractionService using AI-powered extraction.
 * 
 * <p>This service uses the configured AI provider (OpenAI GPT-4o or Ollama Llama 3)
 * for intelligent entity and relationship extraction, with NLP heuristic fallback.
 * 
 * @author AI Diagram Generator Research Team
 * @since 1.0
 */
@Service
public class SemanticExtractionServiceImpl implements SemanticExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticExtractionServiceImpl.class);

    private final AiModelService aiModelService;

    /**
     * Constructs the extraction service with AI model dependency.
     * 
     * @param aiModelService the AI provider for intelligent extraction
     */
    public SemanticExtractionServiceImpl(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
        logger.info("SemanticExtractionService initialized with AI provider: {}",
                aiModelService.getClass().getSimpleName());
    }

    /**
     * Pattern to match capitalized words (potential entity names).
     * Matches words starting with uppercase followed by lowercase letters.
     */
    private static final Pattern CAPITALIZED_WORD_PATTERN = Pattern.compile("\\b([A-Z][a-z]+(?:[A-Z][a-z]+)*)\\b");

    /**
     * Pattern to match CamelCase or PascalCase identifiers.
     */
    private static final Pattern PASCAL_CASE_PATTERN = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\b");

    /**
     * Keywords that indicate relationships between entities.
     * Entries sorted longest-first so multi-word phrases (e.g. "inherits from")
     * are matched before their single-word sub-phrases (e.g. "inherits").
     */
    private static final List<Map.Entry<String, String>> RELATIONSHIP_KEYWORDS = List.of(
            // multi-word phrases first (longest match wins)
            Map.entry("inherits from", "inheritance"),
            Map.entry("is a subclass of", "inheritance"),
            Map.entry("is a subtype of", "inheritance"),
            Map.entry("is a type of", "inheritance"),
            Map.entry("is composed of", "composition"),
            Map.entry("is made up of", "composition"),
            Map.entry("is part of", "composition"),
            Map.entry("owned by", "association"),
            Map.entry("part of", "composition"),
            Map.entry("composed of", "composition"),
            Map.entry("depends on", "dependency"),
            Map.entry("connects to", "association"),
            Map.entry("linked to", "association"),
            Map.entry("associated with", "association"),
            Map.entry("belongs to", "association"),
            Map.entry("has a", "aggregation"),
            Map.entry("has an", "aggregation"),
            Map.entry("is a", "inheritance"),
            Map.entry("is an", "inheritance"),
            // single-word keywords
            Map.entry("contains", "composition"),
            Map.entry("contain", "composition"),
            Map.entry("owns", "composition"),
            Map.entry("own", "composition"),
            Map.entry("aggregates", "aggregation"),
            Map.entry("aggregate", "aggregation"),
            Map.entry("uses", "dependency"),
            Map.entry("use", "dependency"),
            Map.entry("using", "dependency"),
            Map.entry("extends", "inheritance"),
            Map.entry("inherits", "inheritance"),
            Map.entry("inherit", "inheritance"),
            Map.entry("implements", "inheritance"),
            Map.entry("implement", "inheritance"),
            Map.entry("depends", "dependency"),
            Map.entry("references", "association"),
            Map.entry("reference", "association"),
            Map.entry("creates", "dependency"),
            Map.entry("create", "dependency"),
            Map.entry("manages", "association"),
            Map.entry("manage", "association"),
            Map.entry("connects", "association"),
            Map.entry("has", "aggregation"),
            Map.entry("have", "aggregation"),
            Map.entry("having", "aggregation"),
            Map.entry("belongs", "association")
    );

    /**
     * Patterns for detecting UML multiplicity values in natural-language sentences.
     * Each entry maps a compiled Pattern to its canonical UML multiplicity string.
     * Ordered most-specific first so "1..*" wins over bare "many".
     */
    private static final List<Map.Entry<Pattern, String>> MULTIPLICITY_PATTERNS = List.of(
            Map.entry(Pattern.compile("(?i)\\b1\\.\\.[*]"),            "1..*"),
            Map.entry(Pattern.compile("(?i)\\b0\\.\\.[*]"),            "0..*"),
            Map.entry(Pattern.compile("(?i)\\b0\\.\\.[0-9]+"),         "0..1"),
            Map.entry(Pattern.compile("(?i)\\b1\\.\\.[0-9]+"),         "1..1"),
            Map.entry(Pattern.compile("(?i)\\bone\\s+or\\s+more\\b"),  "1..*"),
            Map.entry(Pattern.compile("(?i)\\bone\\s+or\\s+many\\b"),  "1..*"),
            Map.entry(Pattern.compile("(?i)\\bzero\\s+or\\s+more\\b"), "0..*"),
            Map.entry(Pattern.compile("(?i)\\bzero\\s+or\\s+one\\b"),  "0..1"),
            Map.entry(Pattern.compile("(?i)\\bat\\s+most\\s+one\\b"),  "0..1"),
            Map.entry(Pattern.compile("(?i)\\bexactly\\s+one\\b"),     "1"),
            Map.entry(Pattern.compile("(?i)\\bmany\\b"),               "0..*"),
            Map.entry(Pattern.compile("(?i)\\bmultiple\\b"),           "0..*"),
            Map.entry(Pattern.compile("(?i)\\bseveral\\b"),            "0..*")
    );

    /**
     * Keywords that indicate actions.
     */
    private static final Set<String> ACTION_VERBS = Set.of(
            "create", "read", "update", "delete", "save", "load", "process",
            "validate", "send", "receive", "notify", "authenticate", "authorize",
            "calculate", "generate", "transform", "convert", "execute", "invoke",
            "handle", "manage", "control", "monitor", "log", "track", "fetch",
            "store", "retrieve", "submit", "approve", "reject", "cancel",
            "login", "download", "view", "search", "register", "enroll", "attend",
            "take", "upload", "schedule", "grade", "pay", "print", "insert",
            "withdraw", "deposit", "transfer", "browse", "comment", "subscribe"
    );

    private static final Set<String> USE_CASE_ACTION_VERBS = Set.of(
            "login", "log in", "download", "view", "search", "register", "drop",
            "pay", "submit", "approve", "reject", "create", "update", "manage",
            "browse", "rate", "comment", "subscribe", "review", "edit", "publish",
            "enroll", "attend", "take", "upload", "schedule", "grade", "send",
            "receive", "generate", "insert", "authenticate", "withdraw", "deposit",
            "transfer", "print", "eject", "refill", "run", "collect", "check",
            "verify", "validate", "process", "assign"
    );

    /**
     * Words to exclude from entity detection (common English words).
     */
    private static final Set<String> EXCLUDED_WORDS = Set.of(
            "The", "This", "That", "These", "Those", "When", "Where", "What",
            "Which", "Who", "How", "Why", "Each", "Every", "Some", "Any",
            "All", "Most", "Many", "Few", "Several", "Both", "Either", "Neither",
            "First", "Second", "Third", "Last", "Next", "Previous", "One", "Two"
    );

    /**
     * Imperative verbs that users commonly prefix diagram requests with,
     * e.g. "Describe the flow...", "Show me...", "Create a diagram...".
     * These should never be treated as entity names.
     */
    private static final Set<String> IMPERATIVE_VERBS = Set.of(
            "Describe", "Show", "Create", "Design", "Generate", "Draw", "Model",
            "Build", "Define", "Explain", "List", "Diagram", "Display", "Illustrate",
            "Visualize", "Make", "Sketch", "Outline", "Map", "Plan", "Give"
    );

    @Override
    public SemanticModel extract(String text) {
        return extract(text, null);
    }

    @Override
    public SemanticModel extract(String text, DiagramType diagramType) {
        logger.debug("Extracting semantic model from text (diagramType={})", diagramType);

        validateInput(text);

        // Attempt AI-powered extraction with type-aware prompt
        SemanticModel aiModel = attemptAiExtraction(text, diagramType);
        if (aiModel != null) {
            return aiModel;
        }

        // Fallback to NLP heuristic extraction
        logger.info("Using NLP heuristic fallback for semantic extraction");
        return extractByHeuristics(text);
    }

    /**
     * Attempts to extract semantic model using the configured AI provider.
     *
     * @param text the input text
     * @return the extracted SemanticModel, or null if AI extraction fails
     */
    private SemanticModel attemptAiExtraction(String text, DiagramType diagramType) {
        Instant start = Instant.now();
        String providerName = aiModelService.getClass().getSimpleName();

        try {
            String prompt = buildExtractionPrompt(text, diagramType);
            logger.debug("Sending extraction request to {} (diagramType={}, prompt length: {} chars)",
                    providerName, diagramType, prompt.length());

            String response = aiModelService.generateStructuredResponse(prompt);

            Duration elapsed = Duration.between(start, Instant.now());
            logger.info("AI extraction completed via {} in {} ms", providerName, elapsed.toMillis());

            SemanticModel model = parseExtractionResponse(response);
            if (model != null) {
                logger.info("AI extracted {} entities, {} relationships",
                        model.getEntities().size(), model.getRelationships().size());
                return model;
            }

            logger.warn("AI response could not be parsed into a valid SemanticModel");
            return null;

        } catch (AiServiceException e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.warn("AI extraction failed via {} after {} ms: {}. Falling back to heuristics.",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            logger.error("Unexpected error during AI extraction via {} after {} ms: {}",
                    providerName, elapsed.toMillis(), e.getMessage());
            return null;
        }
    }

    /**
     * Builds a type-aware extraction prompt for the AI provider.
     * Each diagram type gets a prompt tailored to its semantics.
     */
    private String buildExtractionPrompt(String text, DiagramType diagramType) {
        if (diagramType == null) {
            return buildClassPrompt(text);
        }
        return switch (diagramType) {
            case CLASS, OBJECT          -> buildClassPrompt(text);
            case SEQUENCE, COLLABORATION -> buildSequencePrompt(text);
            case ER                     -> buildErPrompt(text);
            case USE_CASE               -> buildUseCasePrompt(text);
            case ACTIVITY               -> buildActivityPrompt(text);
            case STATE                  -> buildStatePrompt(text);
            case COMPONENT, DEPLOYMENT,
                 MICROSERVICES          -> buildComponentPrompt(text);
        };
    }

    private String buildClassPrompt(String text) {
        return """
                You are a UML class-diagram modeling expert.
                Extract classes, their attributes (with visibility and type), methods
                (with visibility, return type, and parameters), and relationships from
                the following description.

                Relationship types: inheritance, realization, composition, aggregation,
                association, dependency.
                For inheritance: source = child class, target = parent class.
                Include UML multiplicity where inferable ("1", "0..1", "1..*", "0..*", "*").

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "Student", "kind": "class",
                      "attributes": ["-String name"], "methods": ["+String getName()"] }
                  ],
                  "relationships": [
                    { "source": "Student", "target": "Person", "type": "inheritance",
                      "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildSequencePrompt(String text) {
        return """
                You are a UML sequence-diagram modeling expert.
                Extract actors/participants and the ordered messages between them from
                the following description. Each message has a sender, receiver, and label.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "User",    "kind": "actor",       "attributes": [], "methods": [] },
                    { "name": "AuthService", "kind": "component", "attributes": [], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "User", "target": "AuthService", "type": "message",
                      "label": "login(username, password)", "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildErPrompt(String text) {
        return """
                You are a database modeling expert.
                Extract entities (tables), their attributes (columns with type), and
                relationships (foreign-key associations with cardinality) from the
                following description.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "Order", "kind": "entity",
                      "attributes": ["id INT PK", "total DECIMAL"], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "Order", "target": "Customer", "type": "association",
                      "srcMultiplicity": "0..*", "tgtMultiplicity": "1" }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildUseCasePrompt(String text) {
        return """
                You are a UML use-case diagram modeling expert.
                Extract actors and use cases, plus any include/extend relationships
                from the following description.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "Customer", "kind": "actor",    "attributes": [], "methods": [] },
                    { "name": "PlaceOrder","kind": "usecase", "attributes": [], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "Customer", "target": "PlaceOrder", "type": "association",
                      "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildActivityPrompt(String text) {
        return """
                You are a UML activity-diagram modeling expert.
                Extract activities (actions/steps), decision points, and the flow
                (transitions) between them from the following description.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "ValidateInput", "kind": "activity", "attributes": [], "methods": [] },
                    { "name": "InputValid?",   "kind": "decision", "attributes": [], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "ValidateInput", "target": "InputValid?", "type": "transition",
                      "label": "", "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildStatePrompt(String text) {
        return """
                You are a UML state-diagram modeling expert.
                Extract states and the transitions (with trigger/guard/action) between
                them from the following description.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "Idle",       "kind": "state", "attributes": [], "methods": [] },
                    { "name": "Processing", "kind": "state", "attributes": [], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "Idle", "target": "Processing", "type": "transition",
                      "label": "start", "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    private String buildComponentPrompt(String text) {
        return """
                You are a UML component/deployment/microservices diagram modeling expert.
                Extract components (services, nodes, containers) and the dependencies or
                communication links between them from the following description.

                Return ONLY valid JSON:
                {
                  "entities": [
                    { "name": "Frontend",  "kind": "component", "attributes": [], "methods": [] },
                    { "name": "Backend",   "kind": "component", "attributes": [], "methods": [] }
                  ],
                  "relationships": [
                    { "source": "Frontend", "target": "Backend", "type": "dependency",
                      "label": "REST", "srcMultiplicity": null, "tgtMultiplicity": null }
                  ]
                }

                Text:
                """ + text;
    }

    /**
     * Parses the JSON response from the AI provider into a SemanticModel.
     *
     * @param response the JSON response string
     * @return the parsed SemanticModel, or null if parsing fails
     */
    private SemanticModel parseExtractionResponse(String response) {
        if (response == null || response.isBlank()) {
            logger.warn("AI returned empty response for extraction");
            return null;
        }

        logger.debug("Parsing AI extraction response (length: {} chars)", response.length());

        try {
            SemanticModel model = new SemanticModel();

            // Parse entities
            List<EntityNode> entities = parseEntitiesFromJson(response);
            if (entities.isEmpty()) {
                logger.warn("AI response contained no entities");
                return null;
            }
            entities.forEach(model::addEntity);

            // Parse relationships
            List<Relationship> relationships = parseRelationshipsFromJson(response);
            relationships.forEach(model::addRelationship);

            logger.debug("Parsed semantic model: {} entities, {} relationships",
                    model.getEntities().size(), model.getRelationships().size());
            return model;

        } catch (Exception e) {
            logger.error("Failed to parse AI extraction response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses entity objects from the JSON response.
     *
     * @param json the JSON response
     * @return a list of EntityNode objects
     */
    private List<EntityNode> parseEntitiesFromJson(String json) {
        List<EntityNode> entities = new ArrayList<>();

        // Find "entities" array
        int entitiesStart = json.indexOf("\"entities\"");
        if (entitiesStart == -1) {
            return entities;
        }

        int arrayStart = json.indexOf("[", entitiesStart);
        int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
        if (arrayStart == -1 || arrayEnd == -1) {
            return entities;
        }

        String entitiesArray = json.substring(arrayStart + 1, arrayEnd);

        // Parse each entity object
        int objStart = 0;
        while ((objStart = entitiesArray.indexOf("{", objStart)) != -1) {
            int objEnd = findMatchingBracket(entitiesArray, objStart, '{', '}');
            if (objEnd == -1) break;

            String entityObj = entitiesArray.substring(objStart, objEnd + 1);

            String name = extractJsonStringValue(entityObj, "name");
            List<String> attributes = extractJsonStringArray(entityObj, "attributes");
            List<String> methods = extractJsonStringArray(entityObj, "methods");

            if (name != null && !name.isBlank()) {
                List<String> allMembers = new ArrayList<>(attributes != null ? attributes : Collections.emptyList());
                if (methods != null) allMembers.addAll(methods);
                entities.add(new EntityNode(name, allMembers));
            }

            objStart = objEnd + 1;
        }

        return entities;
    }

    /**
     * Parses relationship objects from the JSON response.
     *
     * @param json the JSON response
     * @return a list of Relationship objects
     */
    private List<Relationship> parseRelationshipsFromJson(String json) {
        List<Relationship> relationships = new ArrayList<>();

        // Find "relationships" array
        int relsStart = json.indexOf("\"relationships\"");
        if (relsStart == -1) {
            return relationships;
        }

        int arrayStart = json.indexOf("[", relsStart);
        int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
        if (arrayStart == -1 || arrayEnd == -1) {
            return relationships;
        }

        String relsArray = json.substring(arrayStart + 1, arrayEnd);

        // Parse each relationship object
        int objStart = 0;
        while ((objStart = relsArray.indexOf("{", objStart)) != -1) {
            int objEnd = findMatchingBracket(relsArray, objStart, '{', '}');
            if (objEnd == -1) break;

            String relObj = relsArray.substring(objStart, objEnd + 1);

            String source = extractJsonStringValue(relObj, "source");
            String target = extractJsonStringValue(relObj, "target");
            String type = extractJsonStringValue(relObj, "type");
            String srcMult = extractJsonStringValue(relObj, "srcMultiplicity");
            String tgtMult = extractJsonStringValue(relObj, "tgtMultiplicity");

            if (source != null && target != null && !source.isBlank() && !target.isBlank()) {
                relationships.add(new Relationship(source, target, type != null ? type : "association",
                        srcMult, tgtMult));
            }

            objStart = objEnd + 1;
        }

        return relationships;
    }

    /**
     * Extracts a string value from a JSON object for the given key.
     *
     * @param json the JSON object string
     * @param key the key to extract
     * @return the string value, or null if not found
     */
    private String extractJsonStringValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;

        // Skip whitespace and find opening quote
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }

        if (valueStart >= json.length() || json.charAt(valueStart) != '"') return null;
        valueStart++; // skip opening quote

        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd == -1) return null;

        return json.substring(valueStart, valueEnd);
    }

    /**
     * Extracts a string array from a JSON object for the given key.
     *
     * @param json the JSON object string
     * @param key the key to extract
     * @return the list of strings, or empty list if not found
     */
    private List<String> extractJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return result;

        int arrayStart = json.indexOf("[", keyIndex);
        int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
        if (arrayStart == -1 || arrayEnd == -1) return result;

        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        // Extract each quoted string
        int quoteStart = 0;
        while ((quoteStart = arrayContent.indexOf("\"", quoteStart)) != -1) {
            int quoteEnd = arrayContent.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) break;
            String value = arrayContent.substring(quoteStart + 1, quoteEnd);
            if (!value.isBlank()) {
                result.add(value);
            }
            quoteStart = quoteEnd + 1;
        }

        return result;
    }

    /**
     * Finds the matching closing bracket for an opening bracket.
     *
     * @param json the JSON string
     * @param startIndex the index of the opening bracket
     * @param open the opening bracket character
     * @param close the closing bracket character
     * @return the index of the matching closing bracket, or -1
     */
    private int findMatchingBracket(String json, int startIndex, char open, char close) {
        if (startIndex == -1 || startIndex >= json.length()) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extracts semantic model using NLP heuristics as fallback.
     *
     * @param text the input text
     * @return the extracted SemanticModel
     */
    /** Words treated as actor names (external users). */
    private static final Set<String> ACTOR_NAMES = Set.of(
            "user", "admin", "administrator", "client", "customer", "operator",
            "guest", "member", "visitor", "student", "professor", "teacher",
            "parent", "registrar", "moderator", "instructor", "librarian",
            "bank customer", "maintenance technician", "billing system",
            "payment gateway", "bank server", "cash dispenser", "search engine"
    );

    private SemanticModel extractByHeuristics(String text) {
        SemanticModel model = new SemanticModel();

        boolean useCaseLike = isUseCaseLike(text);
        boolean sequenceLike = !useCaseLike && isSequenceLike(text);

        if (sequenceLike) {
            // Sequence-specific extraction: ordered messages between named participants
            List<EntityNode> entities = extractSequenceParticipants(text);
            entities.forEach(model::addEntity);
            logger.info("Extracted {} sequence participants", entities.size());

            List<Relationship> messages = extractSequenceMessages(text);
            messages.forEach(model::addRelationship);
            logger.info("Extracted {} sequence messages", messages.size());

            // Extract method-call-style labels as actions for fallback rendering
            List<String> actions = extractSequenceActions(text);
            actions.forEach(model::addAction);
            logger.info("Extracted {} sequence actions", actions.size());
        } else {
            // Extract entities and enrich actor names for use-case wording.
            List<EntityNode> entities = extractEntities(text);
            if (useCaseLike) {
                mergeEntities(entities, extractUseCaseActors(text));
            }
            ensureUserActor(entities, text);
            entities.forEach(model::addEntity);
            logger.info("Extracted {} entities", entities.size());

            // Extract verb-phrase actions. Use-case descriptions need full goals, not bare verbs.
            List<String> actions = useCaseLike ? extractUseCaseActions(text) : extractVerbPhraseActions(text);
            if (actions.isEmpty()) actions = extractActions(text);
            actions.forEach(model::addAction);
            logger.info("Extracted {} actions", actions.size());

            // Extract relationships after actions so use-case include/extend targets are known.
            List<Relationship> relationships = useCaseLike
                    ? extractUseCaseRelationships(text, entities, actions)
                    : extractRelationships(text, entities);
            relationships.forEach(model::addRelationship);
            logger.info("Extracted {} relationships", relationships.size());
        }

        logger.debug("Semantic extraction complete: {}", model);
        return model;
    }

    private boolean isUseCaseLike(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("use case")
                || lower.contains("actor")
                || lower.contains("stakeholder")
                || lower.contains(" can ")
                || lower.contains(" must ")
                || lower.contains(" might ")
                || lower.contains("<<include>>")
                || lower.contains("<<extend>>");
    }

    // ─── Sequence detection and extraction ────────────────────────────────────

    /**
     * Known multi-word participant names. Keys are lowercase phrases ordered longest-first to
     * prevent partial matches (e.g. "transaction server" before "server"). Values are canonical
     * display names used in the SemanticModel and in PlantUML declarations.
     */
    private static final Map<String, String> SEQ_MULTI_WORD_PARTICIPANTS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("transaction server", "Transaction Server");
        m.put("web server",         "Web Server");
        m.put("sql server",         "SQL Server");
        m.put("app server",         "App Server");
        m.put("auth server",        "Auth Server");
        m.put("mail server",        "Mail Server");
        m.put("file server",        "File Server");
        m.put("cache server",       "Cache Server");
        m.put("payment gateway",    "Payment Gateway");
        m.put("cash dispenser",     "Cash Dispenser");
        m.put("bank server",        "Bank Server");
        SEQ_MULTI_WORD_PARTICIPANTS = Collections.unmodifiableMap(m);
    }

    /** Reverse map: spaces-stripped token → canonical display name. e.g. "WebServer" → "Web Server". */
    private static final Map<String, String> SEQ_TOKEN_TO_DISPLAY;
    static {
        Map<String, String> m = new HashMap<>();
        SEQ_MULTI_WORD_PARTICIPANTS.forEach((phrase, display) ->
                m.put(display.replace(" ", ""), display));
        SEQ_TOKEN_TO_DISPLAY = Map.copyOf(m);
    }

    /** Matches "If/When [condition], [message part]". Group 1: condition, Group 2: message text. */
    private static final Pattern SEQ_IF_SENTENCE = Pattern.compile(
            "(?i)^(?:if|when)\\b\\s*([^,.]+?)[,.]\\s+(.+)$");

    /** Matches "Otherwise/Else [if [condition],] [message part]". Group 1: optional else-condition, Group 2: message text. */
    private static final Pattern SEQ_ELSE_SENTENCE = Pattern.compile(
            "(?i)^(?:otherwise|else(?:\\s+if)?)[,.]?\\s*(?:([^,.]+?)[,.]\\s+)?(.+)$");

    /** Detects parallel execution phrases within a sentence. */
    private static final Pattern SEQ_PAR_INDICATOR = Pattern.compile(
            "(?i)\\b(in\\s+parallel|simultaneously|at\\s+the\\s+same\\s+time|concurrently|both\\s+(?:servers?|services?|systems?|components?))\\b");

    /**
     * Matches "Sender sends/calls/... [label] to/for Receiver".
     * Group 1: sender, Group 2: optional message label (before 'to'), Group 3: receiver.
     */
    private static final Pattern SEQ_CALL_PATTERN = Pattern.compile(
            "(?i)\\b([A-Za-z][A-Za-z0-9_]*)\\s+" +
            "(?:calls?|sends?|requests?|invokes?|asks?|notifies?|triggers?|queries?|checks?|verifies?|updates?|releases?|dispenses?)\\s+" +
            "(?:([A-Za-z][A-Za-z0-9_()]*(?:\\(\\))?)\\s+)??" +   // optional label (lazy)
            "(?:to|for)\\s+([A-Za-z][A-Za-z0-9_]*)");

    /**
     * Matches chained send targets: "and [label()] to Receiver".
     * Used after a primary SEQ_CALL_PATTERN match to capture additional targets from the same sender.
     * Group 1: optional label, Group 2: receiver.
     */
    private static final Pattern SEQ_CHAINED_CALL_PATTERN = Pattern.compile(
            "(?i)\\band\\s+" +
            "(?:([A-Za-z][A-Za-z0-9_()]*(?:\\(\\))?)\\s+)??" +   // optional label (lazy)
            "(?:to|for)\\s+([A-Za-z][A-Za-z0-9_]*)");

    /**
     * Matches "Sender returns/responds/replies/confirms/acknowledges/approves/rejects [label] to Receiver".
     * Group 1: sender, Group 2: optional label, Group 3: receiver.
     */
    private static final Pattern SEQ_RETURN_PATTERN = Pattern.compile(
            "(?i)\\b([A-Za-z][A-Za-z0-9_]*)\\s+" +
            "(?:returns?|responds?|replies?|sends?\\s+back|confirms?|acknowledges?|approves?|rejects?|denies?)\\s+" +
            "(?:([A-Za-z][A-Za-z0-9_()]*(?:\\(\\))?)\\s+)??" +   // optional label (lazy)
            "(?:to)\\s+([A-Za-z][A-Za-z0-9_]*)");

    private static final Pattern SEQ_METHOD_LABEL_PATTERN = Pattern.compile(
            "\\b([a-z][a-zA-Z0-9]*)\\(\\)");

    /**
     * Returns true if the text describes a sequence of messages between participants.
     * Looks for call/return verbs, participant patterns, and method-call syntax.
     */
    private boolean isSequenceLike(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasCallVerb = lower.matches("(?s).*\\b(sends?|calls?|requests?|invokes?|asks?|notifies?|triggers?|queries?|checks?|verifies?|updates?|releases?|dispenses?)\\b.*");
        boolean hasReturnVerb = lower.matches("(?s).*\\b(returns?|responds?|replies?|confirms?|acknowledges?|approves?|rejects?|denies?)\\b.*");
        boolean hasMethodCall = SEQ_METHOD_LABEL_PATTERN.matcher(text).find();
        boolean hasToPrep = lower.matches("(?s).*(sends?|calls?|requests?)\\s+(to|a message to)\\b.*");
        boolean hasConditional = Pattern.compile("(?i)\\b(if|when)\\s+[^,]+,").matcher(text).find();
        boolean hasParallel = SEQ_PAR_INDICATOR.matcher(text).find();
        return (hasCallVerb && (hasReturnVerb || hasToPrep || hasMethodCall || hasConditional || hasParallel))
                || (hasCallVerb && hasReturnVerb);
    }

    /**
     * Normalises a raw condition string to use standard comparison operators.
     * e.g. "amount exceeds 1000" → "amount > 1000", "balance is less than limit" → "balance < limit"
     */
    private String normalizeConditionText(String condition) {
        if (condition == null || condition.isBlank()) return "condition";
        return condition
                // Comparison operators
                .replaceAll("(?i)\\bis\\s+greater\\s+than\\s+or\\s+equal\\s+to\\b", ">=")
                .replaceAll("(?i)\\bgreater\\s+than\\s+or\\s+equal\\s+to\\b", ">=")
                .replaceAll("(?i)\\bis\\s+greater\\s+than\\b", ">")
                .replaceAll("(?i)\\bgreater\\s+than\\b", ">")
                .replaceAll("(?i)\\bexceeds?\\b", ">")
                .replaceAll("(?i)\\bis\\s+less\\s+than\\s+or\\s+equal\\s+to\\b", "<=")
                .replaceAll("(?i)\\bless\\s+than\\s+or\\s+equal\\s+to\\b", "<=")
                .replaceAll("(?i)\\bis\\s+less\\s+than\\b", "<")
                .replaceAll("(?i)\\bless\\s+than\\b", "<")
                .replaceAll("(?i)\\bis\\s+equal\\s+to\\b", "==")
                .replaceAll("(?i)\\bequals?\\b", "==")
                // ATM / approval-flow conditions
                .replaceAll("(?i)\\b(?:bank\\s+)?(?:approval|confirmation)\\s+(?:is\\s+)?required\\b", "confirmation required")
                .replaceAll("(?i)\\brequires?\\s+(?:bank\\s+)?(?:approval|confirmation)\\b", "confirmation required")
                .replaceAll("(?i)\\bbank\\s+approv(?:al|es?|ed)\\b", "bank approved")
                .replaceAll("(?i)\\bfunds?\\s+(?:are\\s+)?insufficient\\b", "insufficient funds")
                .replaceAll("(?i)\\bfunds?\\s+(?:are\\s+)?sufficient\\b", "sufficient funds")
                .replaceAll("(?i)\\bbalance\\s+(?:is\\s+)?insufficient\\b", "balance insufficient")
                .replaceAll("(?i)\\bbalance\\s+(?:is\\s+)?sufficient\\b", "balance sufficient")
                // Strip leading articles for cleaner labels
                .replaceAll("(?i)^(the|a|an)\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Replaces known multi-word participant names with single PascalCase tokens before pattern matching. */
    private String tokenizeParticipantNames(String text) {
        String result = text;
        for (Map.Entry<String, String> e : SEQ_MULTI_WORD_PARTICIPANTS.entrySet()) {
            String token = e.getValue().replace(" ", "");
            result = result.replaceAll("(?i)\\b" + Pattern.quote(e.getKey()) + "\\b", token);
        }
        return result;
    }

    /** Restores a PascalCase token to its canonical display name, or returns the input unchanged. */
    private String detokenizeParticipantName(String token) {
        return SEQ_TOKEN_TO_DISPLAY.getOrDefault(token, token);
    }

    /**
     * Extracts ordered sequence participants from text.
     * Identifies actors (users, roles) and system components (servers, services, databases).
     */
    private List<EntityNode> extractSequenceParticipants(String text) {
        Set<String> seen = new LinkedHashSet<>();
        List<EntityNode> result = new ArrayList<>();
        String tokenized = tokenizeParticipantNames(text);

        // Extract participants from call/return patterns (preserves order of appearance)
        Matcher callMatcher = SEQ_CALL_PATTERN.matcher(tokenized);
        while (callMatcher.find()) {
            addSequenceParticipant(detokenizeParticipantName(callMatcher.group(1).trim()), seen, result);  // sender
            addSequenceParticipant(detokenizeParticipantName(callMatcher.group(3).trim()), seen, result);  // receiver
        }
        Matcher returnMatcher = SEQ_RETURN_PATTERN.matcher(tokenized);
        while (returnMatcher.find()) {
            addSequenceParticipant(detokenizeParticipantName(returnMatcher.group(1).trim()), seen, result);  // sender
            addSequenceParticipant(detokenizeParticipantName(returnMatcher.group(3).trim()), seen, result);  // receiver
        }

        // Fallback: collect PascalCase words that look like component names
        if (result.isEmpty()) {
            Matcher pascal = PASCAL_CASE_PATTERN.matcher(tokenized);
            while (pascal.find()) {
                String word = pascal.group(1);
                if (!EXCLUDED_WORDS.contains(word) && !IMPERATIVE_VERBS.contains(word)) {
                    addSequenceParticipant(detokenizeParticipantName(word), seen, result);
                }
            }
        }

        // Ensure at least a generic User participant is present
        if (result.stream().noneMatch(e -> isActorName(e.getName()))) {
            ensureUserActor(result, text);
        }

        return result;
    }

    private void addSequenceParticipant(String name, Set<String> seen, List<EntityNode> result) {
        if (name == null || name.isBlank()) return;
        // Skip internal ALT fragment marker names
        if (name.startsWith("__")) return;
        // Clean trailing noise words
        String cleaned = name.replaceAll("(?i)\\b(a|an|the|its|this|that)\\b.*$", "").trim();
        if (cleaned.isBlank() || cleaned.length() < 2) return;
        // Skip common English stop-words and diagram instruction words
        if (EXCLUDED_WORDS.contains(cleaned) || IMPERATIVE_VERBS.contains(cleaned)) return;
        // Skip pure verbs and prepositions
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (Set.of("to", "for", "with", "back", "a", "an", "the", "request", "response",
                   "message", "result", "data", "it", "its", "them").contains(lower)) return;
        String key = lower.replaceAll("\\s+", " ");
        if (seen.add(key)) {
            result.add(new EntityNode(cleaned, Collections.emptyList()));
        }
    }

    /**
     * Extracts ordered sequence messages as Relationship objects, including ALT and PAR fragment markers.
     * Forward calls use type "sends"; return messages use type "returns".
     * Conditional sentences (starting with "if/when") produce alt_start/alt_else/alt_end markers.
     * Parallel sentences (containing "in parallel", "simultaneously", etc.) produce par_start/par_else/par_end markers.
     * The message label is stored in srcMultiplicity.
     */
    private List<Relationship> extractSequenceMessages(String text) {
        List<Relationship> messages = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?\\n])\\s*");
        boolean inAlt = false;
        boolean inPar = false;

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            // "If/When [condition], [message part]" → open a new alt block
            Matcher ifMatcher = SEQ_IF_SENTENCE.matcher(trimmed);
            if (ifMatcher.matches()) {
                if (inPar) {
                    messages.add(new Relationship("__PAR__", "__END__", "par_end", null, null));
                    inPar = false;
                }
                if (inAlt) {
                    messages.add(new Relationship("__ALT__", "__END__", "alt_end", null, null));
                }
                String condition = normalizeConditionText(ifMatcher.group(1).trim());
                messages.add(new Relationship("__ALT__", "__START__", "alt_start", condition, null));
                inAlt = true;
                extractMessagesFromText(ifMatcher.group(2).trim(), messages);
                continue;
            }

            // "Otherwise/Else [condition], [message part]" → else branch inside open alt
            Matcher elseMatcher = SEQ_ELSE_SENTENCE.matcher(trimmed);
            if (elseMatcher.matches() && inAlt) {
                String elseCondition = elseMatcher.group(1) != null
                        ? normalizeConditionText(elseMatcher.group(1).trim()) : "";
                messages.add(new Relationship("__ALT__", "__ELSE__", "alt_else", elseCondition, null));
                extractMessagesFromText(elseMatcher.group(2).trim(), messages);
                continue;
            }

            // Parallel sentence → open or continue a par block
            if (SEQ_PAR_INDICATOR.matcher(trimmed).find()) {
                if (inAlt) {
                    messages.add(new Relationship("__ALT__", "__END__", "alt_end", null, null));
                    inAlt = false;
                }
                if (!inPar) {
                    messages.add(new Relationship("__PAR__", "__START__", "par_start", null, null));
                    inPar = true;
                } else {
                    messages.add(new Relationship("__PAR__", "__ELSE__", "par_else", null, null));
                }
                extractMessagesFromText(trimmed, messages);
                continue;
            }

            // Regular sentence:
            // - If inside an ALT block, keep it open (multi-sentence alt body support).
            //   The block closes only when a new If/When opens, or at end of text.
            // - If inside a PAR block, close it (PAR body is bounded by par-indicator sentences).
            if (inPar) {
                messages.add(new Relationship("__PAR__", "__END__", "par_end", null, null));
                inPar = false;
            }
            extractMessagesFromText(trimmed, messages);
        }

        if (inAlt) {
            messages.add(new Relationship("__ALT__", "__END__", "alt_end", null, null));
        }
        if (inPar) {
            messages.add(new Relationship("__PAR__", "__END__", "par_end", null, null));
        }

        return messages;
    }

    /** Extracts call and return messages from a fragment of text into the messages list. */
    private void extractMessagesFromText(String text, List<Relationship> messages) {
        String tokenized = tokenizeParticipantNames(text);
        Matcher callMatcher = SEQ_CALL_PATTERN.matcher(tokenized);
        while (callMatcher.find()) {
            String source = detokenizeParticipantName(cleanParticipantName(callMatcher.group(1).trim()));
            String label  = normalizeSequenceLabel(
                    callMatcher.group(2) != null ? callMatcher.group(2).trim() : "", "sends");
            String target = detokenizeParticipantName(cleanParticipantName(callMatcher.group(3).trim()));
            if (!source.isBlank() && !target.isBlank() && !source.equalsIgnoreCase(target)) {
                messages.add(new Relationship(source, target, "sends", label, null));
            }
            // Detect chained sends: "... and [label] to OtherTarget"
            // The region starts from callMatcher.end(); we skip the strict start-position
            // guard so a leading space between the primary match and "and" is tolerated.
            int chainStart = callMatcher.end();
            Matcher chainMatcher = SEQ_CHAINED_CALL_PATTERN.matcher(tokenized);
            chainMatcher.region(chainStart, tokenized.length());
            while (chainMatcher.find()) {
                String chainLabel  = normalizeSequenceLabel(
                        chainMatcher.group(1) != null ? chainMatcher.group(1).trim() : "", "sends");
                String chainTarget = detokenizeParticipantName(cleanParticipantName(chainMatcher.group(2).trim()));
                if (!source.isBlank() && !chainTarget.isBlank() && !source.equalsIgnoreCase(chainTarget)) {
                    messages.add(new Relationship(source, chainTarget, "sends", chainLabel, null));
                }
                chainStart = chainMatcher.end();
                chainMatcher.region(chainStart, tokenized.length());
            }
        }
        Matcher returnMatcher = SEQ_RETURN_PATTERN.matcher(tokenized);
        while (returnMatcher.find()) {
            String source = detokenizeParticipantName(cleanParticipantName(returnMatcher.group(1).trim()));
            String label  = normalizeSequenceLabel(
                    returnMatcher.group(2) != null ? returnMatcher.group(2).trim() : "", "returns");
            String target = detokenizeParticipantName(cleanParticipantName(returnMatcher.group(3).trim()));
            if (!source.isBlank() && !target.isBlank() && !source.equalsIgnoreCase(target)) {
                messages.add(new Relationship(source, target, "returns", label, null));
            }
        }
    }

    private String normalizeSequenceLabel(String raw, String verb) {
        if (raw == null || raw.isBlank()) {
            return verb + "Message()";
        }
        // If already a method call, keep it
        if (raw.endsWith("()")) {
            return raw;
        }
        // If the raw label is a camelCase/PascalCase identifier, append ()
        if (raw.matches("[a-zA-Z][a-zA-Z0-9]*")) {
            return Character.toLowerCase(raw.charAt(0)) + raw.substring(1) + "()";
        }
        // Multi-word: camelCase compress + append ()
        String[] words = raw.split("\\s+");
        if (words.length <= 3) {
            StringBuilder sb = new StringBuilder(words[0].toLowerCase(Locale.ROOT));
            for (int i = 1; i < words.length; i++) {
                String w = words[i];
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            return sb.toString() + "()";
        }
        return raw;
    }

    private String cleanParticipantName(String name) {
        if (name == null) return "";
        return name.replaceAll("(?i)\\b(a|an|the|its|this|that)\\b.*$", "").trim();
    }

    /**
     * Extracts method-call-style labels from the text (e.g., "searchMessage()") as actions.
     * These serve as a fallback if no structured message relationships are found.
     */
    private List<String> extractSequenceActions(String text) {
        List<String> actions = new ArrayList<>();
        Matcher m = SEQ_METHOD_LABEL_PATTERN.matcher(text);
        while (m.find()) {
            actions.add(m.group(1) + "()");
        }
        return actions;
    }

    private void mergeEntities(List<EntityNode> entities, List<String> names) {
        Set<String> existing = new LinkedHashSet<>();
        for (EntityNode entity : entities) {
            existing.add(entity.getName().toLowerCase(Locale.ROOT));
        }
        for (String name : names) {
            if (name != null && !name.isBlank() && existing.add(name.toLowerCase(Locale.ROOT))) {
                entities.add(new EntityNode(name, Collections.emptyList()));
            }
        }
    }

    /**
     * Ensures a User actor appears as the first entity in the list.
     * If the text contains an actor word (user, admin, …) it is moved/added to position 0.
     */
    private void ensureUserActor(List<EntityNode> entities, String text) {
        // Check if any actor-named entity was already extracted
        for (int i = 0; i < entities.size(); i++) {
            if (ACTOR_NAMES.contains(entities.get(i).getName().toLowerCase())) {
                EntityNode actor = entities.remove(i);
                entities.add(0, actor);
                return;
            }
        }
        // Check whether the raw text mentions an actor keyword
        String lower = text.toLowerCase();
        for (String actor : ACTOR_NAMES) {
            if (containsPhrase(lower, actor)) {
                String actorName = toTitleCase(actor);
                entities.add(0, new EntityNode(actorName, Collections.emptyList()));
                return;
            }
        }
    }

    private List<String> extractUseCaseActors(String text) {
        Set<String> actors = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);

        for (String actor : ACTOR_NAMES) {
            if (containsPhrase(lower, actor)) {
                actors.add(toTitleCase(actor));
            }
        }

        Pattern actorListPattern = Pattern.compile(
                "\\b(?:actors?|stakeholders?)\\s+(?:include|includes|are|is)\\s+([^.!?]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = actorListPattern.matcher(text);
        while (matcher.find()) {
            for (String part : splitList(matcher.group(1))) {
                String actor = cleanUseCasePhrase(part);
                if (looksLikeActor(actor)) actors.add(toTitleCase(actor));
            }
        }

        return new ArrayList<>(actors);
    }

    private boolean looksLikeActor(String phrase) {
        if (phrase == null || phrase.isBlank()) return false;
        String normalized = phrase.toLowerCase(Locale.ROOT);
        return ACTOR_NAMES.contains(normalized)
                || normalized.endsWith(" system")
                || normalized.endsWith(" gateway")
                || normalized.endsWith(" server")
                || normalized.endsWith(" dispenser")
                || normalized.endsWith(" engine")
                || normalized.endsWith(" technician");
    }

    private List<String> extractUseCaseActions(String text) {
        Set<String> actions = new LinkedHashSet<>();

        Pattern canPattern = Pattern.compile(
                "\\b(?:the\\s+)?([A-Za-z][A-Za-z\\s]*(?:System|Gateway|Server|Dispenser|Engine|Technician|Customer|Student|Administrator|User|Guest|Professor|Teacher|Parent|Registrar|Moderator|Visitor|Librarian)?)\\s+can\\s+([^.!?]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher canMatcher = canPattern.matcher(text);
        while (canMatcher.find()) {
            for (String candidate : splitList(canMatcher.group(2))) {
                addUseCaseAction(actions, candidate);
            }
        }

        Pattern goalPattern = Pattern.compile(
                "\\b(?:goals?|use cases?)\\s+(?:include|includes|are)\\s+([^.!?]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher goalMatcher = goalPattern.matcher(text);
        while (goalMatcher.find()) {
            for (String candidate : splitList(goalMatcher.group(1))) {
                addUseCaseAction(actions, candidate);
            }
        }

        Pattern verbPhrasePattern = Pattern.compile(
                "\\b(" + verbAlternation() + ")(?:s|ed|ing)?\\b(?:\\s+(?:an?\\s+|the\\s+)?([a-z][a-z0-9_-]*(?:\\s+[a-z][a-z0-9_-]*){0,3}))?",
                Pattern.CASE_INSENSITIVE);
        Matcher verbMatcher = verbPhrasePattern.matcher(text);
        while (verbMatcher.find()) {
            String phrase = verbMatcher.group(1);
            if (verbMatcher.group(2) != null && !verbMatcher.group(2).isBlank()) {
                phrase += " " + trimAtBoundary(verbMatcher.group(2));
            }
            addUseCaseAction(actions, phrase);
        }

        return new ArrayList<>(actions);
    }

    private void addUseCaseAction(Set<String> actions, String candidate) {
        String action = cleanUseCasePhrase(candidate);
        if (action.isBlank()) return;
        String lower = action.toLowerCase(Locale.ROOT);
        boolean startsWithAction = USE_CASE_ACTION_VERBS.stream()
                .anyMatch(verb -> lower.equals(verb) || lower.startsWith(verb + " "));
        if (startsWithAction && !looksLikeActor(action) && !isDiagramInstruction(action)) {
            actions.add(normalizeUseCaseAction(action));
        }
    }

    private List<Relationship> extractUseCaseRelationships(String text, List<EntityNode> entities, List<String> actions) {
        Set<Relationship> relationships = new LinkedHashSet<>();
        Set<String> actionKeys = new LinkedHashSet<>();
        for (String action : actions) actionKeys.add(normalizeKey(action));

        extractActorUseCaseRelationships(text, relationships);
        extractIncludeExtendRelationships(text, "include", relationships, actionKeys);
        extractIncludeExtendRelationships(text, "extend", relationships, actionKeys);
        extractModalUseCaseRelationships(text, "include", relationships, actionKeys);
        extractModalUseCaseRelationships(text, "extend", relationships, actionKeys);

        if (relationships.stream().noneMatch(r -> "association".equalsIgnoreCase(r.getType()))) {
            List<String> actors = entities.stream()
                    .map(EntityNode::getName)
                    .filter(this::isActorName)
                    .toList();
            String fallbackActor = actors.isEmpty() && !entities.isEmpty() ? entities.get(0).getName() : null;
            for (String action : actions) {
                String actor = !actors.isEmpty() ? actors.get(0) : fallbackActor;
                if (actor != null && !actor.equalsIgnoreCase(action)) {
                    relationships.add(new Relationship(actor, normalizeUseCaseAction(action), "association"));
                }
            }
        }

        return new ArrayList<>(relationships);
    }

    private void extractActorUseCaseRelationships(String text, Set<Relationship> relationships) {
        Pattern canPattern = Pattern.compile(
                "\\b(?:the\\s+)?([A-Za-z][A-Za-z\\s]*?)\\s+can\\s+([^.!?]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = canPattern.matcher(text);
        while (matcher.find()) {
            String actor = cleanUseCasePhrase(matcher.group(1));
            if (!looksLikeActor(actor)) continue;
            actor = toTitleCase(actor);
            for (String candidate : splitList(matcher.group(2))) {
                String action = cleanUseCasePhrase(candidate);
                if (!action.isBlank()) {
                    relationships.add(new Relationship(actor, normalizeUseCaseAction(action), "association"));
                }
            }
        }
    }

    private void extractIncludeExtendRelationships(String text, String relationshipType,
                                                   Set<Relationship> relationships, Set<String> actionKeys) {
        String verb = "include".equals(relationshipType) ? "includes?" : "extends?";
        Pattern pattern = Pattern.compile(
                "([^.!?]+?)\\s+(?:(?:<<" + relationshipType + ">>)|" + verb + ")\\s+([^.!?]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String source = extractTrailingUseCasePhrase(matcher.group(1), actionKeys);
            if (source.isBlank() || source.toLowerCase(Locale.ROOT).contains("actor")) continue;
            for (String rawTarget : splitList(matcher.group(2))) {
                String target = cleanUseCasePhrase(rawTarget);
                target = target.replaceAll("(?i)\\bwhen\\b.*$", "").trim();
                if (!target.isBlank()) {
                    relationships.add(new Relationship(
                            normalizeUseCaseAction(source),
                            normalizeUseCaseAction(target),
                            relationshipType));
                }
            }
        }
    }

    private void extractModalUseCaseRelationships(String text, String relationshipType,
                                                  Set<Relationship> relationships, Set<String> actionKeys) {
        String modalPattern = "include".equals(relationshipType)
                ? "(?:must|needs? to|requires?)"
                : "(?:might|may|can optionally)";

        for (String sentence : text.split("[.!?]")) {
            addModalRelationshipFromToPhrase(sentence, modalPattern, relationshipType, relationships);
            addModalRelationshipFromDirectPhrase(sentence, modalPattern, relationshipType, relationships, actionKeys);
        }
    }

    private void addModalRelationshipFromToPhrase(String sentence, String modalPattern, String relationshipType,
                                                  Set<Relationship> relationships) {
        Pattern pattern = Pattern.compile(
                "\\bto\\s+([^,;]+?)\\s*,?\\s+[^,;]*?\\b" + modalPattern + "\\s+([^,;]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        while (matcher.find()) {
            addUseCaseDependency(
                    matcher.group(1),
                    stripModalTargetNoise(matcher.group(2)),
                    relationshipType,
                    relationships);
        }
    }

    private void addModalRelationshipFromDirectPhrase(String sentence, String modalPattern, String relationshipType,
                                                     Set<Relationship> relationships, Set<String> actionKeys) {
        Pattern pattern = Pattern.compile(
                "(.+?)\\s+\\b" + modalPattern + "\\s+([^,;]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        while (matcher.find()) {
            String source = extractTrailingUseCasePhrase(matcher.group(1), actionKeys);
            addUseCaseDependency(
                    source,
                    stripModalTargetNoise(matcher.group(2)),
                    relationshipType,
                    relationships);
        }
    }

    private void addUseCaseDependency(String source, String target, String relationshipType,
                                      Set<Relationship> relationships) {
        String normalizedSource = normalizeUseCaseAction(source);
        String normalizedTarget = normalizeUseCaseAction(target);
        if (normalizedSource.isBlank() || normalizedTarget.isBlank()) return;
        if (looksLikeActor(normalizedSource) || normalizedSource.contains("actor")) return;
        relationships.add(new Relationship(normalizedSource, normalizedTarget, relationshipType));
    }

    private String stripModalTargetNoise(String target) {
        return cleanUseCasePhrase(target)
                .replaceAll("(?i)\\bfirst\\b", "")
                .replaceAll("(?i)\\bbefore continuing\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractTrailingUseCasePhrase(String text, Set<String> actionKeys) {
        String cleaned = cleanUseCasePhrase(text);
        String best = "";
        for (String key : actionKeys) {
            if (normalizeKey(cleaned).endsWith(key) && key.length() > best.length()) {
                best = key;
            }
        }
        if (!best.isBlank()) {
            return best.replace(' ', '_').replace("_", " ");
        }
        String[] parts = cleaned.split("\\s+");
        int start = Math.max(0, parts.length - 4);
        return String.join(" ", Arrays.copyOfRange(parts, start, parts.length));
    }

    /**
     * Extracts verb+noun phrases as actions, e.g. "request reset", "send email",
     * "validate token", "update password".  These make better sequence-diagram
     * message labels than bare verbs.
     */
    private List<String> extractVerbPhraseActions(String text) {
        List<String> phrases = new ArrayList<>();
        // Match: action-verb followed immediately by a lowercase noun (no caps = not an entity)
        Pattern phrasePattern = Pattern.compile(
                "\\b(" + String.join("|", ACTION_VERBS) + ")(?:s|ed|ing)?\\s+(?:an?\\s+|the\\s+)?([a-z][a-z_-]+)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher m = phrasePattern.matcher(text);
        while (m.find()) {
            String verb = m.group(1).toLowerCase();
            // Normalise verb to base form
            verb = verb.replaceAll("(ing|ed|s)$", "");
            // Re-check after stripping suffix
            String noun = m.group(2).toLowerCase();
            // Skip noise nouns that are just stop-words
            if (noun.length() > 2 && !EXCLUDED_WORDS.stream()
                    .anyMatch(w -> w.equalsIgnoreCase(noun))) {
                phrases.add(verb + " " + noun);
                logger.trace("Extracted phrase action: {} {}", verb, noun);
            }
        }
        return phrases;
    }

    /**
     * Validates the input text.
     *
     * @param text the text to validate
     * @throws IllegalArgumentException if text is null or blank
     */
    private void validateInput(String text) {
        if (text == null || text.isBlank()) {
            logger.error("Extraction failed: input text is null or blank");
            throw new IllegalArgumentException("Input text cannot be null or blank");
        }
    }

    /**
     * Extracts entities from the text by identifying capitalized words.
     *
     * @param text the input text
     * @return a list of extracted EntityNode objects
     */
    private List<EntityNode> extractEntities(String text) {
        Set<String> entityNames = new LinkedHashSet<>();
        Map<String, List<String>> entityAttributes = new HashMap<>();

        // Extract PascalCase words as entities
        Matcher pascalMatcher = PASCAL_CASE_PATTERN.matcher(text);
        while (pascalMatcher.find()) {
            String word = pascalMatcher.group(1);
            if (!EXCLUDED_WORDS.contains(word) && !IMPERATIVE_VERBS.contains(word) && word.length() > 1) {
                entityNames.add(word);
                logger.trace("Found potential entity: {}", word);
            }
        }

        // Also check for capitalized words
        Matcher capitalizedMatcher = CAPITALIZED_WORD_PATTERN.matcher(text);
        while (capitalizedMatcher.find()) {
            String word = capitalizedMatcher.group(1);
            if (!EXCLUDED_WORDS.contains(word) && !IMPERATIVE_VERBS.contains(word) && word.length() > 1) {
                entityNames.add(word);
            }
        }

        // Try to extract attributes by looking for patterns like "Entity with attribute1, attribute2"
        extractAttributesFromText(text, entityNames, entityAttributes);

        // Build entity nodes
        List<EntityNode> entities = new ArrayList<>();
        for (String name : entityNames) {
            List<String> attributes = entityAttributes.getOrDefault(name, Collections.emptyList());
            entities.add(new EntityNode(name, attributes));
        }

        return entities;
    }

    /**
     * Extracts attributes for entities from patterns like "Entity with attribute1, attribute2".
     *
     * @param text             the input text
     * @param entityNames      the set of entity names
     * @param entityAttributes the map to populate with entity attributes
     */
    private void extractAttributesFromText(String text, Set<String> entityNames,
                                           Map<String, List<String>> entityAttributes) {
        // Pattern: "Entity with attr1, attr2" or "Entity has attr1 and attr2"
        Pattern attributePattern = Pattern.compile(
                "([A-Z][a-zA-Z0-9]*)\\s+(?:with|has|having|contains)\\s+([a-z][a-zA-Z0-9,\\s]+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = attributePattern.matcher(text);
        while (matcher.find()) {
            String entityName = matcher.group(1);
            String attributeString = matcher.group(2);

            if (entityNames.contains(entityName)) {
                List<String> attributes = parseAttributeList(attributeString);
                entityAttributes.computeIfAbsent(entityName, k -> new ArrayList<>()).addAll(attributes);
                logger.trace("Extracted attributes for {}: {}", entityName, attributes);
            }
        }
    }

    /**
     * Parses a comma/and-separated list of attributes.
     *
     * @param attributeString the string containing attributes
     * @return a list of individual attributes
     */
    private List<String> parseAttributeList(String attributeString) {
        List<String> attributes = new ArrayList<>();
        String[] parts = attributeString.split("[,]|\\band\\b");

        for (String part : parts) {
            String trimmed = part.trim();
            // Take only the first word (the attribute name)
            String[] words = trimmed.split("\\s+");
            if (words.length > 0 && !words[0].isEmpty()) {
                String attr = words[0].toLowerCase();
                // Filter out relationship keywords
                if (RELATIONSHIP_KEYWORDS.stream().noneMatch(e -> e.getKey().equals(attr)) && attr.length() > 1) {
                    attributes.add(attr);
                }
            }
        }

        return attributes;
    }

    /**
     * Extracts relationships from the text based on keyword patterns.
     *
     * @param text     the input text
     * @param entities the list of extracted entities
     * @return a list of extracted Relationship objects
     */
    private List<Relationship> extractRelationships(String text, List<EntityNode> entities) {
        List<Relationship> relationships = new ArrayList<>();
        Set<String> entityNames = new HashSet<>();
        entities.forEach(e -> entityNames.add(e.getName()));

        String[] sentences = text.split("[.!?]");

        for (String sentence : sentences) {
            List<String> entitiesInSentence = findEntitiesInText(sentence, entityNames);

            if (entitiesInSentence.size() >= 2) {
                // Look for relationship keywords in the sentence (longest-first to prefer specific matches)
                String lower = sentence.toLowerCase();
                for (Map.Entry<String, String> keyword : RELATIONSHIP_KEYWORDS) {
                    if (lower.contains(keyword.getKey())) {
                        String source = entitiesInSentence.get(0);
                        String target = entitiesInSentence.get(1);
                        String type = keyword.getValue();

                        // Attempt to detect multiplicity for both ends
                        String srcMult = detectMultiplicity(sentence, source);
                        String tgtMult = detectMultiplicity(sentence, target);

                        Relationship rel = new Relationship(source, target, type, srcMult, tgtMult);
                        if (!relationships.contains(rel)) {
                            relationships.add(rel);
                            logger.trace("Found relationship: {} -> {} ({}) [{}..{}]",
                                    source, target, type, srcMult, tgtMult);
                        }
                        break;
                    }
                }
            }
        }

        return relationships;
    }

    /**
     * Scans {@code sentence} for a multiplicity indicator near {@code entityName}.
     * Returns a UML multiplicity string (e.g. "1", "0..*") or null if none found.
     */
    private String detectMultiplicity(String sentence, String entityName) {
        int pos = sentence.indexOf(entityName);
        if (pos < 0) return null;

        // Search window: ±60 chars around the entity name
        int from = Math.max(0, pos - 60);
        int to   = Math.min(sentence.length(), pos + entityName.length() + 60);
        String window = sentence.substring(from, to);

        for (Map.Entry<Pattern, String> entry : MULTIPLICITY_PATTERNS) {
            if (entry.getKey().matcher(window).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Finds entity names that appear in the given text.
     *
     * @param text        the text to search
     * @param entityNames the set of known entity names
     * @return a list of entity names found in the text, in order of appearance
     */
    private List<String> findEntitiesInText(String text, Set<String> entityNames) {
        List<String> found = new ArrayList<>();
        Map<String, Integer> positions = new HashMap<>();

        for (String entityName : entityNames) {
            int pos = text.indexOf(entityName);
            if (pos >= 0) {
                positions.put(entityName, pos);
            }
        }

        // Sort by position
        positions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> found.add(e.getKey()));

        return found;
    }

    private boolean containsPhrase(String lowerText, String phrase) {
        return Pattern.compile("(^|\\W)" + Pattern.quote(phrase.toLowerCase(Locale.ROOT)) + "(\\W|$)")
                .matcher(lowerText)
                .find();
    }

    private boolean isActorName(String name) {
        if (name == null) return false;
        return looksLikeActor(name) || ACTOR_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isDiagramInstruction(String phrase) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        return lower.contains("use case diagram")
                || lower.contains("diagram")
                || lower.startsWith("create ")
                || lower.startsWith("draw ")
                || lower.startsWith("generate ");
    }

    private String verbAlternation() {
        List<String> verbs = new ArrayList<>(USE_CASE_ACTION_VERBS);
        verbs.sort(Comparator.comparingInt(String::length).reversed());
        List<String> quoted = new ArrayList<>(verbs.size());
        for (String verb : verbs) {
            quoted.add(Pattern.quote(verb));
        }
        return String.join("|", quoted);
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        String normalized = value
                .replaceAll("(?i)\\bas well as\\b", ",")
                .replaceAll("(?i)\\band finally\\b", ",")
                .replaceAll("(?i)\\band\\b", ",");
        String[] parts = normalized.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String cleaned = cleanUseCasePhrase(part);
            if (!cleaned.isBlank()) result.add(cleaned);
        }
        return result;
    }

    private String cleanUseCasePhrase(String value) {
        if (value == null) return "";
        return value
                .replaceAll("(?i)<<\\s*(include|extend)\\s*>>", "")
                .replaceAll("(?i)\\bwhen\\b.*$", "")
                .replaceAll("(?i)\\bif\\b.*$", "")
                .replaceAll("(?i)\\bwith\\b.*$", "")
                .replaceAll("(?i)^the\\s+", "")
                .replaceAll("(?i)^a\\s+", "")
                .replaceAll("(?i)^an\\s+", "")
                .replaceAll("(?i)^and\\s+", "")
                .replaceAll("(?i)^or\\s+", "")
                .replaceAll("[;:()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String trimAtBoundary(String phrase) {
        if (phrase == null) return "";
        return phrase
                .replaceAll("(?i)\\b(?:and|or|when|if|with|from|to|by|after|before|in|on|for)\\b.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeUseCaseAction(String action) {
        String cleaned = cleanUseCasePhrase(action)
                .replaceAll("(?i)\\bfirst\\b", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (cleaned.equals("log in")) return "login";
        if (cleaned.endsWith("s") && cleaned.split("\\s+").length == 1) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String normalizeKey(String value) {
        return cleanUseCasePhrase(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String toTitleCase(String value) {
        if (value == null || value.isBlank()) return value;
        String[] words = value.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<String> titled = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isBlank()) {
                titled.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", titled);
    }

    /**
     * Extracts actions from the text based on action verb keywords.
     *
     * @param text the input text
     * @return a list of extracted actions
     */
    private List<String> extractActions(String text) {
        Set<String> actions = new LinkedHashSet<>();
        String lowerText = text.toLowerCase();
        String[] words = lowerText.split("\\s+");

        for (String word : words) {
            // Clean the word of punctuation
            String cleanWord = word.replaceAll("[^a-z]", "");
            if (ACTION_VERBS.contains(cleanWord)) {
                actions.add(cleanWord);
                logger.trace("Found action: {}", cleanWord);
            }
        }

        // Also look for verb phrases
        for (String verb : ACTION_VERBS) {
            if (lowerText.contains(verb + "s") || lowerText.contains(verb + "ing") ||
                lowerText.contains(verb + "ed")) {
                actions.add(verb);
            }
        }

        return new ArrayList<>(actions);
    }
}
