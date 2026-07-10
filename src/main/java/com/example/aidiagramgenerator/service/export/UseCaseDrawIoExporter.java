package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML use-case diagram source into Draw.io XML.
 *
 * <p>Visual structure:
 * <ul>
 *   <li>Actors rendered as Draw.io actor shapes on the left/right edge</li>
 *   <li>Use cases rendered as ovals in the centre</li>
 *   <li>System boundary drawn as a large rectangle container</li>
 *   <li>Associations drawn as plain lines; include/extend as labelled dashed lines</li>
 * </ul>
 */
public class UseCaseDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(UseCaseDrawIoExporter.class);

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int ACTOR_W  = 40;
    private static final int ACTOR_H  = 60;
    private static final int UC_W     = 150;
    private static final int UC_H     = 50;
    private static final int H_GAP    = 60;
    private static final int V_GAP    = 40;
    private static final int BOUNDARY_PADDING = 40;
    private static final int START_X  = 40;
    private static final int START_Y  = 60;

    // ── Styles ───────────────────────────────────────────────────────────────
    private static final String STYLE_ACTOR =
            "shape=mxgraph.flowchart.actor;whiteSpace=wrap;html=1;"
                    + "fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_USECASE =
            "ellipse;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_BOUNDARY =
            "points=[[0,0],[0.25,0],[0.5,0],[0.75,0],[1,0],[1,0.25],[1,0.5],[1,0.75],"
                    + "[1,1],[0.75,1],[0.5,1],[0.25,1],[0,1],[0,0.75],[0,0.5],[0,0.25]];"
                    + "shape=mxgraph.flowchart.start_2;fillColor=none;fontSize=12;"
                    + "fontStyle=1;strokeColor=#666666;swimlane;startSize=30;";
    private static final String STYLE_ASSOC =
            "html=1;endArrow=none;startArrow=none;edgeStyle=orthogonalEdgeStyle;";
    private static final String STYLE_INCLUDE =
            "html=1;endArrow=open;endFill=0;dashed=1;edgeStyle=orthogonalEdgeStyle;";
    private static final String STYLE_EXTEND =
            "html=1;endArrow=open;endFill=0;dashed=1;edgeStyle=orthogonalEdgeStyle;strokeColor=#FF8000;";
    private static final String STYLE_INHERIT =
            "html=1;endArrow=block;endFill=0;edgeStyle=orthogonalEdgeStyle;";

    // ── Patterns ─────────────────────────────────────────────────────────────
    private static final Pattern P_ACTOR = Pattern.compile(
            "^\\s*actor\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_USECASE = Pattern.compile(
            "\\(([^)]+)\\)|usecase\\s+\"([^\"]+)\"(?:\\s+as\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s*(-->|->|--)\\s*(\\w+|\\([^)]+\\))(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_INCLUDE = Pattern.compile(
            "\\(([^)]+)\\)\\s*\\.+>\\s*\\(([^)]+)\\)(?:\\s*:\\s*<<include>>)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_EXTEND = Pattern.compile(
            "\\(([^)]+)\\)\\s*\\.+>\\s*\\(([^)]+)\\)\\s*:\\s*<<extend>>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_BOUNDARY = Pattern.compile(
            "rectangle\\s+\"([^\"]+)\"\\s*\\{|package\\s+\"([^\"]+)\"\\s*\\{",
            Pattern.CASE_INSENSITIVE);

    // ── Records ───────────────────────────────────────────────────────────────
    private record Actor(String alias, String display) {}
    private record UseCase(String alias, String display) {}
    private record Rel(String from, String to, String type, String label) {}

    // ── Entry point ───────────────────────────────────────────────────────────

    public String export(String plantUml) {
        log.info("[UseCaseDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Actor> actors = new LinkedHashMap<>();
        Map<String, UseCase> useCases = new LinkedHashMap<>();
        List<Rel> rels = new ArrayList<>();

        // Parse actors
        Matcher am = P_ACTOR.matcher(plantUml);
        while (am.find()) {
            String display = am.group(1).trim();
            String alias = am.group(2) != null ? am.group(2).trim() : sanitize(display);
            actors.put(alias, new Actor(alias, display));
        }

        // Parse use cases from (label) syntax and usecase keyword
        Matcher um = P_USECASE.matcher(plantUml);
        while (um.find()) {
            String label = um.group(1) != null ? um.group(1).trim() : um.group(2).trim();
            String alias = um.group(3) != null ? um.group(3).trim() : sanitize(label);
            useCases.put(alias, new UseCase(alias, label));
        }

        // Parse include/extend
        Matcher inc = P_INCLUDE.matcher(plantUml);
        while (inc.find()) {
            String from = sanitize(inc.group(1).trim());
            String to   = sanitize(inc.group(2).trim());
            useCases.computeIfAbsent(from, k -> new UseCase(k, inc.group(1).trim()));
            useCases.computeIfAbsent(to,   k -> new UseCase(k, inc.group(2).trim()));
            rels.add(new Rel(from, to, "include", "<<include>>"));
        }
        Matcher ext = P_EXTEND.matcher(plantUml);
        while (ext.find()) {
            String from = sanitize(ext.group(1).trim());
            String to   = sanitize(ext.group(2).trim());
            useCases.computeIfAbsent(from, k -> new UseCase(k, ext.group(1).trim()));
            useCases.computeIfAbsent(to,   k -> new UseCase(k, ext.group(2).trim()));
            rels.add(new Rel(from, to, "extend", "<<extend>>"));
        }

        // Parse generic relationships
        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from  = rm.group(1).trim();
            String to    = rm.group(3).trim().replaceAll("[()]+", "");
            String label = rm.group(4) != null ? rm.group(4).trim() : "";
            rels.add(new Rel(from, sanitize(to), "assoc", label));
        }

        log.info("[UseCaseDrawIo] actors={} usecases={} rels={}", actors.size(), useCases.size(), rels.size());

        // ── Layout ─────────────────────────────────────────────────────────────
        DrawIoXml xml = new DrawIoXml();
        Map<String, String> idMap = new LinkedHashMap<>();

        int ucCount = useCases.size();
        int ucCols = Math.max(1, (int) Math.ceil(Math.sqrt(ucCount)));
        int ucRows = ucCount == 0 ? 0 : (ucCount + ucCols - 1) / ucCols;

        int boundaryX = START_X + ACTOR_W + H_GAP;
        int boundaryY = START_Y;
        int boundaryW = ucCols * (UC_W + H_GAP) + BOUNDARY_PADDING;
        int boundaryH = Math.max(1, ucRows) * (UC_H + V_GAP) + BOUNDARY_PADDING * 2;

        // System boundary
        String boundaryId = DrawIoXml.uid();
        xml.addRaw("<mxCell id=\"" + boundaryId + "\" value=\"System\" "
                + "style=\"swimlane;startSize=30;fillColor=none;strokeColor=#666666;dashed=0;\"" 
                + " vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"" + boundaryX + "\" y=\"" + boundaryY
                + "\" width=\"" + boundaryW + "\" height=\"" + boundaryH
                + "\" as=\"geometry\"/></mxCell>");

        // Use cases inside boundary
        List<UseCase> ucList = new ArrayList<>(useCases.values());
        for (int i = 0; i < ucList.size(); i++) {
            UseCase uc = ucList.get(i);
            int col = i % ucCols;
            int row = i / ucCols;
            int x = BOUNDARY_PADDING / 2 + col * (UC_W + H_GAP);
            int y = 30 + row * (UC_H + V_GAP);
            String ucId = DrawIoXml.uid();
            idMap.put(uc.alias(), ucId);
            // child of boundary swimlane
            xml.addRaw("<mxCell id=\"" + ucId + "\" value=\"" + DrawIoXml.esc(uc.display())
                    + "\" style=\"" + STYLE_USECASE + "\" vertex=\"1\" parent=\"" + boundaryId + "\">"
                    + "<mxGeometry x=\"" + x + "\" y=\"" + y + "\" width=\"" + UC_W
                    + "\" height=\"" + UC_H + "\" as=\"geometry\"/></mxCell>");
        }

        // Actors (left side)
        List<Actor> actorList = new ArrayList<>(actors.values());
        int actorSpacing = Math.max(ACTOR_H + V_GAP, boundaryH / Math.max(actorList.size(), 1));
        for (int i = 0; i < actorList.size(); i++) {
            Actor a = actorList.get(i);
            int ax = START_X;
            int ay = START_Y + i * actorSpacing + (actorSpacing - ACTOR_H) / 2;
            String aId = DrawIoXml.uid();
            idMap.put(a.alias(), aId);
            xml.addRaw(xml.rect(aId, a.display(), ax, ay, ACTOR_W, ACTOR_H, STYLE_ACTOR));
        }

        // Relationships — edges connect to absolute cell ids
        for (Rel r : rels) {
            String srcId = idMap.get(r.from());
            String tgtId = idMap.get(r.to());
            if (srcId == null || tgtId == null) continue;
            String style = switch (r.type()) {
                case "include" -> STYLE_INCLUDE;
                case "extend"  -> STYLE_EXTEND;
                case "inherit" -> STYLE_INHERIT;
                default        -> STYLE_ASSOC;
            };
            xml.addRaw(xml.edge(DrawIoXml.uid(), r.label(), srcId, tgtId, style));
        }

        int totalW = boundaryX + boundaryW + H_GAP + 40;
        int totalH = boundaryY + boundaryH + 60;
        log.info("[UseCaseDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    private String sanitize(String s) {
        return s.replaceAll("[^\\w]", "_");
    }
}
