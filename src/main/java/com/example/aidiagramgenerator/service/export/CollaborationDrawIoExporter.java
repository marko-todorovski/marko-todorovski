package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML collaboration/communication diagram source into Draw.io XML.
 *
 * <p>Objects are placed in a circular-ish grid. Numbered messages appear as
 * labelled edges between objects.
 */
public class CollaborationDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(CollaborationDrawIoExporter.class);

    private static final int NODE_W  = 140;
    private static final int NODE_H  = 50;
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 60;
    private static final int COLS    = 3;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_OBJ  =
            "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;";
    private static final String STYLE_LINK =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;";
    private static final String STYLE_MSG  =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=block;endFill=1;";

    // participant / object declarations
    private static final Pattern P_OBJ = Pattern.compile(
            "^\\s*(?:object|participant)\\s+\"?([\\w][\\w\\s.]*)\"?(?:\\s+as\\s+(\\w+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    // numbered messages:  A -> B : 1: doSomething
    private static final Pattern P_MSG = Pattern.compile(
            "^\\s*(\\w+)\\s*(->|-->)\\s*(\\w+)\\s*:\\s*(.+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private record Obj(String id, String name) {}
    private record Msg(String from, String to, String label) {}

    public String export(String plantUml) {
        log.info("[CollabDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Obj> objects = new LinkedHashMap<>();
        List<Msg> messages = new ArrayList<>();

        Matcher om = P_OBJ.matcher(plantUml);
        while (om.find()) {
            String name  = om.group(1).trim();
            String alias = om.group(2) != null ? om.group(2).trim() : name.replaceAll("[^\\w]", "_");
            objects.put(alias, new Obj(DrawIoXml.uid(), name));
        }

        Matcher mm = P_MSG.matcher(plantUml);
        while (mm.find()) {
            String from  = mm.group(1).trim();
            String to    = mm.group(3).trim();
            String label = mm.group(4).trim();
            messages.add(new Msg(from, to, label));
            objects.computeIfAbsent(from, k -> new Obj(DrawIoXml.uid(), k));
            objects.computeIfAbsent(to,   k -> new Obj(DrawIoXml.uid(), k));
        }

        log.info("[CollabDrawIo] objects={} messages={}", objects.size(), messages.size());

        DrawIoXml xml = new DrawIoXml();
        List<Obj> list = new ArrayList<>(objects.values());

        for (int i = 0; i < list.size(); i++) {
            Obj o = list.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x   = START_X + col * (NODE_W + H_GAP);
            int y   = START_Y + row * (NODE_H + V_GAP);
            xml.addRaw(xml.rect(o.id(), o.name(), x, y, NODE_W, NODE_H, STYLE_OBJ));
        }

        for (Msg m : messages) {
            Obj src = objects.get(m.from());
            Obj tgt = objects.get(m.to());
            if (src == null || tgt == null) continue;
            xml.addRaw(xml.edge(DrawIoXml.uid(), m.label(), src.id(), tgt.id(), STYLE_MSG));
        }

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (NODE_W + H_GAP);
        int totalH = START_Y * 2 + rows * (NODE_H + V_GAP);

        log.info("[CollabDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }
}
