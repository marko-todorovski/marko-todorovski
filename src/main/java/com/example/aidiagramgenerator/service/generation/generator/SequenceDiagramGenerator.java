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
 * Generates Mermaid sequence-diagram syntax from parsed input.
 *
 * <p>Uses {@code ->>} for synchronous calls and {@code -->>} for return messages,
 * matching the standard UML sequence diagram notation used in textbooks.
 */
@Component
public class SequenceDiagramGenerator implements DiagramGenerator {

    /** Matches "A calls/sends/requests B: message" or "A -> B: message" patterns */
    private static final Pattern CALL_PATTERN = Pattern.compile(
        "(?i)\\b(\\w+)\\s+(?:calls?|sends?|requests?|invokes?|asks?|notifies?)\\s+(\\w+)" +
        "(?:\\s+(?:to|with|for)\\s+([^.!?\\n]+))?");

    /** Matches "B returns/responds/replies to A" */
    private static final Pattern RETURN_PATTERN = Pattern.compile(
        "(?i)\\b(\\w+)\\s+(?:returns?|responds?|replies?|sends\\s+back)\\s+(?:to\\s+)?(\\w+)" +
        "(?:\\s+(?:a|the|with|an)?\\s+([^.!?\\n]+))?");

    /** Matches "If/When [condition], [message part]". Group 1: condition, Group 2: message text. */
    private static final Pattern ALT_IF_PATTERN = Pattern.compile(
        "(?i)^(?:if|when)\\b\\s*([^,.]+?)[,.]\\s+(.+)$");

    /** Matches "Otherwise/Else [if [condition],] [message part]". Group 1: optional else-condition, Group 2: message text. */
    private static final Pattern ALT_ELSE_PATTERN = Pattern.compile(
        "(?i)^(?:otherwise|else(?:\\s+if)?)[,.]?\\s*(?:([^,.]+?)[,.]\\s+)?(.+)$");

    /** Detects parallel execution phrases within a sentence. */
    private static final Pattern PAR_INDICATOR = Pattern.compile(
        "(?i)\\b(in\\s+parallel|simultaneously|at\\s+the\\s+same\\s+time|concurrently|" +
        "both\\s+(?:servers?|services?|systems?|components?))\\b");

