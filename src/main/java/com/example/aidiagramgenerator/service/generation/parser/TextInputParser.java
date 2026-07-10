package com.example.aidiagramgenerator.service.generation.parser;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.InputParser;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses natural-language text into a {@link ParsedInput}.
 *
 * <p>Extracts entities (capitalised nouns), relationships ("A connects to B"),
 * and diagram-relevant keywords. This is intentionally simple — the real heavy
 * lifting will happen in an LLM-based replacement.</p>
 */
@Component
public class TextInputParser implements InputParser {

    private static final Logger logger = LoggerFactory.getLogger(TextInputParser.class);

    /** Keywords that hint at specific diagram types. */
    private static final List<String> DIAGRAM_KEYWORDS = List.of(
            "class", "sequence", "flow", "process", "entity", "relationship",
            "database", "table", "architecture", "system", "component",
            "state", "transition", "bpmn", "workflow", "c4", "container",
            "service", "microservice", "api", "gateway", "queue", "event",
            "use case", "actor", "actors", "stakeholder", "system boundary",
            "include", "extend"
    );

    /** Pattern to detect capitalised words that likely represent entities. */
    private static final Pattern ENTITY_PATTERN = Pattern.compile("\\b([A-Z][a-zA-Z]+)\\b");

    /** Patterns to detect simple relationship phrases. */
    private static final Pattern RELATIONSHIP_PATTERN =
            Pattern.compile("(\\w+)\\s+(?:connects? to|calls?|sends? to|depends? on|extends?|implements?|uses?)\\s+(\\w+)",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public InputType supports() {
        return InputType.TEXT;
    }

    @Override
    public ParsedInput parse(String rawContent) {
        logger.debug("Parsing text input (length={})", rawContent.length());

        ParsedInput parsed = new ParsedInput(rawContent, InputType.TEXT);

        extractKeywords(rawContent, parsed);
        extractEntities(rawContent, parsed);
        extractRelationships(rawContent, parsed);

        parsed.addMetadata("charCount", String.valueOf(rawContent.length()));
        parsed.addMetadata("wordCount", String.valueOf(rawContent.split("\\s+").length));

        logger.debug("Text parsing result: {}", parsed);
        return parsed;
    }

    private void extractKeywords(String text, ParsedInput parsed) {
        String lower = text.toLowerCase();
        for (String keyword : DIAGRAM_KEYWORDS) {
            if (lower.contains(keyword)) {
                parsed.addKeyword(keyword);
            }
        }
    }

    private void extractEntities(String text, ParsedInput parsed) {
        Matcher matcher = ENTITY_PATTERN.matcher(text);
        while (matcher.find()) {
            String entity = matcher.group(1);
            // Skip very common English words
            if (!isCommonWord(entity) && !parsed.getEntities().contains(entity)) {
                parsed.addEntity(entity);
            }
        }
    }

    private void extractRelationships(String text, ParsedInput parsed) {
        Matcher matcher = RELATIONSHIP_PATTERN.matcher(text);
        while (matcher.find()) {
            String from = matcher.group(1);
            String to = matcher.group(2);
            parsed.addRelationship(from + " -> " + to);
        }
    }

    private boolean isCommonWord(String word) {
        return Arrays.asList(
                "The", "This", "That", "These", "Those", "There",
                "Here", "Where", "When", "What", "Which", "Who",
                "How", "Each", "Every", "Some", "Any", "All",
                "Describe", "Create", "Generate", "Build", "Make",
                "With", "From", "Into", "About", "Also", "Then"
        ).contains(word);
    }
}
