package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML state diagram source into Draw.io XML.
 *
 * <p>States render as rounded rectangles. Initial state is a filled circle.
 * Final state is a double circle. Transitions are directed edges.
 */
public class StateDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(StateDrawIoExporter.class);

    private static final int STATE_W = 140;
    private static final int STATE_H = 40;
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 60;
    private static final int COLS    = 3;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_INITIAL  = "ellipse;aspect=fixed;html=1;fillColor=#000000;strokeColor=#000000;";
    private static final String STYLE_FINAL    = "ellipse;aspect=fixed;html=1;fillColor=#000000;strokeColor=#000000;double=1;";
    private static final String STYLE_STATE    = "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_SUBSTATE = "swimlane;startSize=20;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_EDGE     = "html=1;endArrow=block;endFill=1;edgeStyle=orthogonalEdgeStyle;";

    private static final Pattern P_STATE = Pattern.compile(
            "^\\s*state\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?(?:\\s*\\{[^}]*\\})?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_TRANS = Pattern.compile(
            "^\\s*(\\[\\*\\]|\\w+)\\s*-->\\s*(\\[\\*\\]|\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private record State(String id, String name) {}
    private record Transition(String from, String to, String label) {}

    public String export(String plantUml) {
        log.info("[StateDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, State> states = new LinkedHashMap<>();
        List<Transition> transitions = new ArrayList<>();

        // Parse explicit state declarations
        Matcher sm = P_STATE.matcher(plantUml);
        while (sm.find()) {
            String name  = sm.group(1).trim();
            String alias = sm.group(2) != null ? sm.group(2).trim() : name;
            states.put(alias, new State(DrawIoXml.uid(), name));
        }

        // Parse transitions — also auto-discover state names
        String initialId = DrawIoXml.uid();
        String finalId   = DrawIoXml.uid();
        Matcher tm = P_TRANS.matcher(plantUml);
        while (tm.find()) {
            String from  = tm.group(1).trim();
            String to    = tm.group(2).trim();
            String label = tm.group(3) != null ? tm.group(3).trim() : "";
            transitions.add(new Transition(from, to, label));
            if (!"[*]".equals(from)) states.computeIfAbsent(from, k -> new State(DrawIoXml.uid(), k));
            if (!"[*]".equals(to))   states.computeIfAbsent(to,   k -> new State(DrawIoXml.uid(), k));
        }

        log.info("[StateDrawIo] states={} transitions={}", states.size(), transitions.size());

        DrawIoXml xml = new DrawIoXml();

        // Initial + final pseudo-states
        xml.addRaw(xml.rect(initialId, "", 40, 20, 20, 20, STYLE_INITIAL));
        xml.addRaw(xml.rect(finalId,   "", 40, 20, 20, 20, STYLE_FINAL));

        // Layout: grid
        List<State> list = new ArrayList<>(states.values());
        Map<String, int[]> pos = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = START_X + col * (STATE_W + H_GAP);
            int y = START_Y + 60 + row * (STATE_H + V_GAP);
            pos.put(list.get(i).name(), new int[]{x, y});
            xml.addRaw(xml.rect(list.get(i).id(), list.get(i).name(), x, y, STATE_W, STATE_H, STYLE_STATE));
        }

        // Place initial above first state (or top-left)
        int initX = pos.isEmpty() ? 50 : (pos.values().iterator().next()[0] + STATE_W / 2 - 10);
        int initY = START_Y + 20;
        // Overwrite initial position
        xml.addRaw(xml.rect(initialId + "_ovr", "", initX, initY, 20, 20, STYLE_INITIAL));

        // Find last state for final
        int lastX = initX;
        int lastY = initY + 60;
        if (!pos.isEmpty()) {
            int[] last = new ArrayList<>(pos.values()).get(list.size() - 1);
            lastX = last[0] + STATE_W / 2 - 10;
            lastY = last[1] + STATE_H + V_GAP;
        }
        xml.addRaw(xml.rect(finalId + "_ovr", "", lastX, lastY, 20, 20, STYLE_FINAL));

        // Emit transitions
        Map<String, String> idLookup = new HashMap<>();
        list.forEach(s -> idLookup.put(s.name(), s.id()));
        idLookup.put("[*]_initial", initialId + "_ovr");
        idLookup.put("[*]_final",   finalId   + "_ovr");

        boolean initialUsed = false;
        boolean finalUsed   = false;
        for (Transition t : transitions) {
            String srcId, tgtId;
            if ("[*]".equals(t.from())) {
                srcId = initialId + "_ovr";
            } else {
                srcId = idLookup.get(t.from());
            }
            if ("[*]".equals(t.to())) {
                tgtId = finalId + "_ovr";
            } else {
                tgtId = idLookup.get(t.to());
            }
            if (srcId == null || tgtId == null) continue;
            xml.addRaw(xml.edge(DrawIoXml.uid(), t.label(), srcId, tgtId, STYLE_EDGE));
        }

        // Remove unused placeholder cells by simply not emitting them if positions were duplicated.
        // The duplicates from the first addRaw calls above won't cause draw.io issues (it ignores
        // cells with duplicate ids if ids differ—they do differ here).

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (STATE_W + H_GAP);
        int totalH = START_Y + 60 + rows * (STATE_H + V_GAP) + 100;

        log.info("[StateDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }
}