    @Override
    public DiagramType supports() {
        return DiagramType.SEQUENCE;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        List<String> entities      = parsedInput.getEntities();
        List<String> relationships = parsedInput.getRelationships();
        String raw = parsedInput.getRawContent() == null ? "" : parsedInput.getRawContent();

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");

        if (entities.isEmpty()) {
            appendDefault(sb);
            return sb.toString().stripTrailing();
        }

        // Emit participants: human roles use 'actor', system components use 'participant'
        for (String entity : entities) {
            String keyword = isActorName(entity) ? "actor" : "participant";
            sb.append("    ").append(keyword).append(" ").append(entity).append("\n");
        }
        sb.append("\n");

        List<String[]> messages = extractMessages(raw, entities, relationships);

        if (messages.isEmpty()) {
            // Auto-chain: request/response between consecutive participants with activation bars
            for (int i = 0; i < entities.size() - 1; i++) {
                String from = entities.get(i);
                String to   = entities.get(i + 1);
                sb.append("    ").append(from).append("->>" ).append(to).append(": request\n");
                sb.append("    activate ").append(to).append("\n");
                sb.append("    ").append(to).append("-->>").append(from).append(": response\n");
                sb.append("    deactivate ").append(to).append("\n");
            }
        } else {
            Set<String> activated = new LinkedHashSet<>();
            for (String[] msg : messages) {
                String marker = msg[3];
                // Alt fragment markers
                if ("alt_start".equals(marker)) {
                    sb.append("    alt ").append(msg[0]).append("\n");
                    continue;
                }
                if ("alt_else".equals(marker)) {
                    sb.append("    else");
                    if (!msg[0].isBlank()) sb.append(" ").append(msg[0]);
                    sb.append("\n");
                    continue;
                }
                if ("alt_end".equals(marker)) {
                    sb.append("    end\n");
                    continue;
                }
                // Par fragment markers (Mermaid uses 'and' to separate parallel branches)
                if ("par_start".equals(marker)) {
                    sb.append("    par\n");
                    continue;
                }
                if ("par_else".equals(marker)) {
                    sb.append("    and\n");
                    continue;
                }
                if ("par_end".equals(marker)) {
                    sb.append("    end\n");
                    continue;
                }
                // Regular message: msg = [from, to, label, "true"/"false"]
                String from      = msg[0];
                String to        = msg[1];
                boolean isReturn = "true".equals(marker);
                String arrow     = isReturn ? "-->>" : "->>";  
                if (from.equals(to)) {
                    // Self-call: stays on same lifeline, no activation change
                    sb.append("    ").append(from).append("->>" ).append(to)
                      .append(": ").append(msg[2]).append("\n");
                } else {
                    sb.append("    ").append(from).append(arrow).append(to)
                      .append(": ").append(msg[2]).append("\n");
                    if (!isReturn && !activated.contains(to)) {
                        sb.append("    activate ").append(to).append("\n");
                        activated.add(to);
                    }
                    if (isReturn && activated.contains(from)) {
                        sb.append("    deactivate ").append(from).append("\n");
                        activated.remove(from);
                    }
                }
            }
            // Deactivate any still-active participants
            for (String active : new ArrayList<>(activated)) {
                sb.append("    deactivate ").append(active).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    /**
     * Extracts ordered messages from raw text and explicit relationship strings.
     * Returns list of [from, to, label, marker] where marker is "true" (return),
     * "false" (call), "alt_start", "alt_else", or "alt_end".
     * For alt markers, msg[0] carries the condition string.
     */
    private List<String[]> extractMessages(String raw, List<String> entities,
                                            List<String> relationships) {
        List<String[]> result = new ArrayList<>();

        // 1. Parse explicit "A -> B : label" or "A -> B" from relationship list
        for (String rel : relationships) {
            // Accept "A -> B : label", "A --> B : label", "A ->> B : label"
            Matcher rm = Pattern.compile("(\\w+)\\s*-+>+\\s*(\\w+)(?:\\s*:\\s*(.+))?")
                                .matcher(rel);
            if (rm.find()) {
                String from  = resolveCase(rm.group(1), entities);
                String to    = resolveCase(rm.group(2), entities);
                String label = rm.group(3) != null ? rm.group(3).trim() : "call";
                if (from != null && to != null) {
                    result.add(new String[]{from, to, label, "false"});
                }
            }
        }
        if (!result.isEmpty()) return result;

        // 2. Semantic extraction: process sentence by sentence to support alt/par fragments
        boolean inAlt = false;
        boolean inPar = false;
        for (String sentence : raw.split("(?<=[.!?\\n])\\s*")) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            Matcher ifMatcher = ALT_IF_PATTERN.matcher(trimmed);
            if (ifMatcher.matches()) {
                if (inPar) {
                    result.add(new String[]{"", "", "", "par_end"});
                    inPar = false;
                }
                if (inAlt) {
                    result.add(new String[]{"", "", "", "alt_end"});
                }
                result.add(new String[]{normalizeCondition(ifMatcher.group(1).trim()), "", "", "alt_start"});
                inAlt = true;
                extractSentenceMessages(ifMatcher.group(2).trim(), entities, result);
                continue;
            }

            Matcher elseMatcher = ALT_ELSE_PATTERN.matcher(trimmed);
            if (elseMatcher.matches() && inAlt) {
                String elseCondition = elseMatcher.group(1) != null
                    ? normalizeCondition(elseMatcher.group(1).trim()) : "";
                result.add(new String[]{elseCondition, "", "", "alt_else"});
                extractSentenceMessages(elseMatcher.group(2).trim(), entities, result);
                continue;
            }

            if (PAR_INDICATOR.matcher(trimmed).find()) {
                if (inAlt) {
                    result.add(new String[]{"", "", "", "alt_end"});
                    inAlt = false;
                }
                if (!inPar) {
                    result.add(new String[]{"", "", "", "par_start"});
                    inPar = true;
                } else {
                    result.add(new String[]{"", "", "", "par_else"});
                }
                extractSentenceMessages(trimmed, entities, result);
                continue;
            }

            // Regular sentence: close par if open (par body is bounded by par-indicator sentences)
            if (inPar) {
                result.add(new String[]{"", "", "", "par_end"});
                inPar = false;
            }
            extractSentenceMessages(trimmed, entities, result);
        }
        if (inAlt) {
            result.add(new String[]{"", "", "", "alt_end"});
        }
        if (inPar) {
            result.add(new String[]{"", "", "", "par_end"});
        }

        return result;
    }

    /** Extracts call and return messages from a sentence fragment and appends them to result. */
    private void extractSentenceMessages(String text, List<String> entities, List<String[]> result) {
        Matcher cm = CALL_PATTERN.matcher(text);
        while (cm.find()) {
            String from  = resolveCase(cm.group(1), entities);
            String to    = resolveCase(cm.group(2), entities);
            if (from == null || to == null || from.equals(to)) continue;
            String label = cm.group(3) != null
                ? sanitizeLabel(cm.group(3))
                : deriveCallLabel(cm.group(0));
            result.add(new String[]{from, to, label, "false"});
        }
        Matcher rm = RETURN_PATTERN.matcher(text);
        while (rm.find()) {
            String sender   = resolveCase(rm.group(1), entities);
            String receiver = resolveCase(rm.group(2), entities);
            if (sender == null || receiver == null || sender.equals(receiver)) continue;
            String label = rm.group(3) != null ? sanitizeLabel(rm.group(3)) : "response";
            result.add(new String[]{sender, receiver, label, "true"});
        }
    }

    private String resolveCase(String name, List<String> entities) {
        for (String e : entities) {
            if (e.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    private String sanitizeLabel(String raw) {
        return raw.replaceAll("[.!?;]", "").trim();
    }

    private String deriveCallLabel(String sentence) {
        // Extract key verb phrase after entity names (heuristic)
        Matcher m = Pattern.compile(
            "(?i)\\b(calls?|sends?|requests?|invokes?|asks?|notifies?)\\b.*?(\\w+(?:\\(\\))?)$")
                           .matcher(sentence.trim());
        if (m.find()) return m.group(2);
        return "call";
    }

    /** Normalises a condition string to use comparison operator symbols. */
    private String normalizeCondition(String condition) {
        if (condition == null || condition.isBlank()) return "condition";
        return condition
            .replaceAll("(?i)\\bexceeds?\\b", ">")
            .replaceAll("(?i)\\bis\\s+greater\\s+than\\b", ">")
            .replaceAll("(?i)\\bgreater\\s+than\\b", ">")
            .replaceAll("(?i)\\bis\\s+less\\s+than\\b", "<")
            .replaceAll("(?i)\\bless\\s+than\\b", "<")
            .replaceAll("(?i)\\bis\\s+equal\\s+to\\b", "==")
            .replaceAll("(?i)\\bequals?\\b", "==")
            .replaceAll("(?i)^(?:if|when)\\s+", "")
            .trim();
    }

    /** Returns {@code true} when the entity name represents a human actor rather than a system component. */
    private boolean isActorName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return Set.of("user", "admin", "administrator", "client", "customer",
                      "operator", "actor", "guest", "member", "visitor", "person")
                  .contains(lower);
    }

    private void appendDefault(StringBuilder sb) {
        sb.append("    actor User\n")
          .append("    participant AuthService\n")
          .append("    database Database\n")
          .append("\n")
          .append("    User->>AuthService: login(credentials)\n")
          .append("    activate AuthService\n")
          .append("    AuthService->>Database: findUser(username)\n")
          .append("    activate Database\n")
          .append("    Database-->>AuthService: userData\n")
          .append("    deactivate Database\n")
          .append("    AuthService-->>User: authToken\n")
          .append("    deactivate AuthService\n");
    }
}
