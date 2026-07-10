package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML component diagram source into Draw.io XML.
 *
 * <p>Components render as Draw.io component shapes. Interfaces render as small
 * circles. Dependencies use dashed arrows.
 */
public class ComponentDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(ComponentDrawIoExporter.class);

    private static final int COMP_W  = 160;
    private static final int COMP_H  = 50;
    private static final int IFACE_D = 30; // interface circle diameter
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 60;
    private static final int COLS    = 3;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_COMP  =
            "shape=component;align=left;spacingLeft=36;html=1;"
                    + "fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_IFACE =
            "ellipse;aspect=fixed;html=1;fillColor=#ffffff;strokeColor=#000000;";
    private static final String STYLE_DEP   =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;dashed=1;";
    private static final String STYLE_USE   =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;";

    private static final Pattern P_COMP = Pattern.compile(
            "\\[([^\\]]+)\\]|^\\s*component\\s+\"?([\\w][\\w\\s.]*)\"?(?:\\s+as\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_IFACE = Pattern.compile(
            "^\\s*(?:interface|\\()\\s+\"?([\\w][\\w\\s.]*)\"?(?:\\s+as\\s+(\\w+))?\\)?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(?:\\[([^\\]]+)\\]|(\\w+))\\s*(\\.+>|-->|->|--)\\s*(?:\\[([^\\]]+)\\]|(\\w+))(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private enum NodeType { COMPONENT, INTERFACE }
    private record Node(String id, String name, NodeType type) {}
    private record Dep(String from, String to, String label, boolean dashed) {}

    public String export(String plantUml) {
        log.info("[ComponentDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Node> nodes = new LinkedHashMap<>();
        List<Dep> deps = new ArrayList<>();

        // Components
        Matcher cm = P_COMP.matcher(plantUml);
        while (cm.find()) {
            String name = cm.group(1) != null ? cm.group(1).trim() : cm.group(2).trim();
            String alias = cm.group(3) != null ? cm.group(3).trim() : name.replaceAll("[^\\w]", "_");
            nodes.put(alias, new Node(DrawIoXml.uid(), name, NodeType.COMPONENT));
        }

        // Interfaces
        Matcher im = P_IFACE.matcher(plantUml);
        while (im.find()) {
            String name = im.group(1).trim();
            String alias = im.group(2) != null ? im.group(2).trim() : name.replaceAll("[^\\w]", "_");
            nodes.put(alias, new Node(DrawIoXml.uid(), name, NodeType.INTERFACE));
        }

        // Relationships
        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from = rm.group(1) != null ? rm.group(1).trim() : rm.group(2) != null ? rm.group(2).trim() : null;
            String arrow = rm.group(3).trim();
            String to   = rm.group(4) != null ? rm.group(4).trim() : rm.group(5) != null ? rm.group(5).trim() : null;
            String label = rm.group(6) != null ? rm.group(6).trim() : "";
            if (from == null || to == null) continue;
            String fa = from.replaceAll("[^\\w]", "_");
            String ta = to.replaceAll("[^\\w]", "_");
            nodes.computeIfAbsent(fa, k -> new Node(DrawIoXml.uid(), from, NodeType.COMPONENT));
            nodes.computeIfAbsent(ta, k -> new Node(DrawIoXml.uid(), to,   NodeType.COMPONENT));
            boolean dashed = arrow.contains(".");
            deps.add(new Dep(fa, ta, label, dashed));
        }

        log.info("[ComponentDrawIo] nodes={} deps={}", nodes.size(), deps.size());

        DrawIoXml xml = new DrawIoXml();
        List<Node> list = new ArrayList<>(nodes.values());

        for (int i = 0; i < list.size(); i++) {
            Node n = list.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x   = START_X + col * (COMP_W + H_GAP);
            int y   = START_Y + row * (COMP_H + V_GAP);
            if (n.type() == NodeType.INTERFACE) {
                xml.addRaw(xml.rect(n.id(), n.name(), x + (COMP_W - IFACE_D) / 2, y,
                        IFACE_D, IFACE_D, STYLE_IFACE));
            } else {
                xml.addRaw(xml.rect(n.id(), n.name(), x, y, COMP_W, COMP_H, STYLE_COMP));
            }
        }

        for (Dep d : deps) {
            Node src = nodes.get(d.from());
            Node tgt = nodes.get(d.to());
            if (src == null || tgt == null) continue;
            String style = d.dashed() ? STYLE_DEP : STYLE_USE;
            xml.addRaw(xml.edge(DrawIoXml.uid(), d.label(), src.id(), tgt.id(), style));
        }

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (COMP_W + H_GAP);
        int totalH = START_Y * 2 + rows * (COMP_H + V_GAP);

        log.info("[ComponentDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }
}
