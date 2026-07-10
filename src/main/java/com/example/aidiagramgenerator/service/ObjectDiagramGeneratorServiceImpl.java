package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Default implementation of {@link ObjectDiagramGeneratorService}.
 *
 * <p>Parses both structured ({@code var : ClassName}) and natural language
 * ({@code "X is an instance of ClassName"}) object descriptions. Extracts
 * attribute assignments and inter-object links, then renders academically
 * correct PlantUML object diagram syntax.
 *
 * <h3>Supported instance declaration styles</h3>
 * <ul>
 *   <li>{@code var : ClassName} — direct PlantUML typed notation</li>
 *   <li>{@code ClassName instance var} — e.g. "Book instance b1"</li>
 *   <li>{@code var is an instance of ClassName}</li>
 *   <li>{@code var is a/an ClassName} — varName must start with lowercase</li>
 *   <li>{@code var is of type ClassName}</li>
 *   <li>{@code var of type ClassName}</li>
 * </ul>
 *
 * <h3>Supported attribute patterns</h3>
 * <ul>
 *   <li>{@code key = value} / {@code key=value} — inline assignment</li>
 *   <li>Inline "with" clause: {@code with balance=1500 and status="pending"}</li>
 *   <li>{@code X named Y} — produces {@code name = "Y"} on instance X</li>
 * </ul>
 *
 * <h3>Supported link patterns</h3>
 * <ul>
 *   <li>Explicit arrow: {@code src --> tgt : role}</li>
 *   <li>{@code X links Y to Z} — produces X→Y and X→Z</li>
 *   <li>Role phrases: {@code X is enrolled in Y}, {@code X belongs to Y},
 *       {@code X owned by Y}, etc.</li>
 *   <li>{@code X owns/contains/manages Y}</li>
 *   <li>{@code X references/points to Y}</li>
 * </ul>
 */
@Service
public class ObjectDiagramGeneratorServiceImpl implements ObjectDiagramGeneratorService {

    // ── Instance declaration patterns (evaluated in priority order) ────────

    /** {@code var : ClassName} — direct PlantUML typed notation */
    private static final Pattern EXPLICIT_TYPED = Pattern.compile(
            "\\b([a-z]\\w*)\\s*:\\s*([A-Z]\\w+)");

    /** {@code "var : ClassName"} — same but surrounded by double quotes */
    private static final Pattern QUOTED_TYPED = Pattern.compile(
            "\"([a-z]\\w*)\\s*:\\s*([A-Z]\\w+)\"");

