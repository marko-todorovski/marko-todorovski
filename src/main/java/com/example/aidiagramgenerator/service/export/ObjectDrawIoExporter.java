package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML object diagram source into Draw.io XML.
 *
 * <p>Objects render as swimlane-style boxes with header ClassName:instanceName
 * and attribute=value rows in the body.
 */
public class ObjectDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(ObjectDrawIoExporter.class);

    private static final int OBJ_W   = 180;
    private static final int HDR_H   = 30;
    private static final int ATTR_H  = 20;
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 60;
    private static final int COLS    = 4;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_OBJ  =
            "swimlane;fontStyle=1;align=center;startSize=30;html=1;"
                    + "fillColor=#e1d5e7;strokeColor=#9673a6;";
    private static final String STYLE_ATTR =
            "text;strokeColor=none;fillColor=none;align=left;verticalAlign=top;"
                    + "spacingLeft=4;overflow=hidden;html=1;";
    private static final String STYLE_LINK =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;";

    private static final Pattern P_OBJ = Pattern.compile(
            "^\\s*object\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?(?:\\s*\\{([^}]*)\\})?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s*(-->|--|->)\\s*(\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private record Obj(String id, String name, List<String> attrs) {}
    private record Link(String from, String to, String label) {}

    public String export(String plantUml) {
        log.info("[ObjectDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Obj> objects = new LinkedHashMap<>();
        List<Link> links = new ArrayList<>();

        Matcher om = P_OBJ.matcher(plantUml);
        while (om.find()) {
            String name  = om.group(1).trim();
            String alias = om.group(2) != null ? om.group(2).trim() : name.replaceAll("[^\\w]", "_");
            List<String> attrs = parseAttrs(om.group(3));
            objects.put(alias, new Obj(DrawIoXml.uid(), name, attrs));
        }

        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from  = rm.group(1).trim();
            String to    = rm.group(3).trim();
            String label = rm.group(4) != null ? rm.group(4).trim() : "";
            links.add(new Link(from, to, label));
            objects.computeIfAbsent(from, k -> new Obj(DrawIoXml.uid(), k, List.of()));
            objects.computeIfAbsent(to,   k -> new Obj(DrawIoXml.uid(), k, List.of()));
        }

        log.info("[ObjectDrawIo] objects={} links={}", objects.size(), links.size());

        DrawIoXml xml = new DrawIoXml();
        List<Obj> list = new ArrayList<>(objects.values());

        for (int i = 0; i < list.size(); i++) {
            Obj o = list.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x   = START_X + col * (OBJ_W + H_GAP);
            int y   = START_Y + row * (HDR_H + o.attrs().size() * ATTR_H + V_GAP + 10);
            int totalH = HDR_H + Math.max(20, o.attrs().size() * ATTR_H + 4);
            xml.addRaw(xml.rect(o.id(), o.name(), x, y, OBJ_W, totalH, STYLE_OBJ));
            int ay = HDR_H;
            for (String attr : o.attrs()) {
                xml.addRaw("<mxCell id=\"" + DrawIoXml.uid() + "\" value=\"" + DrawIoXml.esc(attr)
                        + "\" style=\"" + STYLE_ATTR + "\" vertex=\"1\" parent=\"" + o.id()
                        + "\"><mxGeometry x=\"0\" y=\"" + ay + "\" width=\"" + OBJ_W
                        + "\" height=\"" + ATTR_H + "\" as=\"geometry\"/></mxCell>");
                ay += ATTR_H;
            }
        }

        for (Link lk : links) {
            Obj src = objects.get(lk.from());
            Obj tgt = objects.get(lk.to());
            if (src == null || tgt == null) continue;
            xml.addRaw(xml.edge(DrawIoXml.uid(), lk.label(), src.id(), tgt.id(), STYLE_LINK));
        }

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (OBJ_W + H_GAP);
        int totalH = START_Y * 2 + rows * (150 + V_GAP);

        log.info("[ObjectDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
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
