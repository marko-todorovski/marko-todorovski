package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link ComponentDiagramGeneratorService}.
 *
 * <p>Parses natural language or semi-structured text describing software components and
 * generates academically correct PlantUML component diagram syntax.
 *
 * <h3>Supported input styles</h3>
 * <ul>
 *   <li><b>Explicit PlantUML:</b> {@code component "Web Browser"}, {@code database "MySQL"},
 *       {@code interface "ILogin"} — passed through directly</li>
 *   <li><b>Explicit arrows:</b> {@code "Web Browser" --> "Sales Software" : SSL}</li>
 *   <li><b>Usage arrow:</b> {@code "Client" ..> "IService" : use}</li>
 *   <li><b>NL dependency:</b> {@code "Web Browser" accesses/uses/connects to "Sales Software" via SSL}</li>
 *   <li><b>NL component declaration:</b> {@code portal consists of Login and Menu}</li>
 *   <li><b>Bare entity list:</b> chained with dependency arrows automatically</li>
 * </ul>
 *
 * <h3>Fallback behaviour</h3>
 * <p>When the input does not yield any components, a canonical
 * Web Browser → Sales Software → MySQL example is emitted so the output is always valid PlantUML.
 */
@Service
public class ComponentDiagramGeneratorServiceImpl implements ComponentDiagramGeneratorService {

    // ── Element type keywords ──────────────────────────────────────────────

    private static final Pattern DATABASE_KW = Pattern.compile(
            "\\b(?:database|db|datastore|data\\s+store)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERFACE_KW = Pattern.compile(
            "\\b(?:interface|api|port|socket)\\b", Pattern.CASE_INSENSITIVE);

    // ── Explicit PlantUML pass-through ─────────────────────────────────────

