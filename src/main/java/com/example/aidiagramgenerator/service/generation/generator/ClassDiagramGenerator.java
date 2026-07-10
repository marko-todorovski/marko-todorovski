package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.service.generation.DiagramGenerator;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;


/**
 * Generates Mermaid class-diagram syntax from parsed input.
 * Performs semantic detection of inheritance, composition, aggregation, dependency,
 * multiplicity, typed attributes, and method signatures from raw text.
 */
@Component
public class ClassDiagramGenerator implements DiagramGenerator {

    // ── Relationship patterns (most-specific / longest first) ────────────────

    private static final List<RelPattern> REL_PATTERNS = List.of(
        rp("(?i)\\b(\\w+)\\s+(?:inherits from|is a subclass of|is a subtype of|is a type of)\\s+(\\w+)", "inheritance"),
        rp("(?i)\\b(\\w+)\\s+(?:inherits|extends|implements)\\s+(\\w+)", "inheritance"),
        rp("(?i)\\b(\\w+)\\s+is an?\\s+(\\w+)", "inheritance"),
        // composition: strong ownership — lifecycle dependency between whole and part
        rp("(?i)\\b(\\w+)\\s+(?:contains an?|owns an?|is composed of|is made up of)\\s+(\\w+)", "composition"),
        rp("(?i)\\b(\\w+)\\s+(?:contains|owns|composes|is part of|part of)\\s+(\\w+)", "composition"),
        // aggregation: weak ownership — "has a", "has"
        rp("(?i)\\b(\\w+)\\s+(?:aggregates|consists of)\\s+(\\w+)", "aggregation"),
        rp("(?i)\\b(\\w+)\\s+(?:depends on|depends upon|uses|calls|invokes)\\s+(\\w+)", "dependency"),
        rp("(?i)\\b(\\w+)\\s+(?:has an?|has|manages|references)\\s+(\\w+)", "aggregation"),
        // association: navigable link without ownership
        rp("(?i)\\b(\\w+)\\s+owned by\\s+(\\w+)", "association")
    );

    // Multiplicity: "one X can take many Y"
    private static final Pattern ONE_TO_MANY = Pattern.compile(
        "(?i)\\bone\\s+(\\w+)\\s+(?:can\\s+)?(?:have|has|take|takes|contain|contains|include|includes)\\s+" +
        "(?:many|multiple|several)\\s+(\\w+)");
    // Multiplicity: "many Xs belong to one Y"
    private static final Pattern MANY_TO_ONE = Pattern.compile(
        "(?i)\\b(?:many|multiple|several)\\s+(\\w+?)s?\\s+(?:belong to|are in|are owned by)\\s+" +
        "(?:one|a single|a)\\s+(\\w+)");
    // Explicit phrase: "X one-to-many Y" / "X one to many Y" / "X one or many Y"
    private static final Pattern PHRASE_ONE_TO_MANY = Pattern.compile(
        "(?i)\\b(\\w+)\\s+(?:one[- ]to[- ]many|one\\s+or\\s+many)\\s+(\\w+)");
    // Explicit phrase: "X many-to-many Y"
    private static final Pattern PHRASE_MANY_TO_MANY = Pattern.compile(
        "(?i)\\b(\\w+)\\s+many[- ]to[- ]many\\s+(\\w+)");
    // Explicit phrase: "X one-to-one Y"
    private static final Pattern PHRASE_ONE_TO_ONE = Pattern.compile(
        "(?i)\\b(\\w+)\\s+one[- ]to[- ]one\\s+(\\w+)");
    // "X has/contains multiple/several/various/numerous Y"
    private static final Pattern HAS_MULTIPLE = Pattern.compile(
        "(?i)\\b(\\w+)\\s+(?:has|have|contain|contains|owns?|manage|manages?)\\s+" +
        "(?:multiple|several|various|numerous)\\s+(\\w+)");
    // Inline UML notation: "X 1..* Y", "X 0..1 Y", "X 0..* Y"
    private static final Pattern INLINE_UML_MULT = Pattern.compile(
        "(?i)\\b(\\w+)\\s+([0-9]+\\.\\.(?:[0-9]+|\\*))\\s+(\\w+)");

    // Method signature: visibility? returnType name(params)
    private static final Pattern METHOD_SIG = Pattern.compile(
        "([+\\-#~])?\\s*(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)");

