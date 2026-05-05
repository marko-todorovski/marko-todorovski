package com.example.aidiagramgenerator.service.generation.parser;

import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.InputParser;
import com.example.aidiagramgenerator.service.generation.model.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NLP-powered parser for natural language text using Stanford CoreNLP.
 *
 * <p>This parser extracts:
 * <ul>
 *   <li>Entities: Nouns, multi-word entities (compound nouns), named entities</li>
 *   <li>Relationships: Subject-verb-object triples from dependency parsing</li>
 *   <li>Actions: Verbs with their arguments (subject, object)</li>
 * </ul>
 *
 * <p>Example input: "A User creates an Order and the PaymentService processes payments"
 * <p>Extracts: entities=[User, Order, PaymentService], actions=[creates, processes],
 *              relationships=[User->Order:creates, PaymentService->payments:processes]
 */
@Component
public class NaturalLanguageParser implements InputParser {

    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageParser.class);

    /** Stanford CoreNLP pipeline for NLP processing. */
    private StanfordCoreNLP pipeline;

    /** Keywords that hint at specific diagram types. */
    private static final List<String> DIAGRAM_KEYWORDS = List.of(
            "class", "sequence", "flow", "process", "entity", "relationship",
            "database", "table", "architecture", "system", "component",
            "state", "transition", "bpmn", "workflow", "c4", "container",
            "service", "microservice", "api", "gateway", "queue", "event",
            "actor", "use case", "deployment", "server", "node"
    );

    /** Pattern to detect PascalCase multi-word entities (e.g., OrderItem, PaymentService). */
    private static final Pattern PASCAL_CASE_PATTERN = 
            Pattern.compile("\\b([A-Z][a-z]+(?:[A-Z][a-z]+)+)\\b");

    /** Pattern to detect quoted multi-word entities. */
    private static final Pattern QUOTED_ENTITY_PATTERN = 
            Pattern.compile("\"([^\"]+)\"|'([^']+)'");

    /** Common stopwords to filter from entity extraction. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "this", "that", "these", "those", "there", "here",
            "where", "when", "what", "which", "who", "how", "each", "every",
            "some", "any", "all", "describe", "create", "generate", "build",
            "make", "with", "from", "into", "about", "also", "then", "show",
            "display", "include", "between", "using", "example", "following"
    );

    /** Verb lemmas that indicate relationships in domain modeling. */
    private static final Set<String> RELATIONSHIP_VERBS = Set.of(
            "create", "send", "receive", "call", "return", "process", "validate",
            "store", "retrieve", "update", "delete", "connect", "depend", "extend",
            "implement", "use", "have", "contain", "manage", "handle", "query",
            "authenticate", "authorize", "enroll", "register", "submit", "approve",
            "reject", "notify", "trigger", "invoke", "publish", "subscribe"
    );

    // ── Pattern-based SemanticModel extraction (no CoreNLP required) ─────────

    /** Splits text at sentence-ending punctuation followed by whitespace. */
    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[.!?])\\s+");

    /**
     * Matches capitalized words of 3+ characters that may be domain entity names.
     * Intentionally broad — filtered by {@link #NON_ENTITY_WORDS}.
     */
    private static final Pattern CAPITALIZED_WORD_PATTERN =
            Pattern.compile("\\b([A-Z][a-zA-Z]{2,})\\b");

    /**
     * Common English words that appear capitalised at sentence starts or in
     * titles but are NOT domain entity names.
     */
    private static final Set<String> NON_ENTITY_WORDS = Set.of(
            "The", "A", "An", "In", "On", "At", "By", "For", "From", "To",
            "Of", "And", "Or", "But", "With", "As", "Is", "Are", "Was", "Were",
            "All", "Each", "Every", "Some", "Any", "Both", "This", "That",
            "These", "Those", "Such", "While", "When", "Where", "How", "What",
            "Which", "Who", "If", "Then", "So", "Also", "Just", "Only",
            "Additionally", "Furthermore", "However", "Therefore", "Finally",
            "First", "Second", "Third", "Next", "Last", "Upon", "After",
            "Before", "During", "Between", "Without", "Within", "Through");

    /**
     * Subject-Verb-Object regex built from {@link #RELATIONSHIP_VERBS} with
     * common English inflections (3rd-person singular, past, gerund).
     *
     * <p>Groups: (1) subject — capitalized word; (2) verb form; (3) object — word ≥ 3 chars.
     */
    private static final Pattern SVO_PATTERN = buildSvoPattern();

    @PostConstruct
    public void init() {
        logger.info("Initializing Stanford CoreNLP pipeline...");
        long start = System.currentTimeMillis();
        
        Properties props = new Properties();
        // Use tokenize, ssplit (sentence split), pos (part-of-speech), lemma, ner, depparse
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
        // Optimize for speed
        props.setProperty("ner.applyFineGrained", "false");
        props.setProperty("ner.useSUTime", "false");
        
        this.pipeline = new StanfordCoreNLP(props);
        
        logger.info("CoreNLP pipeline initialized in {}ms", System.currentTimeMillis() - start);
    }

    @Override
    public InputType supports() {
        return InputType.NATURAL_LANGUAGE;
    }

    @Override
    public ParsedInput parse(String rawContent) {
        logger.debug("Parsing natural language input (length={})", rawContent.length());
        long startTime = System.currentTimeMillis();

        ParsedInput parsed = new ParsedInput(rawContent, InputType.NATURAL_LANGUAGE);
        NlpParseResult nlpResult = new NlpParseResult(rawContent);

        // Extract PascalCase entities first (before NLP processing)
        extractPascalCaseEntities(rawContent, nlpResult);
        extractQuotedEntities(rawContent, nlpResult);

        // Process with CoreNLP
        CoreDocument document = new CoreDocument(rawContent);
        pipeline.annotate(document);

        // Extract from each sentence
        for (CoreSentence sentence : document.sentences()) {
            extractEntitiesFromSentence(sentence, nlpResult);
            extractActionsAndRelationships(sentence, nlpResult);
        }

        // Extract diagram-type keywords
        extractKeywords(rawContent, parsed);

        // Transfer NLP results to ParsedInput
        transferNlpResults(nlpResult, parsed);

        // Add metadata
        long parseTime = System.currentTimeMillis() - startTime;
        nlpResult.setParseTimeMs(parseTime);
        parsed.addMetadata("charCount", String.valueOf(rawContent.length()));
        parsed.addMetadata("wordCount", String.valueOf(rawContent.split("\\s+").length));
        parsed.addMetadata("parserType", "nlp_corenlp");
        parsed.addMetadata("parseTimeMs", String.valueOf(parseTime));
        parsed.addMetadata("entitiesExtracted", String.valueOf(nlpResult.getEntities().size()));
        parsed.addMetadata("actionsExtracted", String.valueOf(nlpResult.getActions().size()));
        parsed.addMetadata("relationshipsExtracted", String.valueOf(nlpResult.getRelationships().size()));

        logger.debug("NLP parsing completed: {}", nlpResult);
        return parsed;
    }

    /**
     * Returns the full NLP parse result with structured objects.
     * Use this method when you need detailed entity/relationship/action objects.
     */
    public NlpParseResult parseToNlpResult(String rawContent) {
        logger.debug("Parsing to NlpParseResult (length={})", rawContent.length());
        long startTime = System.currentTimeMillis();

        NlpParseResult result = new NlpParseResult(rawContent);

        // Extract PascalCase and quoted entities
        extractPascalCaseEntities(rawContent, result);
        extractQuotedEntities(rawContent, result);

        // Process with CoreNLP
        CoreDocument document = new CoreDocument(rawContent);
        pipeline.annotate(document);

        for (CoreSentence sentence : document.sentences()) {
            extractEntitiesFromSentence(sentence, result);
            extractActionsAndRelationships(sentence, result);
        }

        result.setParseTimeMs(System.currentTimeMillis() - startTime);
        logger.debug("NLP parsing completed: {}", result);
        return result;
    }

    /**
     * Extracts PascalCase multi-word entities like OrderItem, PaymentService.
     */
    private void extractPascalCaseEntities(String text, NlpParseResult result) {
        Matcher matcher = PASCAL_CASE_PATTERN.matcher(text);
        while (matcher.find()) {
            String entity = matcher.group(1);
            result.addEntity(new ExtractedEntity(
                    entity, entity, matcher.start(), matcher.end(), "CONCEPT", 1.0
            ));
        }
    }

    /**
     * Extracts quoted multi-word entities.
     */
    private void extractQuotedEntities(String text, NlpParseResult result) {
        Matcher matcher = QUOTED_ENTITY_PATTERN.matcher(text);
        while (matcher.find()) {
            String entity = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String normalized = toPascalCase(entity);
            result.addEntity(new ExtractedEntity(
                    normalized, entity, matcher.start(), matcher.end(), "QUOTED", 1.0
            ));
        }
    }

    /**
     * Extracts entities from a sentence using NER and compound noun detection.
     */
    private void extractEntitiesFromSentence(CoreSentence sentence, NlpParseResult result) {
        List<CoreLabel> tokens = sentence.tokens();
        
        // Extract named entities
        List<CoreLabel> currentNer = new ArrayList<>();
        String currentNerType = "O";
        
        for (CoreLabel token : tokens) {
            String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (ner == null) ner = "O";
            
            if (!"O".equals(ner)) {
                if (ner.equals(currentNerType)) {
                    currentNer.add(token);
                } else {
                    if (!currentNer.isEmpty()) {
                        addNamedEntity(currentNer, currentNerType, result);
                    }
                    currentNer = new ArrayList<>();
                    currentNer.add(token);
                    currentNerType = ner;
                }
            } else {
                if (!currentNer.isEmpty()) {
                    addNamedEntity(currentNer, currentNerType, result);
                    currentNer = new ArrayList<>();
                    currentNerType = "O";
                }
            }
        }
        if (!currentNer.isEmpty()) {
            addNamedEntity(currentNer, currentNerType, result);
        }

        // Extract compound nouns and regular nouns using dependency parse
        SemanticGraph dependencies = sentence.dependencyParse();
        extractCompoundNouns(dependencies, tokens, result);
    }

    /**
     * Adds a named entity from consecutive NER tokens.
     */
    private void addNamedEntity(List<CoreLabel> tokens, String nerType, NlpParseResult result) {
        String originalText = tokens.stream()
                .map(CoreLabel::word)
                .collect(Collectors.joining(" "));
        String normalized = toPascalCase(originalText);
        
        if (!isStopword(normalized.toLowerCase())) {
            int start = tokens.get(0).beginPosition();
            int end = tokens.get(tokens.size() - 1).endPosition();
            result.addEntity(new ExtractedEntity(normalized, originalText, start, end, nerType, 0.9));
        }
    }

    /**
     * Extracts compound nouns (multi-word entities) from dependency parse.
     */
    private void extractCompoundNouns(SemanticGraph dependencies, List<CoreLabel> tokens, 
                                       NlpParseResult result) {
        // Build compound noun groups
        Map<Integer, List<CoreLabel>> compoundGroups = new HashMap<>();
        
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if ("compound".equals(relation)) {
                int headIndex = edge.getGovernor().index();
                CoreLabel dependent = tokens.get(edge.getDependent().index() - 1);
                CoreLabel head = tokens.get(headIndex - 1);
                
                compoundGroups.computeIfAbsent(headIndex, k -> new ArrayList<>())
                        .add(dependent);
                // Ensure head is added
                if (!compoundGroups.get(headIndex).contains(head)) {
                    compoundGroups.get(headIndex).add(head);
                }
            }
        }

        // Create entities from compound groups
        for (List<CoreLabel> group : compoundGroups.values()) {
            group.sort(Comparator.comparing(CoreLabel::index));
            String originalText = group.stream()
                    .map(CoreLabel::word)
                    .collect(Collectors.joining(" "));
            String normalized = toPascalCase(originalText);
            
            if (!isStopword(normalized.toLowerCase()) && normalized.length() > 2) {
                int start = group.get(0).beginPosition();
                int end = group.get(group.size() - 1).endPosition();
                result.addEntity(new ExtractedEntity(normalized, originalText, start, end, "COMPOUND", 0.85));
            }
        }

        // Also extract standalone nouns (NN, NNS, NNP, NNPS)
        for (CoreLabel token : tokens) {
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            if (pos != null && pos.startsWith("NN")) {
                String word = token.word();
                // Skip if already part of a compound or if it's a stopword
                if (!isStopword(word.toLowerCase()) && Character.isUpperCase(word.charAt(0))) {
                    result.addEntity(new ExtractedEntity(
                            word, word, token.beginPosition(), token.endPosition(), "NOUN", 0.7
                    ));
                }
            }
        }
    }

    /**
     * Extracts actions (verbs) and relationships from dependency parse.
     */
    private void extractActionsAndRelationships(CoreSentence sentence, NlpParseResult result) {
        SemanticGraph dependencies = sentence.dependencyParse();
        List<CoreLabel> tokens = sentence.tokens();
        
        // Find all verbs and their arguments
        for (CoreLabel token : tokens) {
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            if (pos != null && pos.startsWith("VB")) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                if (lemma == null) lemma = token.word().toLowerCase();
                
                // Skip auxiliary verbs
                if (isAuxiliaryVerb(lemma)) continue;
                
                // Find subject and object
                String subject = findSubject(dependencies, token.index());
                String object = findObject(dependencies, token.index());
                
                // Determine tense
                String tense = determineTense(pos);
                
                // Add action
                ExtractedAction action = new ExtractedAction(
                        lemma, token.word(), subject, object, tense, false, 0.85
                );
                result.addAction(action);
                
                // Create relationship if both subject and object are found
                if (subject != null && object != null && RELATIONSHIP_VERBS.contains(lemma)) {
                    String normalizedSubject = toPascalCase(subject);
                    String normalizedObject = toPascalCase(object);
                    
                    // Add entities if not already present
                    result.addEntity(new ExtractedEntity(normalizedSubject, "SUBJECT"));
                    result.addEntity(new ExtractedEntity(normalizedObject, "OBJECT"));
                    
                    ExtractedRelationship relationship = new ExtractedRelationship(
                            normalizedSubject, normalizedObject, lemma,
                            token.word(), null, null, 0.8
                    );
                    result.addRelationship(relationship);
                }
            }
        }
    }

    /**
     * Finds the subject of a verb using dependency relations.
     */
    private String findSubject(SemanticGraph dependencies, int verbIndex) {
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            if (edge.getGovernor().index() == verbIndex) {
                String relation = edge.getRelation().getShortName();
                if ("nsubj".equals(relation) || "nsubj:pass".equals(relation)) {
                    return expandCompound(dependencies, edge.getDependent().index());
                }
            }
        }
        return null;
    }

    /**
     * Finds the object of a verb using dependency relations.
     */
    private String findObject(SemanticGraph dependencies, int verbIndex) {
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            if (edge.getGovernor().index() == verbIndex) {
                String relation = edge.getRelation().getShortName();
                if ("obj".equals(relation) || "dobj".equals(relation) || 
                    "iobj".equals(relation) || "obl".equals(relation)) {
                    return expandCompound(dependencies, edge.getDependent().index());
                }
            }
        }
        return null;
    }

    /**
     * Expands a word to include its compound modifiers.
     */
    private String expandCompound(SemanticGraph dependencies, int tokenIndex) {
        List<String> parts = new ArrayList<>();
        String headWord = dependencies.getNodeByIndex(tokenIndex).word();
        
        // Find compound modifiers
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            if (edge.getGovernor().index() == tokenIndex && 
                "compound".equals(edge.getRelation().getShortName())) {
                parts.add(edge.getDependent().word());
            }
        }
        
        parts.add(headWord);
        return String.join(" ", parts);
    }

    /**
     * Determines the tense from POS tag.
     */
    private String determineTense(String pos) {
        return switch (pos) {
            case "VBD", "VBN" -> "past";
            case "VB", "VBP", "VBZ" -> "present";
            case "VBG" -> "progressive";
            default -> "present";
        };
    }

    /**
     * Checks if a verb is an auxiliary (be, have, do, will, etc.).
     */
    private boolean isAuxiliaryVerb(String lemma) {
        return Set.of("be", "have", "do", "will", "would", "could", "should", 
                      "may", "might", "must", "can", "shall").contains(lemma);
    }

    /**
     * Converts text to PascalCase.
     */
    private String toPascalCase(String text) {
        if (text == null || text.isEmpty()) return text;
        
        // Handle already PascalCase
        if (text.matches("[A-Z][a-zA-Z]+")) {
            return text;
        }
        
        // Split by spaces, hyphens, underscores
        String[] words = text.split("[\\s_-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }

    /**
     * Checks if a word is a common stopword.
     */
    private boolean isStopword(String word) {
        return STOPWORDS.contains(word.toLowerCase());
    }

    /**
     * Extracts diagram-type keywords from text.
     */
    private void extractKeywords(String text, ParsedInput parsed) {
        String lower = text.toLowerCase();
        for (String keyword : DIAGRAM_KEYWORDS) {
            if (lower.contains(keyword)) {
                parsed.addKeyword(keyword);
            }
        }
    }

    /**
     * Transfers NLP results to the ParsedInput structure.
     */
    private void transferNlpResults(NlpParseResult nlpResult, ParsedInput parsed) {
        // Transfer entities
        for (ExtractedEntity entity : nlpResult.getEntities()) {
            if (!parsed.getEntities().contains(entity.getName())) {
                parsed.addEntity(entity.getName());
            }
        }
        
        // Transfer relationships
        for (ExtractedRelationship relationship : nlpResult.getRelationships()) {
            parsed.addRelationship(relationship.toArrowNotation());
        }
        
        // Transfer actions
        for (ExtractedAction action : nlpResult.getActions()) {
            parsed.addAction(action.getVerb());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pattern-based SemanticModel extraction — no CoreNLP required
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parses natural language text into a structured {@link SemanticModel} using
     * pure pattern-based extraction — no external NLP library required.
     *
     * <p>Extraction strategy (in priority order):
     * <ol>
     *   <li>PascalCase compound words ({@code OrderItem}, {@code PaymentService}) — highest confidence</li>
     *   <li>Quoted multi-word entities ({@code "Order Management System"}) — high confidence</li>
     *   <li>Subject-Verb-Object triples via {@link #SVO_PATTERN} — entities + relationships + actions</li>
     *   <li>Remaining capitalized words filtered by {@link #NON_ENTITY_WORDS} — supplementary</li>
     * </ol>
     *
     * <p>Entity names are normalised to PascalCase. Duplicates are removed.
     * Handles multi-sentence input by splitting on sentence boundaries.
     *
     * @param text multi-sentence natural language input
     * @return a {@link SemanticModel} with deduplicated entities, relationships, and action verbs
     */
    public SemanticModel parseToSemanticModel(String text) {
        if (text == null || text.isBlank()) {
            return new SemanticModel();
        }

        // Insertion-ordered sets for deterministic, deduplicated output
        Set<String>       entityNames      = new LinkedHashSet<>();
        Set<String>       actionLemmas     = new LinkedHashSet<>();
        List<Relationship> relationships   = new ArrayList<>();
        Set<String>       relationshipKeys = new HashSet<>();  // "src|tgt|type"

        // Phase 1 – PascalCase compound entities (OrderItem, PaymentService …)
        Matcher pascal = PASCAL_CASE_PATTERN.matcher(text);
        while (pascal.find()) {
            entityNames.add(pascal.group(1));
        }

        // Phase 2 – Quoted multi-word entities ("Order Management System")
        Matcher quoted = QUOTED_ENTITY_PATTERN.matcher(text);
        while (quoted.find()) {
            String raw = quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
            entityNames.add(toPascalCase(raw));
        }

        // Phase 3 – Per-sentence SVO triples + supplementary capitalized words
        String[] sentences = SENTENCE_BOUNDARY.split(text);
        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;

            // SVO extraction
            Matcher svo = SVO_PATTERN.matcher(s);
            while (svo.find()) {
                String subject  = svo.group(1);
                String verbForm = svo.group(2);
                String object   = svo.group(3);

                String lemma = findVerbLemma(verbForm);
                if (lemma == null) continue;

                String normSubj = toPascalCase(subject);
                String normObj  = toPascalCase(object);

                if (isStopword(normSubj.toLowerCase()) || NON_ENTITY_WORDS.contains(normSubj)) continue;
                if (isStopword(normObj.toLowerCase())  || normObj.length() < 2)               continue;

                entityNames.add(normSubj);
                entityNames.add(normObj);
                actionLemmas.add(lemma);

                String key = normSubj + "|" + normObj + "|" + lemma;
                if (relationshipKeys.add(key)) {
                    relationships.add(new Relationship(normSubj, normObj, lemma));
                }
            }

            // Phase 4 – Supplementary capitalized words not already captured
            Matcher cap = CAPITALIZED_WORD_PATTERN.matcher(s);
            while (cap.find()) {
                String word = cap.group(1);
                if (!NON_ENTITY_WORDS.contains(word) && !isStopword(word.toLowerCase())) {
                    entityNames.add(word);
                }
            }
        }

        // Build the domain model (final NON_ENTITY_WORDS filter for safety)
        List<EntityNode> entityNodes = entityNames.stream()
                .filter(name -> !NON_ENTITY_WORDS.contains(name))
                .map(EntityNode::new)
                .collect(Collectors.toList());

        return new SemanticModel(entityNodes, relationships, new ArrayList<>(actionLemmas));
    }

    // ── Static helpers for pattern-based extraction ───────────────────────────

    /**
     * Builds the SVO pattern by generating common inflected forms for every verb
     * in {@link #RELATIONSHIP_VERBS}.
     *
     * <p>Generated forms per base verb {@code v}:
     * <ul>
     *   <li>base: {@code v}</li>
     *   <li>+s (3rd person): {@code v + "s"}</li>
     *   <li>if ends in {@code e}: past {@code v + "d"}, gerund {@code v[0..-1] + "ing"}</li>
     *   <li>if ends in {@code y}: {@code v[0..-1] + "ies"}, {@code v[0..-1] + "ied"}, {@code v + "ing"}</li>
     *   <li>otherwise: {@code v + "es"}, {@code v + "ed"}, {@code v + "ing"}</li>
     * </ul>
     * Irregular forms {@code has} and {@code had} are also included.
     */
    private static Pattern buildSvoPattern() {
        Set<String> forms = new LinkedHashSet<>();
        for (String v : RELATIONSHIP_VERBS) {
            forms.add(v);
            forms.add(v + "s");
            if (v.endsWith("e")) {
                forms.add(v + "d");
                forms.add(v.substring(0, v.length() - 1) + "ing");
            } else if (v.endsWith("y")) {
                forms.add(v.substring(0, v.length() - 1) + "ies");
                forms.add(v.substring(0, v.length() - 1) + "ied");
                forms.add(v + "ing");
            } else {
                forms.add(v + "es");
                forms.add(v + "ed");
                forms.add(v + "ing");
            }
        }
        // Irregular forms
        forms.add("has");
        forms.add("had");

        String verbAlt = String.join("|", forms);
        return Pattern.compile(
                "\\b([A-Z][a-zA-Z]*)\\s+(" + verbAlt + ")\\s+"
                        + "(?:(?:a|an|the|its|their|all|each)\\s+)?([a-zA-Z]{3,})"
        );
    }

    /**
     * Resolves an inflected verb form to its base (lemma) if it is a recognised
     * relationship verb in {@link #RELATIONSHIP_VERBS}.
     *
     * <p>Handles the most common English inflection patterns by stripping suffixes
     * in longest-first order and optionally reinstating a trailing {@code e}.
     *
     * @param verbForm the inflected verb string (case-insensitive)
     * @return the base form, or {@code null} if the verb is not recognised
     */
    private String findVerbLemma(String verbForm) {
        String lower = verbForm.toLowerCase();
        if (RELATIONSHIP_VERBS.contains(lower)) return lower;

        // Irregular verb forms
        if ("has".equals(lower) || "had".equals(lower)) {
            return RELATIONSHIP_VERBS.contains("have") ? "have" : null;
        }

        // Strip inflection suffixes, longest first so "ing" is tried before "s"
        for (String suffix : List.of("ing", "ies", "ied", "ed", "es", "s", "d")) {
            if (lower.endsWith(suffix) && lower.length() > suffix.length() + 2) {
                String stem = lower.substring(0, lower.length() - suffix.length());
                if (RELATIONSHIP_VERBS.contains(stem)) return stem;
                // Reinstate a trailing 'e' elided before -ing, -es, -ed
                if (RELATIONSHIP_VERBS.contains(stem + "e")) return stem + "e";
            }
        }
        return null;
    }
}
