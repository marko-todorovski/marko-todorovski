package com.example.aidiagramgenerator.service.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Low-level Draw.io XML assembler.
 * Holds raw mxCell strings and wraps them in the required mxfile envelope.
 * All coordinate math is done by the dedicated per-type exporters.
 */
public class DrawIoXml {

    private final List<String> cells = new ArrayList<>();
    private int elementCount = 0;

    /** Append a pre-rendered {@code <mxCell>} string verbatim. */
    public void addRaw(String cellXml) {
        cells.add(cellXml);
        elementCount++;
    }

    public int getElementCount() {
        return elementCount;
    }

    // ── shape builders ────────────────────────────────────────────────────────

    /** Rectangle vertex (default node). */
    public String rect(String id, String label, int x, int y, int w, int h, String style) {
        return "<mxCell id=\"" + id + "\" value=\"" + esc(label) + "\" style=\"" + style
                + "\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"" + x + "\" y=\"" + y
                + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\"/></mxCell>";
    }

    /** Edge between two cell ids. */
    public String edge(String id, String label, String srcId, String tgtId, String style) {
        return "<mxCell id=\"" + id + "\" value=\"" + esc(label) + "\" style=\"" + style
                + "\" edge=\"1\" parent=\"1\" source=\"" + srcId + "\" target=\"" + tgtId
                + "\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>";
    }

    /**
     * Edge with explicit waypoints (for sequence diagram horizontal arrows that
     * must span between fixed x positions at a given y).
     */
    public String edgeWithPoints(String id, String label, String srcId, String tgtId,
                                  String style, List<int[]> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("<mxCell id=\"").append(id).append("\" value=\"").append(esc(label))
          .append("\" style=\"").append(style)
          .append("\" edge=\"1\" parent=\"1\" source=\"").append(srcId)
          .append("\" target=\"").append(tgtId).append("\">");
        sb.append("<mxGeometry relative=\"1\" as=\"geometry\">");
        if (!points.isEmpty()) {
            sb.append("<Array as=\"points\">");
            for (int[] p : points) {
                sb.append("<mxPoint x=\"").append(p[0]).append("\" y=\"").append(p[1]).append("\"/>");
            }
            sb.append("</Array>");
        }
        sb.append("</mxGeometry></mxCell>");
        return sb.toString();
    }

    /** Floating label cell (no source/target). */
    public String label(String id, String text, int x, int y, int w, int h) {
        return "<mxCell id=\"" + id + "\" value=\"" + esc(text)
                + "\" style=\"text;html=1;align=center;verticalAlign=middle;resizable=0;\""
                + " vertex=\"1\" parent=\"1\"><mxGeometry x=\"" + x + "\" y=\"" + y
                + "\" width=\"" + w + "\" height=\"" + h + "\" as=\"geometry\"/></mxCell>";
    }

    // ── build ─────────────────────────────────────────────────────────────────

    public String build(int pageW, int pageH) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mxfile host=\"app.diagrams.net\" modified=\"").append(Instant.now())
           .append("\" agent=\"AI Diagram Generator\" version=\"21.0.0\" type=\"device\">\n");
        xml.append("  <diagram id=\"").append(uid()).append("\" name=\"Page-1\">\n");
        xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" ")
           .append("guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" ")
           .append("page=\"1\" pageScale=\"1\" pageWidth=\"").append(pageW)
           .append("\" pageHeight=\"").append(pageH).append("\" math=\"0\" shadow=\"0\">\n");
        xml.append("      <root>\n");
        xml.append("        <mxCell id=\"0\"/>\n");
        xml.append("        <mxCell id=\"1\" parent=\"0\"/>\n");
        for (String cell : cells) {
            xml.append("        ").append(cell).append("\n");
        }
        xml.append("      </root>\n");
        xml.append("    </mxGraphModel>\n");
        xml.append("  </diagram>\n");
        xml.append("</mxfile>\n");
        return xml.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    public static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
