package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.RepositoryLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Best-effort primary-language and framework detection from a file-extension histogram and the
 * contents of a handful of well-known marker files. Never executes anything - purely reads.
 */
@Service
public class LanguageDetectionService {

    private static final Map<String, RepositoryLanguage> EXTENSION_LANGUAGE = Map.ofEntries(
            Map.entry("java", RepositoryLanguage.JAVA),
            Map.entry("kt", RepositoryLanguage.KOTLIN),
            Map.entry("kts", RepositoryLanguage.KOTLIN),
            Map.entry("ts", RepositoryLanguage.TYPESCRIPT),
            Map.entry("tsx", RepositoryLanguage.TYPESCRIPT),
            Map.entry("js", RepositoryLanguage.JAVASCRIPT),
            Map.entry("jsx", RepositoryLanguage.JAVASCRIPT),
            Map.entry("mjs", RepositoryLanguage.JAVASCRIPT),
            Map.entry("py", RepositoryLanguage.PYTHON),
            Map.entry("cs", RepositoryLanguage.CSHARP),
            Map.entry("go", RepositoryLanguage.GO),
            Map.entry("rb", RepositoryLanguage.RUBY),
            Map.entry("php", RepositoryLanguage.PHP),
            Map.entry("cpp", RepositoryLanguage.CPP),
            Map.entry("cc", RepositoryLanguage.CPP),
            Map.entry("hpp", RepositoryLanguage.CPP),
            Map.entry("c", RepositoryLanguage.C),
            Map.entry("h", RepositoryLanguage.C)
    );

    private final ObjectMapper objectMapper;

    public LanguageDetectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record DetectionResult(RepositoryLanguage primaryLanguage, String framework, String projectName) {
    }

    public DetectionResult detect(Map<String, Integer> extensionCounts, Map<String, String> markerFiles) {
        RepositoryLanguage primaryLanguage = detectPrimaryLanguage(extensionCounts);
        String framework = detectFramework(markerFiles);
        String projectName = detectProjectName(markerFiles);
        return new DetectionResult(primaryLanguage, framework, projectName);
    }

    private RepositoryLanguage detectPrimaryLanguage(Map<String, Integer> extensionCounts) {
        String bestExtension = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : extensionCounts.entrySet()) {
            if (!EXTENSION_LANGUAGE.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestExtension = entry.getKey();
            }
        }
        return bestExtension == null ? RepositoryLanguage.UNKNOWN : EXTENSION_LANGUAGE.get(bestExtension);
    }

    private String detectFramework(Map<String, String> markerFiles) {
        String pomXml = markerFiles.get("pom.xml");
        if (pomXml != null) {
            return pomXml.contains("spring-boot") ? "Spring Boot" : "Maven";
        }
        String gradle = firstNonNull(markerFiles.get("build.gradle"), markerFiles.get("build.gradle.kts"));
        if (gradle != null) {
            return gradle.contains("org.springframework.boot") ? "Spring Boot" : "Gradle";
        }
        String packageJson = markerFiles.get("package.json");
        if (packageJson != null) {
            return detectNodeFramework(packageJson);
        }
        String requirements = firstNonNull(markerFiles.get("requirements.txt"), markerFiles.get("pipfile"));
        if (requirements != null) {
            String lower = requirements.toLowerCase();
            if (lower.contains("django")) return "Django";
            if (lower.contains("flask")) return "Flask";
            if (lower.contains("fastapi")) return "FastAPI";
            return "Python";
        }
        if (markerFiles.containsKey("go.mod")) {
            return "Go Modules";
        }
        String gemfile = markerFiles.get("gemfile");
        if (gemfile != null) {
            return gemfile.toLowerCase().contains("rails") ? "Ruby on Rails" : "Ruby";
        }
        String composerJson = markerFiles.get("composer.json");
        if (composerJson != null) {
            return composerJson.contains("laravel/framework") ? "Laravel" : "PHP/Composer";
        }
        if (markerFiles.keySet().stream().anyMatch(name -> name.endsWith(".csproj"))) {
            return ".NET";
        }
        return null;
    }

    private String detectNodeFramework(String packageJson) {
        try {
            JsonNode node = objectMapper.readTree(packageJson);
            String deps = collectDependencyNames(node);
            if (deps.contains("\"next\"")) return "Next.js";
            if (deps.contains("\"react\"")) return "React";
            if (deps.contains("\"@angular/core\"")) return "Angular";
            if (deps.contains("\"vue\"")) return "Vue";
            if (deps.contains("\"express\"")) return "Express";
            if (deps.contains("\"@nestjs/core\"")) return "NestJS";
            return "Node.js";
        } catch (Exception e) {
            return "Node.js";
        }
    }

    private String collectDependencyNames(JsonNode packageJsonNode) {
        StringBuilder combined = new StringBuilder();
        for (String section : new String[] {"dependencies", "devDependencies"}) {
            JsonNode section_ = packageJsonNode.get(section);
            if (section_ != null) {
                section_.fieldNames().forEachRemaining(name -> combined.append('"').append(name).append('"').append(' '));
            }
        }
        return combined.toString();
    }

    private String detectProjectName(Map<String, String> markerFiles) {
        String packageJson = markerFiles.get("package.json");
        if (packageJson != null) {
            try {
                JsonNode node = objectMapper.readTree(packageJson);
                JsonNode name = node.get("name");
                if (name != null && !name.asText().isBlank()) {
                    return name.asText();
                }
            } catch (Exception ignored) {
                // Best effort only.
            }
        }
        String pomXml = markerFiles.get("pom.xml");
        if (pomXml != null) {
            String artifactId = extractXmlTag(pomXml, "artifactId");
            if (artifactId != null) {
                return artifactId;
            }
        }
        return null;
    }

    private static String extractXmlTag(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        if (start < 0) {
            return null;
        }
        int contentStart = start + openTag.length();
        int end = xml.indexOf(closeTag, contentStart);
        if (end < 0) {
            return null;
        }
        String value = xml.substring(contentStart, end).trim();
        return value.isEmpty() ? null : value;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