    // "Entity with/has attr1, attr2"
    private static final Pattern ENTITY_WITH_ATTRS = Pattern.compile(
        "(?i)\\b(\\w+)\\s+with\\s+([a-z][a-zA-Z0-9,\\s]+?)(?=[.!?]|$)");

    private static final Set<String> JAVA_TYPES = Set.of(
        "String", "int", "long", "double", "float", "boolean",
        "Integer", "Long", "Double", "Float", "Boolean", "List",
        "Map", "Set", "Object", "Date", "UUID", "char", "byte",
        "short", "BigDecimal", "void"
    );

    /** Academic type inference: maps common attribute name patterns to their UML type. */
    private static final Map<String, String> ATTR_TYPE_MAP = Map.ofEntries(
        // identity / numeric
        Map.entry("id",          "int"),
        Map.entry("count",       "int"),
        Map.entry("quantity",    "int"),
        Map.entry("age",         "int"),
        Map.entry("year",        "int"),
        Map.entry("size",        "int"),
        Map.entry("number",      "int"),
        Map.entry("rank",        "int"),
        Map.entry("score",       "int"),
        Map.entry("credits",     "int"),
        Map.entry("capacity",    "int"),
        // money / floating
        Map.entry("price",       "double"),
        Map.entry("amount",      "double"),
        Map.entry("balance",     "double"),
        Map.entry("salary",      "double"),
        Map.entry("total",       "double"),
        Map.entry("cost",        "double"),
        Map.entry("rate",        "double"),
        Map.entry("weight",      "double"),
        Map.entry("height",      "double"),
        Map.entry("length",      "double"),
        // boolean flags
        Map.entry("isactive",    "boolean"),
        Map.entry("isenabled",   "boolean"),
        Map.entry("isclosed",    "boolean"),
        Map.entry("isopen",      "boolean"),
        Map.entry("isdeleted",   "boolean"),
        Map.entry("ispublished", "boolean"),
        Map.entry("available",   "boolean"),
        Map.entry("active",      "boolean"),
        Map.entry("enabled",     "boolean"),
        // date/time
        Map.entry("date",        "Date"),
        Map.entry("createdat",   "Date"),
        Map.entry("updatedat",   "Date"),
        Map.entry("timestamp",   "Date"),
        Map.entry("birthdate",   "Date"),
        Map.entry("startdate",   "Date"),
        Map.entry("enddate",     "Date"),
        Map.entry("duedate",     "Date"),
        Map.entry("deadline",    "Date"),
        // common strings (explicit to avoid overfitting)
        Map.entry("name",        "String"),
        Map.entry("title",       "String"),
        Map.entry("description", "String"),
        Map.entry("label",       "String"),
        Map.entry("email",       "String"),
        Map.entry("phone",       "String"),
        Map.entry("address",     "String"),
        Map.entry("url",         "String"),
        Map.entry("isbn",        "String"),
        Map.entry("code",        "String"),
        Map.entry("type",        "String"),
        Map.entry("status",      "String"),
        Map.entry("color",       "String"),
        Map.entry("username",    "String"),
        Map.entry("password",    "String"),
        Map.entry("token",       "String"),
        Map.entry("content",     "String"),
        Map.entry("text",        "String"),
        Map.entry("format",      "String")
    );

    /** Abstract patterns: "abstract class X", "X is abstract", "abstract X" */
    private static final Pattern ABSTRACT_PATTERN = Pattern.compile(
        "(?i)\\babstract\\s+(?:class\\s+)?(\\w+)|\\b(\\w+)\\s+is\\s+abstract\\b");

    /** Interface patterns: "interface X", "X interface", "X is an interface" */
    private static final Pattern INTERFACE_PATTERN = Pattern.compile(
        "(?i)\\binterface\\s+(\\w+)|\\b(\\w+)\\s+interface\\b|\\b(\\w+)\\s+is\\s+an?\\s+interface\\b");

    @Override
    public DiagramType supports() {
        return DiagramType.CLASS;
    }

