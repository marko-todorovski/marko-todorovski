package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link DeploymentDiagramGeneratorService}.
 *
 * <p>Parses natural language or semi-structured text describing infrastructure deployment
 * and generates academically correct PlantUML deployment diagram syntax.
 *
 * <h3>Supported input styles</h3>
 * <ul>
 *   <li><b>Explicit PlantUML:</b> {@code node "AppServer"}, {@code artifact "App.war"},
 *       {@code database "MySQL"} — passed through directly</li>
 *   <li><b>Nested block:</b> {@code node "AppServer" { artifact "App.war" }}</li>
 *   <li><b>Explicit arrows:</b> {@code "Client" --> "Server" : HTTP}</li>
 *   <li><b>NL deployment:</b> {@code "App.war" is deployed on "AppServer"}</li>
 *   <li><b>NL communication:</b> {@code "Client" communicates with "Server" via HTTPS}</li>
 *   <li><b>Bare entity list:</b> chained with communication paths automatically</li>
 * </ul>
 *
 * <h3>Fallback behaviour</h3>
 * <p>When no nodes are detected, a canonical Client → AppServer → Database example is emitted.
 */
@Service
public class DeploymentDiagramGeneratorServiceImpl implements DeploymentDiagramGeneratorService {

    // ── Element types ──────────────────────────────────────────────────────