    /** {@code ClassName instance varName} — e.g. "Loan instance l1" */
    private static final Pattern CLASS_INSTANCE = Pattern.compile(
            "\\b([A-Z]\\w+)\\s+instance\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** {@code varName is an instance of ClassName} */
    private static final Pattern INSTANCE_OF = Pattern.compile(
            "\\b(\\w+)\\s+is\\s+an?\\s+instance\\s+of\\s+([A-Z]\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** {@code varName is of type ClassName} */
    private static final Pattern IS_OF_TYPE = Pattern.compile(
            "\\b([a-z]\\w+)\\s+is\\s+of\\s+type\\s+([A-Z]\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** {@code varName of type ClassName} */
    private static final Pattern OF_TYPE = Pattern.compile(
            "\\b([a-z]\\w+)\\s+of\\s+type\\s+([A-Z]\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** {@code varName is a/an ClassName} — varName must start with lowercase */
    private static final Pattern IS_A = Pattern.compile(
            "\\b([a-z]\\w+)\\s+is\\s+an?\\s+([A-Z]\\w+)\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Attribute patterns ─────────────────────────────────────────────────

    /**
     * {@code key=value} or {@code key = value}.
     * Quoted values ({@code "..."} or {@code '...'}) are captured in full;
     * unquoted values extend to the next comma, semicolon, or newline.
     */
    private static final Pattern KV_EQUALS = Pattern.compile(
            "\\b([a-zA-Z]\\w*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^,;=\\s\"'][^,;\\n\"']*)");

    /** {@code X named Y} — assigns {@code name = "Y"} to instance X (or its class) */
    private static final Pattern NAMED_CLAUSE = Pattern.compile(
            "\\b(\\w+)\\s+named\\s+([\\w][^,.;\\n]*?)(?=[,.;\\n]|$)",
            Pattern.CASE_INSENSITIVE);

    // ── Link patterns ──────────────────────────────────────────────────────

    /** Explicit arrow: {@code src --> tgt} or {@code src --> tgt : role} */
    private static final Pattern EXPLICIT_ARROW = Pattern.compile(
            "\\b(\\w+)\\s*-+>\\s*(\\w+)(?:\\s*:\\s*([^\\n]+))?");

    /** {@code X links Y to Z} — produces X→Y and X→Z */
    private static final Pattern LINKS_TO = Pattern.compile(
            "\\b(\\w+)\\s+links?\\s+(\\w+)\\s+to\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** Role phrases: {@code X is enrolled in/belongs to/owned by/... Y} */
    private static final Pattern ROLE_PHRASE = Pattern.compile(
            "\\b(\\w+)\\s+(?:is\\s+)?(enrolled\\s+in|linked\\s+to|associated\\s+with" +
            "|part\\s+of|owned\\s+by|belongs\\s+to|placed\\s+by|contained\\s+in|managed\\s+by)\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    /** {@code X owns/contains/manages Y} */
    private static final Pattern OWNS_CONTAINS = Pattern.compile(
            "\\b(\\w+)\\s+(?:owns|contains|manages)\\s+(\\w+)\\b",
            Pattern.CASE_INSENSITIVE);

    /** {@code X references/points to Y} */
    private static final Pattern REFERENCES = Pattern.compile(
            "\\b(\\w+)\\s+(?:references?|points?\\s+to)\\s+(\\w+)\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Reserved words ─────────────────────────────────────────────────────

    /**
     * Words that must never be treated as instance variable names or attribute keys.
     * Intentionally excludes valid UML attribute names such as
     * {@code name}, {@code status}, {@code id}, {@code balance}, {@code date}.
     */
    private static final Set<String> RESERVED = Set.of(
            // PlantUML structural keywords
            "object", "class", "interface", "abstract", "note", "as",
            "startuml", "enduml", "skinparam", "newpage",
            // English stop words (cannot be meaningful identifiers)
            "the", "a", "an", "is", "are", "was", "were",
            "has", "have", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "must", "shall",
            "and", "or", "but", "not", "yet", "so",
            "in", "on", "at", "by", "to", "from", "for", "of", "with", "into",
            "that", "this", "these", "those", "which", "who", "what", "where",
            "when", "how", "there", "here",
            "if", "then", "else", "each", "both", "all", "some", "many",
            "its", "their", "our", "your", "his", "her", "they", "them",
            "also", "above", "below", "instance", "diagram", "uml",
            "snapshot", "runtime", "link", "links"
    );

    // ──────────────────────────────────────────────────────────────────────

    @Override
    public String generateObjectDiagram(String text) {
        if (text == null || text.isBlank()) {
            return defaultDiagram();
        }

        List<ObjectInstance> instances = extractInstances(text);
        if (instances.isEmpty()) {
            return defaultDiagram();
        }

        Map<String, List<String>> attrs = extractAttributes(text, instances);
        List<ObjectLink> links = extractLinks(text, instances);
        return render(instances, attrs, links);
    }

    // ── Instance extraction ────────────────────────────────────────────────

    /**
     * Extracts all typed object instances from {@code text}.
     * Patterns are evaluated in priority order; the first declaration of a given
     * {@code varName} wins.
     */
    List<ObjectInstance> extractInstances(String text) {
        // LinkedHashMap preserves declaration order and deduplicates by varName.
        Map<String, ObjectInstance> seen = new LinkedHashMap<>();

        applyInstancePattern(QUOTED_TYPED,   text, seen, 1, 2);
        applyInstancePattern(EXPLICIT_TYPED, text, seen, 1, 2);
        applyInstancePattern(CLASS_INSTANCE, text, seen, 2, 1);  // var=group2, class=group1
        applyInstancePattern(INSTANCE_OF,    text, seen, 1, 2);
        applyInstancePattern(IS_OF_TYPE,     text, seen, 1, 2);
        applyInstancePattern(OF_TYPE,        text, seen, 1, 2);
        applyInstancePattern(IS_A,           text, seen, 1, 2);

        return new ArrayList<>(seen.values());
    }

    private void applyInstancePattern(Pattern pattern, String text,
                                       Map<String, ObjectInstance> seen,
                                       int varGroup, int classGroup) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String varName   = m.group(varGroup).trim();
            String className = m.group(classGroup).trim();
            if (!isReserved(varName) && !isReserved(className)
                    && !varName.equalsIgnoreCase(className)) {
                seen.putIfAbsent(varName, new ObjectInstance(varName, className));
            }
        }
    }

    // ── Attribute extraction ───────────────────────────────────────────────

    /**
     * Extracts {@code key = value} attributes from each sentence and assigns them
     * to the most relevant instance in that sentence.
     *
     * <p>Assignment rules:
     * <ol>
     *   <li>Find the instance whose {@code varName} appears earliest in the sentence
     *       (highest priority).</li>
     *   <li>Fall back to the instance whose {@code className} appears earliest.</li>
     *   <li>If neither matches, use the last instance that was the context of a
     *       previous sentence.</li>
     * </ol>
     */
    Map<String, List<String>> extractAttributes(String text, List<ObjectInstance> instances) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (ObjectInstance inst : instances) {
            result.put(inst.varName(), new ArrayList<>());
        }

        Set<String> varNamesLower   = new HashSet<>();
        Set<String> classNamesLower = new HashSet<>();
        for (ObjectInstance inst : instances) {
            varNamesLower.add(inst.varName().toLowerCase());
            classNamesLower.add(inst.className().toLowerCase());
        }

        ObjectInstance lastContext = null;

        for (String sentence : splitSentences(text)) {
            ObjectInstance context = findContextInstance(sentence, instances);
            if (context != null) {
                lastContext = context;
            } else {
                context = lastContext;
            }
            if (context == null) continue;

            // key=value attributes
            Matcher km = KV_EQUALS.matcher(sentence);
            while (km.find()) {
                String key = km.group(1).trim();
                String val = km.group(2).trim();
                if (isReserved(key)
                        || varNamesLower.contains(key.toLowerCase())
                        || classNamesLower.contains(key.toLowerCase())
                        || key.length() < 2) {
                    continue;
                }
                addAttribute(result, context.varName(), key + " = " + formatValue(val));
            }

            // "X named Y" → name = "Y"
            Matcher nm = NAMED_CLAUSE.matcher(sentence);
            while (nm.find()) {
                String subject = nm.group(1).trim();
                String nameVal = nm.group(2).trim();
                // Assign to the instance that matches subject (by varName or className),
                // falling back to current context.
                ObjectInstance target = findByVarOrClass(subject, instances);
                if (target == null) target = context;
                addAttribute(result, target.varName(), "name = " + formatValue(nameVal));
            }
        }

        return result;
    }

    private void addAttribute(Map<String, List<String>> result, String varName, String formatted) {
        List<String> attrs = result.get(varName);
        if (attrs != null && !attrs.contains(formatted)) {
            attrs.add(formatted);
        }
    }

    // ── Link extraction ────────────────────────────────────────────────────

    /**
     * Extracts directed links between known instances from {@code text}.
     * Only links where at least one endpoint is a declared instance are considered;
     * role-based phrases require both endpoints to be known.
     */
    List<ObjectLink> extractLinks(String text, List<ObjectInstance> instances) {
        Set<String> varNamesLower = new HashSet<>();
        for (ObjectInstance inst : instances) {
            varNamesLower.add(inst.varName().toLowerCase());
        }

        List<ObjectLink> result   = new ArrayList<>();
        Set<String>      emitted  = new LinkedHashSet<>();

        // 1. Explicit arrows: "src --> tgt" or "src --> tgt : role"
        Matcher m = EXPLICIT_ARROW.matcher(text);
        while (m.find()) {
            String src  = m.group(1);
            String tgt  = m.group(2);
            String role = m.group(3) != null ? m.group(3).trim() : null;
            if (varNamesLower.contains(src.toLowerCase())
                    && varNamesLower.contains(tgt.toLowerCase())) {
                addLink(result, emitted, new ObjectLink(src, tgt, role));
            }
        }

        // 2. "X links Y to Z" → X→Y and X→Z
        m = LINKS_TO.matcher(text);
        while (m.find()) {
            String linker = m.group(1);
            String y      = m.group(2);
            String z      = m.group(3);
            if (varNamesLower.contains(linker.toLowerCase())) {
                if (varNamesLower.contains(y.toLowerCase())) {
                    addLink(result, emitted, new ObjectLink(linker, y, null));
                }
                if (varNamesLower.contains(z.toLowerCase())) {
                    addLink(result, emitted, new ObjectLink(linker, z, null));
                }
            }
        }

        // 3. Role phrases: "X is enrolled in Y", "X belongs to Y", etc.
        m = ROLE_PHRASE.matcher(text);
        while (m.find()) {
            String src  = m.group(1);
            String role = m.group(2).replaceAll("\\s+", " ").trim();
            String tgt  = m.group(3);
            if (varNamesLower.contains(src.toLowerCase())
                    && varNamesLower.contains(tgt.toLowerCase())) {
                addLink(result, emitted, new ObjectLink(src, tgt, role));
            }
        }

        // 4. "X owns/contains/manages Y"
        m = OWNS_CONTAINS.matcher(text);
        while (m.find()) {
            String src = m.group(1);
            String tgt = m.group(2);
            if (varNamesLower.contains(src.toLowerCase())
                    && varNamesLower.contains(tgt.toLowerCase())) {
                addLink(result, emitted, new ObjectLink(src, tgt, "contains"));
            }
        }

        // 5. "X references/points to Y"
        m = REFERENCES.matcher(text);
        while (m.find()) {
            String src = m.group(1);
            String tgt = m.group(2);
            if (varNamesLower.contains(src.toLowerCase())
                    && varNamesLower.contains(tgt.toLowerCase())) {
                addLink(result, emitted, new ObjectLink(src, tgt, "references"));
            }
        }

        // Auto-chain consecutive instances when no links were found
        if (result.isEmpty() && instances.size() >= 2) {
            for (int i = 0; i < instances.size() - 1; i++) {
                addLink(result, emitted,
                        new ObjectLink(instances.get(i).varName(),
                                       instances.get(i + 1).varName(), null));
            }
        }

        return result;
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private String render(List<ObjectInstance> instances,
                          Map<String, List<String>> attrs,
                          List<ObjectLink> links) {
        StringBuilder sb = new StringBuilder("@startuml\n");

        for (ObjectInstance inst : instances) {
            List<String> instAttrs = attrs.getOrDefault(inst.varName(), List.of());
            if (instAttrs.isEmpty()) {
                // Render a compact header-only block (valid PlantUML and cleaner academically)
                sb.append("object \"").append(inst.varName())
                  .append(" : ").append(inst.className())
                  .append("\" as ").append(inst.varName()).append("\n");
            } else {
                sb.append("object \"").append(inst.varName())
                  .append(" : ").append(inst.className())
                  .append("\" as ").append(inst.varName()).append(" {\n");
                instAttrs.forEach(a -> sb.append("  ").append(a).append("\n"));
                sb.append("}\n");
            }
        }

        if (!links.isEmpty()) {
            sb.append("\n");
            for (ObjectLink link : links) {
                sb.append(link.source()).append(" --> ").append(link.target());
                if (link.role() != null && !link.role().isBlank()) {
                    sb.append(" : ").append(link.role());
                }
                sb.append("\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    // ── Default diagram ────────────────────────────────────────────────────

    String defaultDiagram() {
        return "@startuml\n" +
               "object \"dogD : Dog\" as dogD {\n" +
               "  name = \"Wolfy\"\n" +
               "  pedigree = true\n" +
               "}\n" +
               "object \"owner1 : Person\" as owner1 {\n" +
               "  name = \"Alice\"\n" +
               "}\n" +
               "object \"order1 : Order\" as order1 {\n" +
               "  total = 49.99\n" +
               "  status = \"pending\"\n" +
               "}\n" +
               "\n" +
               "dogD --> owner1 : ownedBy\n" +
               "owner1 --> order1 : placed\n" +
               "@enduml";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Splits {@code text} into sentences.
     * Splits on {@code .}, {@code !}, {@code ?} only when followed by whitespace
     * (avoiding splits on decimal numbers like {@code 49.99}) and also on newlines.
     */
    private List<String> splitSentences(String text) {
        String[] parts = text.split("(?<=[.!?])(?=\\s)|\\n");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.replaceAll("[.!?\\s]+$", "").replaceAll("^\\s+", "").trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? List.of(text.trim()) : result;
    }

    /**
     * Finds the instance whose {@code varName} appears earliest in {@code sentence}.
     * Falls back to the instance whose {@code className} appears earliest.
     * Returns {@code null} if neither is found.
     */
    private ObjectInstance findContextInstance(String sentence, List<ObjectInstance> instances) {
        String lower = sentence.toLowerCase(Locale.ROOT);

        // 1. VarName match (higher priority — more specific)
        int bestVarPos = Integer.MAX_VALUE;
        ObjectInstance bestVarInst = null;
        for (ObjectInstance inst : instances) {
            int pos = indexOfWholeWord(lower, inst.varName().toLowerCase(Locale.ROOT));
            if (pos >= 0 && pos < bestVarPos) {
                bestVarPos = pos;
                bestVarInst = inst;
            }
        }
        if (bestVarInst != null) return bestVarInst;

        // 2. ClassName match (lower priority)
        int bestClassPos = Integer.MAX_VALUE;
        ObjectInstance bestClassInst = null;
        for (ObjectInstance inst : instances) {
            int pos = lower.indexOf(inst.className().toLowerCase(Locale.ROOT));
            if (pos >= 0 && pos < bestClassPos) {
                bestClassPos = pos;
                bestClassInst = inst;
            }
        }
        return bestClassInst;
    }

    /** Returns the start index of {@code word} as a whole word in {@code text}, or -1. */
    private int indexOfWholeWord(String text, String word) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
        return m.find() ? m.start() : -1;
    }

    /**
     * Finds the instance whose {@code varName} or {@code className} equals {@code token}
     * (case-insensitive). Returns {@code null} if not found.
     */
    private ObjectInstance findByVarOrClass(String token, List<ObjectInstance> instances) {
        for (ObjectInstance inst : instances) {
            if (inst.varName().equalsIgnoreCase(token)
                    || inst.className().equalsIgnoreCase(token)) {
                return inst;
            }
        }
        return null;
    }

    private void addLink(List<ObjectLink> result, Set<String> emitted, ObjectLink link) {
        String key = link.source().toLowerCase(Locale.ROOT)
                + "|" + link.target().toLowerCase(Locale.ROOT)
                + "|" + (link.role() != null ? link.role().toLowerCase(Locale.ROOT) : "");
        if (emitted.add(key)) {
            result.add(link);
        }
    }

    /**
     * Formats a raw attribute value for PlantUML:
     * <ul>
     *   <li>Already-quoted strings → returned as-is (normalised to double quotes)</li>
     *   <li>Numeric values → unquoted</li>
     *   <li>{@code true} / {@code false} / {@code null} → unquoted</li>
     *   <li>Everything else → wrapped in double quotes</li>
     * </ul>
     */
    private String formatValue(String raw) {
        if (raw == null || raw.isBlank()) return "\"\"";
        String s = raw.trim();
        // Already double-quoted
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) return s;
        // Already single-quoted — convert to double
        if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
            return "\"" + s.substring(1, s.length() - 1) + "\"";
        }
        // Numeric (integer or decimal)
        if (s.matches("-?\\d+(\\.\\d+)?")) return s;
        // Boolean / null literals
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("false") || lower.equals("null")) return lower;
        // Default: quote the value
        return "\"" + s + "\"";
    }

    private boolean isReserved(String word) {
        return word == null || RESERVED.contains(word.toLowerCase(Locale.ROOT));
    }

    // ── Domain records ─────────────────────────────────────────────────────

    /** An object instance in the diagram: a variable name and its class type. */
    record ObjectInstance(String varName, String className) {}

    /** A directed link between two instances, with an optional role label. */
    record ObjectLink(String source, String target, String role) {}
}
