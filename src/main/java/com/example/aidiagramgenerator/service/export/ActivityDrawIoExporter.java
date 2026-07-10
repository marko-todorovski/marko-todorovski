package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML activity diagram source into Draw.io XML.
 *
 * <p>Nodes: start (filled circle), end (double circle), action (rounded rectangle),
 * decision (diamond), fork/join (bar). Flow arrows connect them top-to-bottom.
 */
public class ActivityDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(ActivityDrawIoExporter.class);

    private static final int NODE_W  = 160;
    private static final int NODE_H  = 40;
    private static final int DEC_W   = 100;
    private static final int DEC_H   = 60;
    private static final int FORK_W  = 160;
    private static final int FORK_H  = 10;
    private static final int V_GAP   = 40;
    private static final int START_X = 140;
    private static final int START_Y = 40;
    private static final int COL_W   = NODE_W + 80;

    private static final String STYLE_START  = "ellipse;aspect=fixed;html=1;fillColor=#000000;strokeColor=#000000;";
    private static final String STYLE_END    = "ellipse;aspect=fixed;html=1;fillColor=#000000;strokeColor=#000000;"
            + "double=1;";
    private static final String STYLE_ACTION = "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_DECISION =
            "rhombus;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;";
    private static final String STYLE_FORK   =
            "shape=mxgraph.bpmn.shape;perimeter=mxPerimeter.RectanglePerimeter;"
                    + "symbol=general;html=1;fillColor=#000000;strokeColor=#000000;";
    private static final String STYLE_EDGE   = "html=1;endArrow=block;endFill=1;edgeStyle=orthogonalEdgeStyle;";
    private static final String STYLE_GUARD  = "html=1;endArrow=block;endFill=1;edgeStyle=orthogonalEdgeStyle;dashed=1;";

    private static final Pattern P_START     = Pattern.compile("^\\s*start\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_STOP      = Pattern.compile("^\\s*stop\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ACTION    = Pattern.compile("^\\s*:([^;]+);\\s*$", Pattern.MULTILINE);
    private static final Pattern P_IF        = Pattern.compile("^\\s*if\\s*\\(([^)]+)\\)\\s*then(?:\\s*\\(([^)]+)\\))?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ELSE      = Pattern.compile("^\\s*else(?:\\s*\\(([^)]+)\\))?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ENDIF     = Pattern.compile("^\\s*endif\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_FORK      = Pattern.compile("^\\s*fork(?:\\s+again)?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ENDFORK   = Pattern.compile("^\\s*end\\s*fork\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ARROW     = Pattern.compile("^\\s*->\\s*(.+)?\\s*$", Pattern.MULTILINE);

    // Simple linear extraction — works well enough for most activity diagrams
    public String export(String plantUml) {
        log.info("[ActivityDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        DrawIoXml xml = new DrawIoXml();
        List<String[]> nodes = new ArrayList<>(); // [id, label, style, w, h]
        List<String[]> edges = new ArrayList<>();  // [srcId, tgtId, label, style]

        String startId = DrawIoXml.uid();
        String endId   = DrawIoXml.uid();
        int nodeW20 = 20; // start/end diameter

        // Parse ordered sequence of nodes
        String[] lines = plantUml.split("\n");
        String prevId = null;
        boolean started = false;

        for (String raw : lines) {
            String line = raw.trim();

            if (line.equalsIgnoreCase("start")) {
                nodes.add(new String[]{startId, "", STYLE_START, "20", "20"});
                prevId = startId;
                started = true;
                continue;
            }
            if (line.equalsIgnoreCase("stop") || line.equalsIgnoreCase("end")) {
                nodes.add(new String[]{endId, "", STYLE_END, "20", "20"});
                if (prevId != null) edges.add(new String[]{prevId, endId, "", STYLE_EDGE});
                prevId = endId;
                continue;
            }

            // Action :label;
            Matcher am = P_ACTION.matcher(line);
            if (am.matches()) {
                String actionId = DrawIoXml.uid();
                String label = am.group(1).trim();
                nodes.add(new String[]{actionId, label, STYLE_ACTION,
                        String.valueOf(NODE_W), String.valueOf(NODE_H)});
                if (prevId != null) edges.add(new String[]{prevId, actionId, "", STYLE_EDGE});
                prevId = actionId;
                continue;
            }

            // if/decision
            Matcher im = P_IF.matcher(line);
            if (im.find()) {
                String decId = DrawIoXml.uid();
                String label = im.group(1).trim();
                nodes.add(new String[]{decId, label, STYLE_DECISION,
                        String.valueOf(DEC_W), String.valueOf(DEC_H)});
                if (prevId != null) edges.add(new String[]{prevId, decId, "", STYLE_EDGE});
                prevId = decId;
                continue;
            }

            // fork
            if (line.toLowerCase().startsWith("fork")) {
                String forkId = DrawIoXml.uid();
                nodes.add(new String[]{forkId, "", STYLE_FORK,
                        String.valueOf(FORK_W), String.valueOf(FORK_H)});
                if (prevId != null) edges.add(new String[]{prevId, forkId, "", STYLE_EDGE});
                prevId = forkId;
                continue;
            }
        }

        // If no nodes were found do a generic word-based fallback
        if (nodes.isEmpty()) {
            return genericFallback(plantUml);
        }

        log.info("[ActivityDrawIo] nodes={} edges={}", nodes.size(), edges.size());

        // Layout top-to-bottom single column
        int y = START_Y;
        Map<String, int[]> posMap = new LinkedHashMap<>();
        for (String[] n : nodes) {
            int w = Integer.parseInt(n[3]);
            int h = Integer.parseInt(n[4]);
            int x = START_X + (NODE_W - w) / 2;
            posMap.put(n[0], new int[]{x, y, w, h});
            xml.addRaw(xml.rect(n[0], n[1], x, y, w, h, n[2]));
            y += h + V_GAP;
        }

        for (String[] e : edges) {
            xml.addRaw(xml.edge(DrawIoXml.uid(), e[2], e[0], e[1], e[3]));
        }

        int totalW = START_X * 2 + NODE_W + 80;
        int totalH = y + 60;
        log.info("[ActivityDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    private String genericFallback(String plantUml) {
        DrawIoXml xml = new DrawIoXml();
        Pattern wp = Pattern.compile(":([^;]+);", Pattern.DOTALL);
        Matcher wm = wp.matcher(plantUml);
        List<String> labels = new ArrayList<>();
        while (wm.find()) labels.add(wm.group(1).trim());

        int y = START_Y;
        String prevId = null;
        for (String label : labels) {
            String id = DrawIoXml.uid();
            xml.addRaw(xml.rect(id, label, START_X, y, NODE_W, NODE_H, STYLE_ACTION));
            if (prevId != null) xml.addRaw(xml.edge(DrawIoXml.uid(), "", prevId, id, STYLE_EDGE));
            prevId = id;
            y += NODE_H + V_GAP;
        }
        return xml.build(START_X * 2 + NODE_W + 80, y + 40);
    }
}