    private static final Pattern DATABASE_KW = Pattern.compile(
            "\\b(?:database|db|datastore|data\\s+store|mysql|postgres|oracle|mongo|redis)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIFACT_KW = Pattern.compile(
            "\\b(?:artifact|war|jar|ear|config|xml|file|\\.war|\\.jar|\\.ear)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOUD_KW = Pattern.compile(
            "\\b(?:cloud|aws|azure|gcp|internet|web)\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Explicit PlantUML pass-through ─────────────────────────────────────

    /**
     * {@code node "AppServer"} or {@code node [AppServer]} or
     * {@code node "AppServer" { ... }} (opening line of a block).
     */
    private static final Pattern EXPLICIT_NODE = Pattern.compile(
            "^\\s*(node|artifact|database|component|cloud|frame|rectangle|package)\\s+" +
            "(?:\"([^\"]+)\"|\\[([^]]+)])\\s*(\\{)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Closing brace of a nested block. */
    private static final Pattern BLOCK_CLOSE = Pattern.compile("^\\s*\\}\\s*$");

    /** Explicit arrow: {@code "A" --> "B" : label} */
    private static final Pattern EXPLICIT_ARROW = Pattern.compile(
            "\"([^\"]+)\"\\s*-->\\s*\"([^\"]+)\"(?:\\s*:\\s*(.+))?");

    /** Protocol-labeled arrow with unquoted multi-word names:
     *  {@code AppServer --> MySQL : JDBC} */
    private static final Pattern PROTOCOL_ARROW = Pattern.compile(
            "(.+?)\\s+-->\\s+(.+?)\\s*:\\s*" +
            "(JDBC|HTTPS?|SSL|TCP(?:/IP)?|UDP|REST|SOAP|gRPC|AMQP|FTP|SMTP|WebSocket)\\s*$",
            Pattern.CASE_INSENSITIVE);

    // ── NL patterns ────────────────────────────────────────────────────────

    /**
     * "X is deployed on / runs on / hosted on Y"
     * Groups: (artifact, node)
     */
    private static final Pattern NL_DEPLOYED_ON = Pattern.compile(
            "\"?([\\w.\\s-]+?)\"?\\s+(?:is\\s+)?(?:deployed\\s+on|runs\\s+on|hosted\\s+on|installed\\s+on)\\s+" +
            "\"?([\\w.\\s-]+?)\"?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * "X communicates with / connects to / accesses Y [via LABEL]"
     * Groups: (from, to, label?)
     */
    private static final Pattern NL_COMMUNICATES = Pattern.compile(
            "\"?([\\w.\\s-]+?)\"?\\s+(?:communicates?\\s+with|connects?\\s+to|accesses?|sends?\\s+to|uses?)\\s+" +
            "\"?([\\w.\\s-]+?)\"?(?:\\s+(?:via|using|over|through)\\s+([\\w/.-]+))?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Protocol keywords for label extraction. */
    private static final Pattern PROTOCOL_IN_LINE = Pattern.compile(
            "\\b(JDBC|HTTPS?|SSL|TCP(?:/IP)?|UDP|REST|SOAP|gRPC|AMQP|FTP|SMTP|WebSocket)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Capitalised words for bare entity detection. */
    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\b");

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "A", "An", "The", "Is", "Are", "Was", "Were", "Be", "Been", "Being",
            "Has", "Have", "Had", "Do", "Does", "Did", "Will", "Would", "Could",
            "Should", "May", "Might", "Can", "Shall", "Must", "Need", "Used",
            "With", "Via", "And", "Or", "But", "For", "Of", "On", "In",
            "At", "By", "From", "To", "Up", "Down", "Over", "Under", "Through",
            "After", "Before", "During", "Between", "Into", "Out", "Off",
            "It", "Its", "This", "That", "These", "Those", "Which", "Who",
            "What", "Where", "When", "How", "If", "Then", "So", "Also",
            "I", "Create", "Generate", "Show", "Make", "Draw", "Diagram",
            "Deployed", "Runs", "Hosted", "Communicates", "Connects", "Accesses"
    );

    // ── Node model ─────────────────────────────────────────────────────────

    /** A top-level deployment element with optional nested children. */
    private static class DeploymentNode {
        final String name;
        final String type;               // node | database | artifact | cloud | ...
        final List<DeploymentNode> children = new ArrayList<>();

        DeploymentNode(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    // ══ Public API ══════════════════════════════════════════════════════════

    @Override
    public String generateDeploymentDiagram(String text) {
        if (text == null || text.isBlank()) {
            return buildDefault();
        }

        // Top-level nodes (ordered, by name)
        Map<String, DeploymentNode> topLevel = new LinkedHashMap<>();
        // Pending nesting: artifact → parent node name
        Map<String, String> pendingNesting   = new LinkedHashMap<>();
        // Arrows: [from, to, label?]
        List<String[]> arrows = new ArrayList<>();

        // Track currently open block (single-level nesting supported)
        DeploymentNode openBlock = null;

        for (String raw : text.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("'") || line.startsWith("//")) continue;

            // ── Block close ────────────────────────────────────────────────
            if (BLOCK_CLOSE.matcher(line).matches()) {
                openBlock = null;
                continue;
            }

            // ── 0. Protocol arrow (unquoted multi-word names) ──────────────
            Matcher m = PROTOCOL_ARROW.matcher(line);
            if (m.matches()) {
                String from  = m.group(1).strip();
                String to    = m.group(2).strip();
                String label = normalizeProtocol(m.group(3));
                ensureTopLevel(topLevel, from);
                ensureTopLevel(topLevel, to);
                arrows.add(new String[]{from, to, label});
                continue;
            }

            // ── 1. Explicit PlantUML node/artifact line ────────────────────
            m = EXPLICIT_NODE.matcher(line);
            if (m.matches()) {
                String keyword  = m.group(1).toLowerCase();
                String name     = m.group(2) != null ? m.group(2) : m.group(3);
                boolean hasOpen = m.group(4) != null;
                String type     = resolveType(keyword, name);

                if (openBlock != null) {
                    // Nested inside a block
                    openBlock.children.add(new DeploymentNode(name, type));
                } else {
                    DeploymentNode node = new DeploymentNode(name, type);
                    topLevel.put(name, node);
                    if (hasOpen) openBlock = node;
                }
                continue;
            }

            // ── 2. Explicit quoted arrow ───────────────────────────────────
            m = EXPLICIT_ARROW.matcher(line);
            if (m.find()) {
                String from  = m.group(1).strip();
                String to    = m.group(2).strip();
                String label = m.group(3) != null ? m.group(3).strip() : null;
                ensureTopLevel(topLevel, from);
                ensureTopLevel(topLevel, to);
                arrows.add(new String[]{from, to, label});
                continue;
            }

            // ── 3. NL deployed-on ──────────────────────────────────────────
            m = NL_DEPLOYED_ON.matcher(line);
            if (m.find()) {
                String artifact = m.group(1).strip();
                String parent   = m.group(2).strip();
                ensureTopLevel(topLevel, parent);
                pendingNesting.put(artifact, parent);
                continue;
            }

            // ── 4. NL communicates ─────────────────────────────────────────
            m = NL_COMMUNICATES.matcher(line);
            if (m.find()) {
                String from  = m.group(1).strip();
                String to    = m.group(2).strip();
                String label = m.group(3) != null ? m.group(3).strip() : extractProtocol(line);
                ensureTopLevel(topLevel, from);
                ensureTopLevel(topLevel, to);
                arrows.add(new String[]{from, to, label});
                continue;
            }

            // ── 5. Bare capitalised words ──────────────────────────────────
            m = CAPITALIZED_WORD.matcher(line);
            while (m.find()) {
                String word = m.group(1);
                if (!STOP_WORDS.contains(word)) {
                    ensureTopLevel(topLevel, word);
                }
            }
        }

        // Apply pending nesting (artifact deployed on node)
        for (Map.Entry<String, String> entry : pendingNesting.entrySet()) {
            String artifactName = entry.getKey();
            String parentName   = entry.getValue();
            DeploymentNode parent = topLevel.get(parentName);
            if (parent != null) {
                parent.children.add(new DeploymentNode(artifactName,
                        inferType(artifactName)));
                topLevel.remove(artifactName);
            }
        }

        if (topLevel.isEmpty()) {
            return buildDefault();
        }

        if (arrows.isEmpty()) {
            List<String> names = new ArrayList<>(topLevel.keySet());
            for (int i = 0; i < names.size() - 1; i++) {
                arrows.add(new String[]{names.get(i), names.get(i + 1), null});
            }
        }

        return render(topLevel, arrows);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void ensureTopLevel(Map<String, DeploymentNode> map, String name) {
        map.computeIfAbsent(name, n -> new DeploymentNode(n, inferType(n)));
    }

    private String resolveType(String keyword, String name) {
        return switch (keyword) {
            case "database"  -> "database";
            case "artifact"  -> "artifact";
            case "cloud"     -> "cloud";
            case "component" -> "component";
            default          -> inferType(name);
        };
    }

    private String inferType(String name) {
        if (DATABASE_KW.matcher(name).find()) return "database";
        if (ARTIFACT_KW.matcher(name).find()) return "artifact";
        if (CLOUD_KW.matcher(name).find())    return "cloud";
        return "node";
    }

    private String extractProtocol(String line) {
        Matcher m = PROTOCOL_IN_LINE.matcher(line);
        return m.find() ? normalizeProtocol(m.group(1)) : null;
    }

    private static String normalizeProtocol(String label) {
        if (label == null) return null;
        String upper = label.trim().toUpperCase();
        if (upper.equals("TCP") || upper.equals("TCP/IP")) return "TCP/IP";
        return upper;
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    private String render(Map<String, DeploymentNode> topLevel, List<String[]> arrows) {
        StringBuilder sb = new StringBuilder("@startuml\n\n");

        sb.append("skinparam shadowing false\n");
        sb.append("skinparam padding 10\n");
        sb.append("skinparam nodesep 70\n");
        sb.append("skinparam ranksep 80\n");
        sb.append("\n");
        sb.append("skinparam node {\n");
        sb.append("  BackgroundColor #F0F4FF\n");
        sb.append("  BorderColor #3A3A8A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 13\n");
        sb.append("  BorderThickness 1.5\n");
        sb.append("}\n");
        sb.append("skinparam database {\n");
        sb.append("  BackgroundColor #E8F4FD\n");
        sb.append("  BorderColor #4A4A4A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 13\n");
        sb.append("}\n");
        sb.append("skinparam artifact {\n");
        sb.append("  BackgroundColor #FFFDE7\n");
        sb.append("  BorderColor #4A4A4A\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 12\n");
        sb.append("}\n");
        sb.append("skinparam component {\n");
        sb.append("  BackgroundColor #FAFAFA\n");
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

        // Declare nodes (with optional nested children)
        for (DeploymentNode node : topLevel.values()) {
            if (node.children.isEmpty()) {
                sb.append(node.type).append(" \"").append(node.name).append("\"\n");
            } else {
                sb.append(node.type).append(" \"").append(node.name).append("\" {\n");
                for (DeploymentNode child : node.children) {
                    sb.append("  ").append(child.type).append(" \"").append(child.name).append("\"\n");
                }
                sb.append("}\n");
            }
        }

        // Arrows
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
               "skinparam shadowing false\n" +
               "skinparam padding 10\n" +
               "skinparam nodesep 70\n" +
               "skinparam ranksep 80\n\n" +
               "skinparam node {\n" +
               "  BackgroundColor #F0F4FF\n" +
               "  BorderColor #3A3A8A\n" +
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
               "skinparam artifact {\n" +
               "  BackgroundColor #FFFDE7\n" +
               "  BorderColor #4A4A4A\n" +
               "  FontName Arial\n" +
               "  FontSize 12\n" +
               "}\n\n" +
               "node \"Client\" {\n" +
               "  artifact \"Browser\"\n" +
               "}\n" +
               "node \"AppServer\" {\n" +
               "  artifact \"App.war\"\n" +
               "}\n" +
               "database \"MySQL\"\n\n" +
               "\"Client\" --> \"AppServer\" : HTTPS\n" +
               "\"AppServer\" --> \"MySQL\" : JDBC\n" +
               "@enduml";
    }
}
