package com.example.aidiagramgenerator.service.generation.parser;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.InputParser;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a URL (e.g. a GitHub repository link) into a {@link ParsedInput}.
 *
 * <p>Currently performs lightweight URL analysis (extracts owner, repo name, path
 * segments). In a production setup this would fetch the actual content from the URL
 * and parse it — or an LLM-based version would do so.</p>
 */
@Component
public class UrlInputParser implements InputParser {

    private static final Logger logger = LoggerFactory.getLogger(UrlInputParser.class);

    /** Pattern for GitHub repository URLs. */
    private static final Pattern GITHUB_PATTERN =
            Pattern.compile("github\\.com/([^/]+)/([^/]+)(?:/tree/[^/]+/(.+))?");

    @Override
    public InputType supports() {
        return InputType.URL;
    }

    @Override
    public ParsedInput parse(String rawContent) {
        logger.debug("Parsing URL input: {}", rawContent);

        ParsedInput parsed = new ParsedInput(rawContent, InputType.URL);

        try {
            URI uri = URI.create(rawContent.trim());
            parsed.addMetadata("host", uri.getHost() != null ? uri.getHost() : "unknown");
            parsed.addMetadata("scheme", uri.getScheme() != null ? uri.getScheme() : "unknown");

            if (uri.getPath() != null) {
                String[] segments = uri.getPath().split("/");
                for (String segment : segments) {
                    if (!segment.isBlank()) {
                        parsed.addEntity(segment);
                    }
                }
            }

            parseGitHubUrl(rawContent, parsed);

        } catch (Exception e) {
            logger.warn("Could not parse URL structure: {}", rawContent, e);
            parsed.addMetadata("parseError", e.getMessage());
        }

        // URLs typically produce architecture diagrams
        parsed.addKeyword("architecture");
        parsed.addKeyword("system");
        parsed.addKeyword("component");

        logger.debug("URL parsing result: {}", parsed);
        return parsed;
    }

    private void parseGitHubUrl(String url, ParsedInput parsed) {
        Matcher matcher = GITHUB_PATTERN.matcher(url);
        if (matcher.find()) {
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            String path = matcher.groupCount() >= 3 ? matcher.group(3) : null;

            parsed.addMetadata("github.owner", owner);
            parsed.addMetadata("github.repo", repo);
            if (path != null) {
                parsed.addMetadata("github.path", path);
            }
            parsed.addMetadata("sourceType", "github");

            // Add owner/repo as top-level entities
            if (!parsed.getEntities().contains(repo)) {
                parsed.addEntity(repo);
            }

            parsed.addKeyword("repository");
        }
    }
}