    /** e.g. {@code component "Web Browser"} or {@code component [Web Browser]} */
    private static final Pattern EXPLICIT_COMPONENT = Pattern.compile(
            "^\\s*(component|database|interface|node|artifact|cloud|frame|package)\\s+" +
            "(?:\"([^\"]+)\"|\\[([^]]+)])\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Protocol-labeled arrow with optional multi-word unquoted names:
     * {@code Web Browser --> Apache Server : HTTP} or {@code Java Servlet --> MySQL : JDBC}.
     * Groups: (from, to, protocol)
     */
    private static final Pattern PROTOCOL_ARROW = Pattern.compile(
            "(.+?)\\s+-->\\s+(.+?)\\s*:\\s*" +
            "(JDBC|HTTPS?|SSL|TCP(?:/IP)?|UDP|REST|SOAP|gRPC|AMQP|FTP|SMTP|WebSocket)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Explicit arrow: {@code "A" --> "B" : label} or {@code A --> B} */
    private static final Pattern EXPLICIT_ARROW = Pattern.compile(
            "\"([^\"]+)\"\\s*(--?>|\\.\\.)\\s*>?\\s*\"([^\"]+)\"(?:\\s*:\\s*(.+))?|" +
            "([A-Za-z]\\w*)\\s*(--?>|\\.\\.)\\s*>?\\s*([A-Za-z]\\w*)(?:\\s*:\\s*(.+))?");

    // ── NL dependency patterns ─────────────────────────────────────────────

    /**
     * "X accesses/uses/connects to/calls Y via|using|over|through LABEL"
     * Groups: (from, to, label?)
     */
    private static final Pattern NL_DEPENDENCY = Pattern.compile(
            "\"([^\"]+)\"\\s+(?:accesses?|uses?|calls?|connects?\\s+to|sends?\\s+to|depends?\\s+on)\\s+" +
            "\"([^\"]+)\"(?:\\s+(?:via|using|over|through|with)\\s+([\\w/.-]+))?|" +
            "([A-Z][\\w\\s]*)\\s+(?:accesses?|uses?|calls?|connects?\\s+to|depends?\\s+on)\\s+" +
            "([A-Z][\\w\\s]*)(?:\\s+(?:via|using|over|through|with)\\s+([\\w/.-]+))?",
            Pattern.CASE_INSENSITIVE);

    /**
     * "X consists of / is made up of / contains A, B and C"
     * Groups: (parent, children-string)
     */
    private static final Pattern NL_CONSISTS_OF = Pattern.compile(
            "([A-Z][\\w\\s]*)\\s+(?:consists?\\s+of|is\\s+made\\s+up\\s+of|contains?|includes?)\\s+" +
            "([A-Z][\\w,\\s]+(?:\\s+and\\s+[A-Z][\\w\\s]*)?)",
            Pattern.CASE_INSENSITIVE);

    /**
     * "X is a (component|interface|database|service|browser|portal)"
     * Groups: (name, type-word)
     */
    private static final Pattern NL_IS_A = Pattern.compile(
            "([A-Z][\\w\\s]*)\\s+is\\s+(?:a|an)\\s+" +
            "(component|interface|database|service|browser|portal|module|library)",
            Pattern.CASE_INSENSITIVE);

    // ── Protocol / technology keywords used as relationship labels ─────────

    private static final Pattern PROTOCOL_IN_LINE = Pattern.compile(
            "\\b(JDBC|HTTPS?|SSL|TCP(?:/IP)?|UDP|REST|SOAP|gRPC|AMQP|FTP|SMTP|WebSocket)\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Capitalised-word detection for bare entity lists ───────────────────

    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\b");

    // ── Stop-words excluded from bare entity detection ─────────────────────

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "A", "An", "The", "Is", "Are", "Was", "Were", "Be", "Been", "Being",
            "Has", "Have", "Had", "Do", "Does", "Did", "Will", "Would", "Could",
            "Should", "May", "Might", "Can", "Shall", "Must", "Need", "Used",
            "With", "Via", "And", "Or", "But", "For", "Of", "On", "In",
            "At", "By", "From", "To", "Up", "Down", "Over", "Under", "Through",
            "After", "Before", "During", "Between", "Into", "Out", "Off",
            "It", "Its", "This", "That", "These", "Those", "Which", "Who",
            "What", "Where", "When", "How", "If", "Then", "So", "Also",
            "I", "Create", "Generate", "Show", "Make", "Draw", "Diagram"
    );

    // ══ Public API ══════════════════════════════════════════════════════════

    @Override
    public String generateComponentDiagram(String text) {
        if (text == null || text.isBlank()) {
            return buildDefault();
        }

        // Ordered map: name → element type ("component"|"database"|"interface")
        Map<String, String> elements    = new LinkedHashMap<>();
        // Ordered list: [from, to, label?]
        List<String[]>      arrows      = new ArrayList<>();

        for (String raw : text.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("'") || line.startsWith("//")) continue;

            // ── 0. Protocol-labeled arrows: "Web Browser --> Apache Server : HTTP" ──
            Matcher m = PROTOCOL_ARROW.matcher(line);
            if (m.matches()) {
                String from  = m.group(1).strip();
                String to    = m.group(2).strip();
                String label = normalizeProtocol(m.group(3));
                elements.putIfAbsent(from, inferType(from));
                elements.putIfAbsent(to,   inferType(to));
                arrows.add(new String[]{from, to, label});
                continue;
            }

            // ── 1. Explicit PlantUML keyword lines ─────────────────────────
            m = EXPLICIT_COMPONENT.matcher(line);
            if (m.matches()) {
                String keyword = m.group(1).toLowerCase();
                String name    = m.group(2) != null ? m.group(2) : m.group(3);
                String type    = resolveElementType(keyword, name);
                elements.put(name, type);
                continue;
            }

            // ── 2. Explicit arrows ─────────────────────────────────────────
            m = EXPLICIT_ARROW.matcher(line);
            if (m.find()) {
                String from  = m.group(1) != null ? m.group(1) : m.group(5);
                String to    = m.group(3) != null ? m.group(3) : m.group(7);
                String label = m.group(4) != null ? m.group(4) :
                               m.group(8) != null ? m.group(8) : null;
                if (from != null && to != null) {
                    elements.putIfAbsent(from, inferType(from));
                    elements.putIfAbsent(to,   inferType(to));
                    arrows.add(new String[]{from, to, label != null ? label.strip() : null});
                }
                continue;
            }

            // ── 3. NL dependency: "X accesses Y via LABEL" ────────────────
            m = NL_DEPENDENCY.matcher(line);
            if (m.find()) {
                String from  = coalesce(m.group(1), m.group(4));
                String to    = coalesce(m.group(2), m.group(5));
                String label = coalesce(m.group(3), m.group(6));
                if (label == null) label = extractProtocol(line);
                from = from.strip(); to = to.strip();
                elements.putIfAbsent(from, inferType(from));
                elements.putIfAbsent(to,   inferType(to));
                arrows.add(new String[]{from, to, label});
                continue;
            }

            // ── 4. "X consists of A, B and C" ─────────────────────────────
            m = NL_CONSISTS_OF.matcher(line);
            if (m.find()) {
                String parent   = m.group(1).strip();
                String childStr = m.group(2);
                elements.putIfAbsent(parent, inferType(parent));
                for (String child : splitChildren(childStr)) {
                    elements.putIfAbsent(child, inferType(child));
                    arrows.add(new String[]{parent, child, null});
                }
                continue;
            }

            // ── 5. "X is a component/database/..." ────────────────────────
            m = NL_IS_A.matcher(line);
            if (m.find()) {
                String name = m.group(1).strip();
                String type = resolveElementType(m.group(2).toLowerCase(), name);
                elements.put(name, type);
                continue;
            }

            // ── 6. Bare capitalised words — collect as components ──────────
            m = CAPITALIZED_WORD.matcher(line);
            while (m.find()) {
                String word = m.group(1);
                if (!STOP_WORDS.contains(word)) {
                    elements.putIfAbsent(word, inferType(word));
                }
            }
        }

        if (elements.isEmpty()) {
            return buildDefault();
        }

        // If elements were collected but no explicit arrows, chain them
        if (arrows.isEmpty()) {
            List<String> names = new ArrayList<>(elements.keySet());
            for (int i = 0; i < names.size() - 1; i++) {
                arrows.add(new String[]{names.get(i), names.get(i + 1), null});
            }
        }

        return render(elements, arrows);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Determine PlantUML element keyword for a given name/type hint. */
    private String resolveElementType(String keyword, String name) {
        if ("database".equals(keyword) || DATABASE_KW.matcher(keyword).find()) return "database";
        if ("interface".equals(keyword) || INTERFACE_KW.matcher(keyword).find()) return "interface";
        return "component";
    }

    /** Infer element type from name alone (heuristic). */
    private String inferType(String name) {
        if (DATABASE_KW.matcher(name).find()) return "database";
        if (INTERFACE_KW.matcher(name).find()) return "interface";
        if (name.matches("(?i).*(?:db|database|mysql|postgres|oracle|mongo|redis).*")) return "database";
        if (name.matches("(?i).*(?:interface|api).*")) return "interface";
        return "component";
    }

    /** Extract a protocol keyword from a line (e.g. "SSL", "JDBC"). */
    private String extractProtocol(String line) {
        Matcher m = PROTOCOL_IN_LINE.matcher(line);
        return m.find() ? normalizeProtocol(m.group(1)) : null;
    }

    /**
     * Canonicalize protocol labels to uppercase.
     * {@code tcp} and {@code tcp/ip} both become {@code TCP/IP};
     * all others are uppercased (e.g. {@code https} → {@code HTTPS}).
     */
    private static String normalizeProtocol(String label) {
        if (label == null) return null;
        String upper = label.trim().toUpperCase();
        if (upper.equals("TCP") || upper.equals("TCP/IP")) return "TCP/IP";
        return upper;
    }

    /** Split "A, B and C" into ["A", "B", "C"]. */
    private List<String> splitChildren(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split("(?i)\\s*,\\s*|\\s+and\\s+")) {
            String p = part.strip();
            if (!p.isEmpty()) result.add(p);
        }
        return result;
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null && !b.isBlank() ? b : null);
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    private String render(Map<String, String> elements, List<String[]> arrows) {
        StringBuilder sb = new StringBuilder("@startuml\n\n");

        sb.append("left to right direction\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam padding 8\n");
        sb.append("skinparam nodesep 70\n");
        sb.append("skinparam ranksep 80\n");
        sb.append("\n");
        sb.append("skinparam component {\n");
        sb.append("  BackgroundColor #FAFAFA\n");
        sb.append("  BorderColor #4A4A4A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 13\n");
        sb.append("  BorderThickness 1.5\n");
        sb.append("}\n");
        sb.append("skinparam interface {\n");
        sb.append("  BackgroundColor #FFFDE7\n");
        sb.append("  BorderColor #4A4A4A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 12\n");
        sb.append("}\n");
        sb.append("skinparam database {\n");
        sb.append("  BackgroundColor #E8F4FD\n");
        sb.append("  BorderColor #4A4A4A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 13\n");
        sb.append("}\n");
        sb.append("skinparam arrow {\n");
        sb.append("  Color #333333\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 11\n");
        sb.append("  FontColor #444444\n");
        sb.append("}\n");
        sb.append("\n");

        // Declare elements
        for (Map.Entry<String, String> e : elements.entrySet()) {
            String name = e.getKey();
            String type = e.getValue();
            sb.append(type).append(" \"").append(name).append("\"\n");
        }

        if (!arrows.isEmpty()) {
            sb.append("\n");
            for (String[] a : arrows) {
                sb.append("\"").append(a[0]).append("\" --> \"").append(a[1]).append("\"");
                if (a[2] != null) sb.append(" : ").append(a[2]);
                sb.append("\n");
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private String buildDefault() {
        return "@startuml\n\n" +
               "left to right direction\n" +
               "skinparam shadowing false\n" +
               "skinparam padding 8\n" +
               "skinparam nodesep 70\n" +
               "skinparam ranksep 80\n\n" +
               "skinparam component {\n" +
               "  BackgroundColor #FAFAFA\n" +
               "  BorderColor #4A4A4A\n" +
               "  FontName Arial\n" +
               "  FontSize 13\n" +
               "  BorderThickness 1.5\n" +
               "}\n" +
               "skinparam database {\n" +
               "  BackgroundColor #E8F4FD\n" +
               "  BorderColor #4A4A4A\n" +
               "  FontName Arial\n" +
               "  FontSize 13\n" +
               "}\n" +
               "skinparam arrow {\n" +
               "  Color #333333\n" +
               "  FontName Arial\n" +
               "  FontSize 11\n" +
               "  FontColor #444444\n" +
               "}\n\n" +
               "component \"Web Browser\"\n" +
               "component \"Sales Software\"\n" +
               "database \"MySQL\"\n\n" +
               "\"Web Browser\" --> \"Sales Software\" : SSL\n" +
               "\"Sales Software\" --> \"MySQL\" : JDBC\n" +
               "@enduml";
    }
}
