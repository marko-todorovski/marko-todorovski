package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML ER diagram source into Draw.io XML.
 *
 * <p>Each entity is a rectangle with attribute rows.
 * Relationships use crow's-foot notation where possible.
 */
public class ErDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(ErDrawIoExporter.class);

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int ENTITY_W   = 180;
    private static final int HEADER_H   = 30;
    private static final int ATTR_H     = 20;
    private static final int H_GAP      = 80;
    private static final int V_GAP      = 60;
    private static final int COLS       = 3;
    private static final int START_X    = 40;
    private static final int START_Y    = 40;

    // ── Styles ───────────────────────────────────────────────────────────────
    private static final String STYLE_ENTITY =
            "swimlane;fontStyle=1;align=center;startSize=30;html=1;"
                    + "fillColor=#fff2cc;strokeColor=#d6b656;";
    private static final String STYLE_ATTR =
            "text;strokeColor=none;fillColor=none;align=left;verticalAlign=top;"
                    + "spacingLeft=4;overflow=hidden;html=1;";
    private static final String STYLE_REL =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=ERmany;startArrow=ERone;";
    private static final String STYLE_REL_GENERIC =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;";

    // ── Patterns ─────────────────────────────────────────────────────────────
    private static final Pattern P_ENTITY = Pattern.compile(
            "entity\\s+\"?([\\w][\\w\\s]*)\"?(?:\\s+as\\s+(\\w+))?\\s*\\{([^}]*)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s+([|o}{]{1,3}[-. ]+[-. ][|o}{]{1,3})\\s+(\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private record Entity(String id, String name, List<String> attrs) {}
    private record Rel(String from, String to, String cardinality, String label) {}

    public String export(String plantUml) {
        log.info("[ErDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Entity> entities = new LinkedHashMap<>();
        List<Rel> rels = new ArrayList<>();

        Matcher em = P_ENTITY.matcher(plantUml);
        while (em.find()) {
            String name  = em.group(1).trim();
            String alias = em.group(2) != null ? em.group(2).trim() : name;
            List<String> attrs = parseAttrs(em.group(3));
            entities.put(alias, new Entity(DrawIoXml.uid(), name, attrs));
        }

        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from  = rm.group(1).trim();
            String card  = rm.group(2).trim();
            String to    = rm.group(3).trim();
            String label = rm.group(4) != null ? rm.group(4).trim() : "";
            rels.add(new Rel(from, to, card, label));
        }

        // Infer missing entities from rels
        for (Rel r : rels) {
            entities.computeIfAbsent(r.from(), k -> new Entity(DrawIoXml.uid(), k, List.of()));
            entities.computeIfAbsent(r.to(),   k -> new Entity(DrawIoXml.uid(), k, List.of()));
        }

        log.info("[ErDrawIo] entities={} rels={}", entities.size(), rels.size());

        DrawIoXml xml = new DrawIoXml();
        List<Entity> list = new ArrayList<>(entities.values());

        for (int i = 0; i < list.size(); i++) {
            Entity e = list.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x = START_X + col * (ENTITY_W + H_GAP);
            int y = START_Y + row * (HEADER_H + e.attrs().size() * ATTR_H + V_GAP + 10);
            int totalH = HEADER_H + Math.max(20, e.attrs().size() * ATTR_H + 4);
            xml.addRaw(xml.rect(e.id(), e.name(), x, y, ENTITY_W, totalH, STYLE_ENTITY));
            int ay = HEADER_H;
            for (String attr : e.attrs()) {
                String aId = DrawIoXml.uid();
                xml.addRaw("<mxCell id=\"" + aId + "\" value=\"" + DrawIoXml.esc(attr)
                        + "\" style=\"" + STYLE_ATTR + "\" vertex=\"1\" parent=\"" + e.id()
                        + "\"><mxGeometry x=\"0\" y=\"" + ay + "\" width=\"" + ENTITY_W
                        + "\" height=\"" + ATTR_H + "\" as=\"geometry\"/></mxCell>");
                ay += ATTR_H;
            }
        }

        for (Rel r : rels) {
            Entity src = entities.get(r.from());
            Entity tgt = entities.get(r.to());
            if (src == null || tgt == null) continue;
            xml.addRaw(xml.edge(DrawIoXml.uid(), r.label(), src.id(), tgt.id(), STYLE_REL));
        }

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (ENTITY_W + H_GAP);
        int totalH = START_Y * 2 + rows * (150 + V_GAP);

        log.info("[ErDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    private List<String> parseAttrs(String body) {
        List<String> list = new ArrayList<>();
        if (body == null) return list;
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }
}