    @Override
    public String generate(ParsedInput parsedInput) {
        String raw = parsedInput.getRawContent();
        List<String> entityList = parsedInput.getEntities();
        List<String> relStrings = parsedInput.getRelationships();

        Set<String> entitySet = new LinkedHashSet<>(entityList);
        StringBuilder sb = new StringBuilder("classDiagram\n");

        if (entitySet.isEmpty()) {
            appendDefault(sb);
            return sb.toString().stripTrailing();
        }

        // Detect abstract classes and interfaces from raw text
        Set<String> abstractClasses = detectAbstractClasses(raw, entitySet);
        Set<String> interfaces      = detectInterfaces(raw, entitySet);

        Map<String, List<String>> members = parseMembers(raw, entitySet);
        List<ClassRelLink> links = parseLinks(raw, relStrings, entitySet);

        for (String entity : entitySet) {
            String stereotype = interfaces.contains(entity) ? "interface"
                              : abstractClasses.contains(entity) ? "abstract"
                              : null;
            appendClassBlock(sb, entity, members.getOrDefault(entity, Collections.emptyList()),
                             stereotype);
        }

        if (links.isEmpty() && entitySet.size() >= 2) {
            List<String> names = new ArrayList<>(entitySet);
            for (int i = 0; i < names.size() - 1; i++) {
                sb.append("    ").append(names.get(i))
                  .append(" --> ").append(names.get(i + 1)).append("\n");
            }
        } else {
            for (ClassRelLink link : links) {
                sb.append("    ").append(link.toMermaid()).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    // ── Class block rendering ─────────────────────────────────────────────────

    private void appendClassBlock(StringBuilder sb, String entity, List<String> members,
                                  String stereotype) {
        if (members.isEmpty() && stereotype == null) {
            sb.append("    class ").append(entity).append("\n");
        } else {
            sb.append("    class ").append(entity).append(" {\n");
            if (stereotype != null) {
                sb.append("        <<").append(stereotype).append(">>\n");
            }
            for (String m : members) {
                sb.append("        ").append(m).append("\n");
            }
            sb.append("    }\n");
        }
    }

    // ── Member parsing ────────────────────────────────────────────────────────

    private Map<String, List<String>> parseMembers(String raw, Set<String> entities) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        // "Entity with attr1, attr2, ..."
        Matcher withMatcher = ENTITY_WITH_ATTRS.matcher(raw);
        while (withMatcher.find()) {
            String entity = withMatcher.group(1);
            if (!entities.contains(entity)) continue;
            List<String> list = result.computeIfAbsent(entity, k -> new ArrayList<>());
            for (String part : withMatcher.group(2).split("[,]|\\band\\b")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                String formatted = trimmed.contains("(") ? formatRawMethod(trimmed) : formatRawAttr(trimmed);
                if (formatted != null && !list.contains(formatted)) list.add(formatted);
            }
        }

        // Typed method signatures in sentences that mention specific entities
        for (String entity : entities) {
            for (String sentence : raw.split("[.!?\\n]")) {
                if (!sentence.contains(entity)) continue;
                List<String> list = result.computeIfAbsent(entity, k -> new ArrayList<>());
                extractTypedMethods(sentence, list);
            }
        }

        return result;
    }

    private void extractTypedMethods(String text, List<String> members) {
        Matcher m = METHOD_SIG.matcher(text);
        while (m.find()) {
            String vis = m.group(1);
            String returnType = m.group(2);
            String name = m.group(3);
            String params = m.group(4);
            if (!JAVA_TYPES.contains(returnType) && vis == null) continue;
            String v = vis != null ? vis : (isPublicByConvention(name) ? "+" : "-");
            // Mark abstract methods — detected when raw text contains "abstract" near this method
            boolean isAbstract = text.toLowerCase().contains("abstract " + name)
                              || text.toLowerCase().contains("abstract method " + name);
            String formatted = v + returnType + " " + name + "(" + params.trim() + ")"
                              + (isAbstract ? "*" : "");
            if (!members.contains(formatted)) members.add(formatted);
        }
    }

    private String formatRawAttr(String raw) {
        raw = raw.trim();
        if (raw.matches("[+\\-#~].*")) return raw;
        // "name: String" or "name : String" format with explicit type
        if (raw.contains(":")) {
            String[] parts = raw.split("\\s*:\\s*", 2);
            String attrName = parts[0].trim();
            String type = parts.length > 1 ? parts[1].trim() : inferType(attrName);
            char vis = isPrivateByConvention(attrName) ? '-' : '+';
            return vis + type + " " + attrName;
        }
        // Infer type from attribute name
        String attrName = raw.toLowerCase().replace(" ", "_");
        String type = inferType(attrName);
        char vis = isPrivateByConvention(attrName) ? '-' : '+';
        return vis + type + " " + attrName;
    }

    /**
     * Infers the UML attribute type from a common attribute name.
     * Falls back to {@code String} when name is unknown.
     */
    private String inferType(String attrNameRaw) {
        String key = attrNameRaw.toLowerCase().replace("_", "").replace(" ", "");
        String mapped = ATTR_TYPE_MAP.get(key);
        if (mapped != null) return mapped;
        // Pattern-based fallbacks
        if (key.startsWith("is") || key.startsWith("has") || key.startsWith("can")) return "boolean";
        if (key.endsWith("count") || key.endsWith("number") || key.endsWith("id")
                || key.endsWith("index") || key.endsWith("age")
                || key.endsWith("year"))  return "int";
        if (key.endsWith("price") || key.endsWith("amount")
                || key.endsWith("balance") || key.endsWith("total")) return "double";
        if (key.endsWith("date") || key.endsWith("time") || key.endsWith("at")) return "Date";
        return "String";
    }

    private String formatRawMethod(String raw) {
        raw = raw.trim();
        if (raw.matches("[+\\-#~].*")) return raw;
        return "+" + raw;
    }

    private boolean isPrivateByConvention(String name) {
        return name.equals("id") || name.equals("password") || name.equals("token")
            || name.equals("secret") || name.equals("hash") || name.startsWith("_");
    }

    private boolean isPublicByConvention(String name) {
        return name.startsWith("get") || name.startsWith("set") || name.startsWith("is")
            || name.startsWith("has") || name.equals("toString");
    }

    // ── Relationship parsing ──────────────────────────────────────────────────

    private List<ClassRelLink> parseLinks(String raw, List<String> relStrings, Set<String> entities) {
        List<ClassRelLink> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Semantic patterns from raw text
        for (RelPattern rp : REL_PATTERNS) {
            Matcher m = rp.pattern.matcher(raw);
            while (m.find()) {
                String src = resolveEntity(m.group(1), entities);
                String tgt = resolveEntity(m.group(2), entities);
                if (src == null || tgt == null || src.equals(tgt)) continue;
                if (seen.add(src + "|" + tgt)) {
                    links.add(new ClassRelLink(src, tgt, rp.type, null, null));
                }
            }
        }

        // Multiplicity: "one X can take many Y"
        Matcher m1 = ONE_TO_MANY.matcher(raw);
        while (m1.find()) {
            String src = resolveEntity(capitalize(m1.group(1)), entities);
            String tgt = resolveEntityPlural(m1.group(2), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "1", "0..*"));
            }
        }

        // Multiplicity: "many Xs belong to one Y"
        Matcher mRev = MANY_TO_ONE.matcher(raw);
        while (mRev.find()) {
            String src = resolveEntityPlural(mRev.group(1), entities);
            String tgt = resolveEntity(capitalize(mRev.group(2)), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "0..*", "1"));
            }
        }

        // Explicit phrase: "X one-to-many Y"
        Matcher mOtM = PHRASE_ONE_TO_MANY.matcher(raw);
        while (mOtM.find()) {
            String src = resolveEntity(mOtM.group(1), entities);
            String tgt = resolveEntityPlural(mOtM.group(2), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "1", "0..*"));
            }
        }

        // Explicit phrase: "X many-to-many Y"
        Matcher mMtM = PHRASE_MANY_TO_MANY.matcher(raw);
        while (mMtM.find()) {
            String src = resolveEntityPlural(mMtM.group(1), entities);
            String tgt = resolveEntityPlural(mMtM.group(2), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "0..*", "0..*"));
            }
        }

        // Explicit phrase: "X one-to-one Y"
        Matcher mOtO = PHRASE_ONE_TO_ONE.matcher(raw);
        while (mOtO.find()) {
            String src = resolveEntity(mOtO.group(1), entities);
            String tgt = resolveEntity(mOtO.group(2), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "1", "1"));
            }
        }

        // "X has multiple Y"
        Matcher mHM = HAS_MULTIPLE.matcher(raw);
        while (mHM.find()) {
            String src = resolveEntity(capitalize(mHM.group(1)), entities);
            String tgt = resolveEntityPlural(mHM.group(2), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "aggregation", "1", "0..*"));
            }
        }

        // Inline UML notation: "X 1..* Y", "X 0..1 Y"
        Matcher mInl = INLINE_UML_MULT.matcher(raw);
        while (mInl.find()) {
            String src = resolveEntity(mInl.group(1), entities);
            String mult = mInl.group(2);
            String tgt = resolveEntityPlural(mInl.group(3), entities);
            if (src == null || tgt == null || src.equals(tgt)) continue;
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, "association", "1", mult));
            }
        }

        // From parsed relStrings: "A -> B : label"
        for (String rel : relStrings) {
            Matcher rm = Pattern.compile("(\\w+)\\s*->\\s*(\\w+)(?:\\s*:\\s*(.+))?").matcher(rel);
            if (!rm.find()) continue;
            String src = resolveEntity(rm.group(1), entities);
            String tgt = resolveEntity(rm.group(2), entities);
            if (src == null || tgt == null) continue;
            String type = labelToType(rm.group(3));
            if (seen.add(src + "|" + tgt)) {
                links.add(new ClassRelLink(src, tgt, type, null, null));
            }
        }

        return links;
    }

    private String resolveEntity(String name, Set<String> entities) {
        if (name == null) return null;
        if (entities.contains(name)) return name;
        String cap = capitalize(name);
        return entities.contains(cap) ? cap : null;
    }

    private String resolveEntityPlural(String name, Set<String> entities) {
        String resolved = resolveEntity(name, entities);
        if (resolved != null) return resolved;
        String singular = (name.endsWith("s") && name.length() > 2)
            ? name.substring(0, name.length() - 1) : name;
        return resolveEntity(capitalize(singular), entities);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String labelToType(String label) {
        if (label == null) return "association";
        String lower = label.toLowerCase();
        if (lower.contains("inherit") || lower.contains("extend") || lower.contains("implement") || lower.contains("is a type of") || lower.contains("is a")) return "inheritance";
        if (lower.contains("compos") || lower.contains("part of")) return "composition";
        if (lower.contains("has a")) return "aggregation";
        if (lower.contains("aggregat")) return "aggregation";
        if (lower.contains("use") || lower.contains("depend") || lower.contains("call")) return "dependency";
        return "association";
    }

    // ── Default fallback ──────────────────────────────────────────────────────

    private Set<String> detectAbstractClasses(String raw, Set<String> entities) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        Matcher m = ABSTRACT_PATTERN.matcher(raw);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (name != null) {
                String resolved = resolveEntity(name, entities);
                if (resolved != null) result.add(resolved);
            }
        }
        return result;
    }

    private Set<String> detectInterfaces(String raw, Set<String> entities) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        Matcher m = INTERFACE_PATTERN.matcher(raw);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1)
                        : m.group(2) != null ? m.group(2) : m.group(3);
            if (name != null) {
                String resolved = resolveEntity(name, entities);
                if (resolved != null) result.add(resolved);
            }
        }
        return result;
    }

    private void appendDefault(StringBuilder sb) {
        sb.append("    class User {\n")
          .append("        -int id\n")
          .append("        -String username\n")
          .append("        -String email\n")
          .append("        +login() boolean\n")
          .append("        +logout() void\n")
          .append("    }\n")
          .append("    class AuthService {\n")
          .append("        +authenticate(String username) String\n")
          .append("        +validateToken(String token) boolean\n")
          .append("    }\n")
          .append("    class Database {\n")
          .append("        +query(String sql) List\n")
          .append("        +save(Object entity) void\n")
          .append("    }\n")
          .append("    User ..> AuthService : uses\n")
          .append("    AuthService ..> Database : queries\n");
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record RelPattern(Pattern pattern, String type) {}

    private static RelPattern rp(String regex, String type) {
        return new RelPattern(Pattern.compile(regex), type);
    }

    private record ClassRelLink(
        String source, String target, String type,
        String srcMult, String tgtMult
    ) {
        String toMermaid() {
            String arrow = switch (type) {
                case "inheritance" -> "<|--";
                case "composition" -> "*--";
                case "aggregation" -> "o--";
                case "dependency"  -> "..>";
                case "realization" -> "..|>";
            default            -> "--";            };
            if ("inheritance".equals(type) || "realization".equals(type)) {
                // PlantUML/Mermaid: Parent <|-- Child (child IS-A parent)
                return target + " " + arrow + " " + source;
            }
            if (srcMult != null) {
                return source + " \"" + srcMult + "\" " + arrow + " \"" + (tgtMult != null ? tgtMult : "*") + "\" " + target;
            }
            return source + " " + arrow + " " + target;
        }
    }
}
