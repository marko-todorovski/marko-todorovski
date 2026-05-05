package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
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
     */
    private static final Map<String, String> RELATIONSHIP_KEYWORDS = Map.ofEntries(
            Map.entry("has", "association"),
            Map.entry("have", "association"),
            Map.entry("having", "association"),
            Map.entry("contains", "composition"),
            Map.entry("contain", "composition"),
            Map.entry("uses", "dependency"),
            Map.entry("use", "dependency"),
            Map.entry("using", "dependency"),
            Map.entry("belongs to", "association"),
            Map.entry("belongs", "association"),
            Map.entry("extends", "inheritance"),
            Map.entry("inherits", "inheritance"),
            Map.entry("inherit", "inheritance"),
            Map.entry("implements", "realization"),
            Map.entry("implement", "realization"),
            Map.entry("depends on", "dependency"),
            Map.entry("depends", "dependency"),
            Map.entry("references", "association"),
            Map.entry("reference", "association"),
            Map.entry("composed of", "composition"),
            Map.entry("aggregates", "aggregation"),
            Map.entry("aggregate", "aggregation"),
            Map.entry("creates", "dependency"),
            Map.entry("create", "dependency"),
            Map.entry("manages", "association"),
            Map.entry("manage", "association"),
            Map.entry("owns", "composition"),
            Map.entry("own", "composition"),
            Map.entry("connects to", "association"),
            Map.entry("connects", "association"),
            Map.entry("linked to", "association"),
            Map.entry("associated with", "association")
    );

    /**
     * Keywords that indicate actions.
     */
    private static final Set<String> ACTION_VERBS = Set.of(
            "create", "read", "update", "delete", "save", "load", "process",
            "validate", "send", "receive", "notify", "authenticate", "authorize",
            "calculate", "generate", "transform", "convert", "execute", "invoke",
            "handle", "manage", "control", "monitor", "log", "track", "fetch",
            "store", "retrieve", "submit", "approve", "reject", "cancel"
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
        logger.debug("Extracting semantic model from text");

        validateInput(text);

        // Attempt AI-powered extraction
        SemanticModel aiModel = attemptAiExtraction(text);
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
    private SemanticModel attemptAiExtraction(String text) {
        Instant start = Instant.now();
        String providerName = aiModelService.getClass().getSimpleName();

        try {
            String prompt = buildExtractionPrompt(text);
            logger.debug("Sending extraction request to {} (prompt length: {} chars)",
                    providerName, prompt.length());

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
     * Builds the extraction prompt for the AI provider.
     *
     * @param text the input text
     * @return the constructed prompt
     */
    private String buildExtractionPrompt(String text) {
        return """
                You are a software modeling expert.
                Extract entities, attributes, and relationships from the following description.
                
                Return ONLY valid JSON:
                {
                  "entities": [
                      { "name": "User", "attributes": ["id", "email"] }
                  ],
                  "relationships": [
                      { "source": "User", "target": "Order", "type": "one-to-many" }
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

            if (name != null && !name.isBlank()) {
                entities.add(new EntityNode(name, attributes != null ? attributes : Collections.emptyList()));
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

            if (source != null && target != null && !source.isBlank() && !target.isBlank()) {
                relationships.add(new Relationship(source, target, type != null ? type : "association"));
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
            "user", "admin", "client", "customer", "operator", "guest", "member", "visitor"
    );

    private SemanticModel extractByHeuristics(String text) {
        SemanticModel model = new SemanticModel();

        // Extract entities — always ensure a User actor is present first
        List<EntityNode> entities = extractEntities(text);
        ensureUserActor(entities, text);
        entities.forEach(model::addEntity);
        logger.info("Extracted {} entities", entities.size());

        // Extract relationships
        List<Relationship> relationships = extractRelationships(text, entities);
        relationships.forEach(model::addRelationship);
        logger.info("Extracted {} relationships", relationships.size());

        // Extract verb-phrase actions (e.g. "request reset", "send email")
        List<String> actions = extractVerbPhraseActions(text);
        if (actions.isEmpty()) {
            actions = extractActions(text);
        }
        actions.forEach(model::addAction);
        logger.info("Extracted {} actions", actions.size());

        logger.debug("Semantic extraction complete: {}", model);
        return model;
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
            if (lower.contains(actor)) {
                String actorName = Character.toUpperCase(actor.charAt(0)) + actor.substring(1);
                entities.add(0, new EntityNode(actorName, Collections.emptyList()));
                return;
            }
        }
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
                if (!RELATIONSHIP_KEYWORDS.containsKey(attr) && attr.length() > 1) {
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
                // Look for relationship keywords in the sentence
                for (Map.Entry<String, String> keyword : RELATIONSHIP_KEYWORDS.entrySet()) {
                    if (sentence.toLowerCase().contains(keyword.getKey())) {
                        // Create relationship between first two entities found
                        String source = entitiesInSentence.get(0);
                        String target = entitiesInSentence.get(1);
                        String type = keyword.getValue();

                        Relationship rel = new Relationship(source, target, type);
                        if (!relationships.contains(rel)) {
                            relationships.add(rel);
                            logger.trace("Found relationship: {} -> {} ({})", source, target, type);
                        }
                        break;
                    }
                }
            }
        }

        return relationships;
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
