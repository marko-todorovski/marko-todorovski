package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML class diagram source into a proper Draw.io XML document.
 *
 * <p>Each class becomes a UML class shape with separate header and body compartments.
 * Relationships use the correct Draw.io arrow styles for inheritance, implementation,
 * composition, aggregation, dependency, and association.
 */
public class ClassDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(ClassDrawIoExporter.class);

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int CLASS_W        = 180;
    private static final int HEADER_H       = 30;
    private static final int ATTR_H         = 20;
    private static final int MIN_BODY_H     = 20;
    private static final int H_GAP          = 80;
    private static final int V_GAP          = 80;
    private static final int COLS           = 4;
    private static final int START_X        = 40;
    private static final int START_Y        = 40;

    // ── Styles ───────────────────────────────────────────────────────────────
    private static final String STYLE_CLASS_HEADER =
            "swimlane;fontStyle=1;align=center;startSize=30;html=1;"
                    + "fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_ATTR =
            "text;strokeColor=none;fillColor=none;align=left;verticalAlign=top;"
                    + "spacingLeft=4;spacingRight=4;overflow=hidden;rotatable=0;points="
                    + "[[0,0.5],[1,0.5]];portConstraint=eastwest;html=1;";
    private static final String STYLE_INTERFACE =
            "swimlane;fontStyle=3;align=center;startSize=30;html=1;"
                    + "fillColor=#e1d5e7;strokeColor=#9673a6;";

    // Arrow styles (Draw.io UML-compliant)
    private static final String ARROW_INHERIT     = "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;endFill=0;";
    private static final String ARROW_IMPLEMENT   = "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;endFill=0;dashed=1;";
    private static final String ARROW_COMPOSE     = "edgeStyle=orthogonalEdgeStyle;html=1;startArrow=diamondThin;startFill=1;endArrow=none;";
    private static final String ARROW_AGGREGATE   = "edgeStyle=orthogonalEdgeStyle;html=1;startArrow=diamondThin;startFill=0;endArrow=none;";
    private static final String ARROW_DEPEND      = "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;dashed=1;";
    private static final String ARROW_ASSOC       = "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;";

    // ── Parsing patterns ─────────────────────────────────────────────────────
    private static final Pattern P_CLASS = Pattern.compile(
            "^\\s*(?:(abstract|interface)\\s+)?class\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+(\\w+))?(?:\\s*\\{([^}]*)\\})?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern P_INTERFACE = Pattern.compile(
            "^\\s*interface\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+(\\w+))?(?:\\s*\\{([^}]*)\\})?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s*\"?[^\"]*\"?\\s*(\\|\\|--|\\.\\.\\|\\||<\\|--|--\\|>|<\\|\\.\\.|\\.\\.\\|>|\\*--|--\\*|o--|--o|-->|<--|\\.\\.>|<\\.\\.|--|\\.\\.)\\s*\"?[^\"]*\"?\\s*(\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ── Data ─────────────────────────────────────────────────────────────────

    private record ClassDef(String id, String name, boolean isInterface, List<String> members) {}

    private record RelDef(String from, String to, String arrow, String label) {}

    // ── Public entry point ───────────────────────────────────────────────────

    public String export(String plantUml) {
        log.info("[ClassDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, ClassDef> classes = parseClasses(plantUml);
        List<RelDef> rels = parseRelationships(plantUml);

        // Infer missing classes from relationships
        for (RelDef r : rels) {
            classes.computeIfAbsent(r.from(), k -> new ClassDef(DrawIoXml.uid(), k, false, List.of()));
            classes.computeIfAbsent(r.to(),   k -> new ClassDef(DrawIoXml.uid(), k, false, List.of()));
        }

        log.info("[ClassDrawIo] classes={} relationships={}", classes.size(), rels.size());

        DrawIoXml xml = new DrawIoXml();

        // Layout: simple grid
        List<ClassDef> classlist = new ArrayList<>(classes.values());
        Map<String, int[]> positions = new LinkedHashMap<>();
        for (int i = 0; i < classlist.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = START_X + col * (CLASS_W + H_GAP);
            int y = START_Y + row * (200 + V_GAP);
            positions.put(classlist.get(i).name(), new int[]{x, y});
        }

        // Emit class boxes
        for (ClassDef c : classlist) {
            int[] pos = positions.get(c.name());
            int bodyH = Math.max(MIN_BODY_H, c.members().size() * ATTR_H + 4);
            int totalH = HEADER_H + bodyH;

            String headerStyle = c.isInterface() ? STYLE_INTERFACE : STYLE_CLASS_HEADER;
            // Swimlane parent cell
            xml.addRaw(xml.rect(c.id(), c.name(), pos[0], pos[1], CLASS_W, totalH, headerStyle));

            // Member rows as child cells inside the swimlane
            int attrY = HEADER_H;
            for (String member : c.members()) {
                String aId = DrawIoXml.uid();
                // Child cells have parent = swimlane id
                xml.addRaw("<mxCell id=\"" + aId + "\" value=\"" + DrawIoXml.esc(member)
                        + "\" style=\"" + STYLE_ATTR + "\" vertex=\"1\" parent=\"" + c.id()
                        + "\"><mxGeometry x=\"0\" y=\"" + attrY + "\" width=\"" + CLASS_W
                        + "\" height=\"" + ATTR_H + "\" as=\"geometry\"/></mxCell>");
                attrY += ATTR_H;
            }
        }

        // Emit relationship edges
        for (RelDef r : rels) {
            ClassDef src = classes.get(r.from());
            ClassDef tgt = classes.get(r.to());
            if (src == null || tgt == null) continue;
            String style = resolveArrowStyle(r.arrow());
            xml.addRaw(xml.edge(DrawIoXml.uid(), r.label() != null ? r.label() : "",
                    src.id(), tgt.id(), style));
        }

        int cols = Math.min(classlist.size(), COLS);
        int rows = classlist.isEmpty() ? 1 : (classlist.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (CLASS_W + H_GAP);
        int totalH = START_Y * 2 + rows * (230 + V_GAP);

        log.info("[ClassDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private Map<String, ClassDef> parseClasses(String code) {
        Map<String, ClassDef> map = new LinkedHashMap<>();

        Matcher m = P_CLASS.matcher(code);
        while (m.find()) {
            String modifier = m.group(1);
            String name = m.group(2).trim();
            String body = m.group(4);
            boolean isInterface = "interface".equalsIgnoreCase(modifier);
            List<String> members = parseMembers(body);
            map.put(name, new ClassDef(DrawIoXml.uid(), name, isInterface, members));
        }

        Matcher im = P_INTERFACE.matcher(code);
        while (im.find()) {
            String name = im.group(1).trim();
            String body = im.group(3);
            List<String> members = parseMembers(body);
            map.put(name, new ClassDef(DrawIoXml.uid(), name, true, members));
        }

        return map;
    }

    private List<String> parseMembers(String body) {
        List<String> list = new ArrayList<>();
        if (body == null || body.isBlank()) return list;
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.equals("{") && !t.equals("}")) {
                list.add(t);
            }
        }
        return list;
    }

    private List<RelDef> parseRelationships(String code) {
        List<RelDef> list = new ArrayList<>();
        Matcher m = P_REL.matcher(code);
        while (m.find()) {
            String from  = m.group(1).trim();
            String arrow = m.group(2).trim();
            String to    = m.group(3).trim();
            String label = m.group(4) != null ? m.group(4).trim() : null;
            if (!from.equalsIgnoreCase(to)) {
                list.add(new RelDef(from, to, arrow, label));
            }
        }
        return list;
    }

    private String resolveArrowStyle(String arrow) {
        return switch (arrow) {
            case "<|--", "--|>" -> ARROW_INHERIT;
            case "<|..", "..|>" -> ARROW_IMPLEMENT;
            case "*--", "--*"   -> ARROW_COMPOSE;
            case "o--", "--o"   -> ARROW_AGGREGATE;
            case "..>", "<.."   -> ARROW_DEPEND;
            default             -> ARROW_ASSOC;
        };
    }
}
