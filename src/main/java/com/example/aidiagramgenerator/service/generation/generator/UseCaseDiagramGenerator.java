package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates PlantUML use-case diagrams from educational exercise text.
 */
@Component
public class UseCaseDiagramGenerator implements DiagramGenerator {

    private static final Set<String> ACTOR_NAMES = Set.of(
            "student", "administrator", "admin", "user", "guest", "professor",
            "teacher", "parent", "registrar", "moderator", "visitor", "customer",
            "bank customer", "maintenance technician", "billing system",
            "payment gateway", "bank server", "cash dispenser", "search engine"
    );

    private static final Set<String> ACTION_VERBS = Set.of(
            "login", "log in", "download", "view", "search", "register", "drop",
            "pay", "submit", "approve", "reject", "create", "update", "manage",
            "browse", "rate", "comment", "subscribe", "review", "edit", "publish",
            "enroll", "attend", "take", "upload", "schedule", "grade", "send",
            "receive", "generate", "insert", "authenticate", "withdraw", "deposit",
            "transfer", "print", "eject", "refill", "run", "collect", "check",
            "verify", "validate", "process", "assign"
    );

    @Override
    public DiagramType supports() {
        return DiagramType.USE_CASE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        Set<String> actors = new LinkedHashSet<>();
        Set<String> useCases = new LinkedHashSet<>();
        List<UseCaseRelationship> relationships = new ArrayList<>();

        String raw = parsedInput.getRawContent() == null ? "" : parsedInput.getRawContent();
        extractActors(raw, actors);
        extractActorUseCases(raw, actors, useCases, relationships);
        extractIncludeExtend(raw, "include", useCases, relationships);
        extractIncludeExtend(raw, "extend", useCases, relationships);
        extractModalRelationships(raw, "include", useCases, relationships);
        extractModalRelationships(raw, "extend", useCases, relationships);

        for (String action : parsedInput.getActions()) {
            addUseCase(useCases, action);
        }
        for (String entity : parsedInput.getEntities()) {
            if (isActor(entity)) actors.add(toTitleCase(entity));
        }

        if (actors.isEmpty()) actors.add("User");
        if (useCases.isEmpty()) {
            useCases.add("login");
            useCases.add("view information");
        }
        if (relationships.stream().noneMatch(r -> "association".equals(r.type))) {
            String actor = actors.iterator().next();
            for (String useCase : useCases) {
                relationships.add(new UseCaseRelationship(actor, useCase, "association"));
            }
        }

        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append("left to right direction\n");
        sb.append("skinparam packageStyle rectangle\n\n");

        for (String actor : actors) {
            String titleActor = toTitleCase(actor);
            if (titleActor.contains(" ")) {
                sb.append("actor \"").append(titleActor).append("\" as ").append(alias(actor)).append("\n");
            } else {
                sb.append("actor ").append(titleActor).append("\n");
            }
        }

        sb.append("\nrectangle \"System\" {\n");
        for (String useCase : useCases) {
            sb.append("  ").append(parenthesizedUseCase(useCase)).append("\n");
        }
        sb.append("}\n\n");

        Set<String> emitted = new LinkedHashSet<>();
        for (UseCaseRelationship relationship : relationships) {
            String key = relationship.source.toLowerCase(Locale.ROOT) + "|" + relationship.target.toLowerCase(Locale.ROOT) + "|" + relationship.type;
            if (!emitted.add(key)) continue;
            if ("association".equals(relationship.type)) {
                sb.append(actorRef(relationship.source)).append(" --> ")
                  .append(parenthesizedUseCase(relationship.target)).append("\n");
            } else {
                sb.append(parenthesizedUseCase(relationship.source)).append(" ..> ")
                  .append(parenthesizedUseCase(relationship.target))
                  .append(" : <<").append(mapUmlRelationshipType(relationship.type)).append(">>\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private void extractActors(String text, Set<String> actors) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String actor : ACTOR_NAMES) {
            if (containsPhrase(lower, actor)) actors.add(toTitleCase(actor));
        }

        Pattern pattern = Pattern.compile("\\b(?:actors?|stakeholders?)\\s+(?:include|includes|are|is)\\s+([^.!?]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            for (String part : splitList(matcher.group(1))) {
                if (isActor(part)) actors.add(toTitleCase(part));
            }
        }
    }

    private void extractActorUseCases(String text, Set<String> actors, Set<String> useCases,
                                      List<UseCaseRelationship> relationships) {
        Pattern pattern = Pattern.compile("\\b(?:the\\s+)?([A-Za-z][A-Za-z\\s]*?)\\s+can\\s+([^.!?]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String actor = clean(matcher.group(1));
            if (!isActor(actor)) continue;
            actor = toTitleCase(actor);
            actors.add(actor);
            for (String part : splitList(matcher.group(2))) {
                String useCase = normalizeUseCase(part);
                if (addUseCase(useCases, useCase)) {
                    relationships.add(new UseCaseRelationship(actor, useCase, "association"));
                } else if (!useCase.isBlank()) {
                    relationships.add(new UseCaseRelationship(actor, useCase, "association"));
                }
            }
        }
    }

    private void extractIncludeExtend(String text, String type, Set<String> useCases,
                                      List<UseCaseRelationship> relationships) {
        String verb = "include".equals(type) ? "includes?" : "extends?";
        Pattern pattern = Pattern.compile("([^.!?]+?)\\s+(?:(?:<<" + type + ">>)|" + verb + ")\\s+([^.!?]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String source = trailingUseCase(matcher.group(1));
            if (source.toLowerCase(Locale.ROOT).contains("actor")) continue;
            for (String part : splitList(matcher.group(2))) {
                String target = normalizeUseCase(part);
                if (source.isBlank() || target.isBlank()) continue;
                useCases.add(source);
                useCases.add(target);
                relationships.add(new UseCaseRelationship(source, target, mapUmlRelationshipType(type)));
            }
        }
    }

    private void extractModalRelationships(String text, String type, Set<String> useCases,
                                           List<UseCaseRelationship> relationships) {
        String modalPattern = "include".equals(type)
                ? "(?:must|needs? to|requires?)"
                : "(?:might|may|can optionally)";

        for (String sentence : text.split("[.!?]")) {
            addModalRelationshipFromToPhrase(sentence, modalPattern, type, useCases, relationships);
            addModalRelationshipFromDirectPhrase(sentence, modalPattern, type, useCases, relationships);
        }
    }

    private void addModalRelationshipFromToPhrase(String sentence, String modalPattern, String type,
                                                  Set<String> useCases,
                                                  List<UseCaseRelationship> relationships) {
        Pattern pattern = Pattern.compile(
                "\\bto\\s+([^,;]+?)\\s*,?\\s+[^,;]*?\\b" + modalPattern + "\\s+([^,;]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        while (matcher.find()) {
            addUseCaseDependency(matcher.group(1), stripModalTargetNoise(matcher.group(2)),
                    type, useCases, relationships);
        }
    }

    private void addModalRelationshipFromDirectPhrase(String sentence, String modalPattern, String type,
                                                     Set<String> useCases,
                                                     List<UseCaseRelationship> relationships) {
        Pattern pattern = Pattern.compile("(.+?)\\s+\\b" + modalPattern + "\\s+([^,;]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        while (matcher.find()) {
            addUseCaseDependency(trailingUseCase(matcher.group(1)), stripModalTargetNoise(matcher.group(2)),
                    type, useCases, relationships);
        }
    }

    private void addUseCaseDependency(String source, String target, String type, Set<String> useCases,
                                      List<UseCaseRelationship> relationships) {
        String normalizedSource = normalizeUseCase(source);
        String normalizedTarget = normalizeUseCase(target);
        if (normalizedSource.isBlank() || normalizedTarget.isBlank()) return;
        if (isActor(normalizedSource) || normalizedSource.contains("actor")) return;
        useCases.add(normalizedSource);
        useCases.add(normalizedTarget);
        relationships.add(new UseCaseRelationship(normalizedSource, normalizedTarget, mapUmlRelationshipType(type)));
    }

    private boolean addUseCase(Set<String> useCases, String candidate) {
        String useCase = normalizeUseCase(candidate);
        if (useCase.isBlank()) return false;
        String lower = useCase.toLowerCase(Locale.ROOT);
        boolean startsWithAction = ACTION_VERBS.stream()
                .anyMatch(verb -> lower.equals(verb) || lower.startsWith(verb + " "));
        if (!startsWithAction || lower.contains("diagram")) return false;
        return useCases.add(useCase);
    }

    private String trailingUseCase(String value) {
        String cleaned = normalizeUseCase(value);
        String[] words = cleaned.split("\\s+");
        int start = Math.max(0, words.length - 4);
        return String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length));
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
            String cleaned = clean(part);
            if (!cleaned.isBlank()) result.add(cleaned);
        }
        return result;
    }

    private boolean isActor(String value) {
        if (value == null) return false;
        String lower = clean(value).toLowerCase(Locale.ROOT);
        return ACTOR_NAMES.contains(lower)
                || lower.endsWith(" system")
                || lower.endsWith(" gateway")
                || lower.endsWith(" server")
                || lower.endsWith(" dispenser")
                || lower.endsWith(" engine")
                || lower.endsWith(" technician");
    }

    private boolean containsPhrase(String lowerText, String phrase) {
        return Pattern.compile("(^|\\W)" + Pattern.quote(phrase.toLowerCase(Locale.ROOT)) + "(\\W|$)")
                .matcher(lowerText)
                .find();
    }

    private String normalizeUseCase(String value) {
        String cleaned = clean(value)
                .replaceAll("(?i)\\bfirst\\b", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (cleaned.equals("log in")) return "login";
        return cleaned;
    }

    private String stripModalTargetNoise(String target) {
        return clean(target)
                .replaceAll("(?i)\\bfirst\\b", "")
                .replaceAll("(?i)\\bbefore continuing\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String mapUmlRelationshipType(String type) {
        if (type == null) return "include";
        String normalized = type.toLowerCase(Locale.ROOT).replaceAll("[<>\\s]", "");
        if (normalized.startsWith("extend")) return "extend";
        return "include";
    }

    private String clean(String value) {
        if (value == null) return "";
        return value
                .replaceAll("(?i)<<\\s*(include|extend)\\s*>>", "")
                .replaceAll("(?i)\\bwhen\\b.*$", "")
                .replaceAll("(?i)\\bif\\b.*$", "")
                .replaceAll("(?i)^the\\s+", "")
                .replaceAll("(?i)^a\\s+", "")
                .replaceAll("(?i)^an\\s+", "")
                .replaceAll("(?i)^and\\s+", "")
                .replaceAll("[;:()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String alias(String value) {
        String sanitized = value == null ? "Unknown" : value.replaceAll("[^A-Za-z0-9_]", "_");
        return sanitized.isBlank() ? "Unknown" : sanitized;
    }

    private String actorRef(String actor) {
        String title = toTitleCase(actor);
        return title.contains(" ") ? alias(actor) : title;
    }

    private String useCaseAlias(String value) {
        return "UC_" + alias(value);
    }

    private String parenthesizedUseCase(String value) {
        return "(" + toTitleCase(value) + ")";
    }

    private String toTitleCase(String value) {
        if (value == null || value.isBlank()) return value;
        String[] words = value.trim().replace("_", " ").split("\\s+");
        List<String> titled = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isBlank()) titled.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", titled);
    }

    private record UseCaseRelationship(String source, String target, String type) {
    }
}
