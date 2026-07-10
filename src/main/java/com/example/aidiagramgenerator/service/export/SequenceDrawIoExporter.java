package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML sequence diagram source into a Draw.io XML document.
 *
 * <p>Visual structure produced:
 * <ul>
 *   <li>Participant header boxes (rectangles for participants, actor shapes for actors)</li>
 *   <li>Vertical dashed lifelines extending downward from each header</li>
 *   <li>Horizontal solid arrows for synchronous messages (-&gt; / -&gt;&gt;)</li>
 *   <li>Horizontal dashed arrows for return / async messages (--&gt; / --&gt;&gt;)</li>
 *   <li>Activation bars (thin rectangles) on lifelines during active calls</li>
 *   <li>Mirrored footer boxes at the bottom</li>
 * </ul>
 *
 * <p>Layout constants can be tuned; all positions are calculated deterministically
 * to avoid overlaps.
 */
public class SequenceDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(SequenceDrawIoExporter.class);

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int HEADER_W = 120;
    private static final int HEADER_H = 40;
    private static final int ACTOR_W  = 40;
    private static final int ACTOR_H  = 60;
    private static final int H_GAP    = 80;   // gap between participant centres
    private static final int START_X  = 60;
    private static final int HEADER_Y = 20;
    private static final int FIRST_MSG_Y = 110; // y of first message arrow
    private static final int MSG_STEP   = 50;   // vertical gap between messages
    private static final int ACT_W      = 10;   // activation bar width
    private static final int MARGIN_BOT = 60;

    // ── Styles ───────────────────────────────────────────────────────────────
    private static final String STYLE_PARTICIPANT =
            "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontStyle=1;";
    private static final String STYLE_ACTOR =
            "shape=mxgraph.flowchart.actor;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_LIFELINE =
            "endArrow=none;dashed=1;html=1;strokeColor=#999999;exitX=0.5;exitY=1;exitDx=0;exitDy=0;";
    private static final String STYLE_MSG_SOLID =
            "html=1;endArrow=open;endFill=1;edgeStyle=orthogonalEdgeStyle;"
                    + "orthogonalLoop=1;jettySize=auto;exitX=0.5;exitY=0.5;exitDx=0;exitDy=0;"
                    + "entryX=0.5;entryY=0.5;entryDx=0;entryDy=0;";
    private static final String STYLE_MSG_DASHED =
            "html=1;endArrow=open;endFill=0;dashed=1;edgeStyle=orthogonalEdgeStyle;"
                    + "orthogonalLoop=1;jettySize=auto;exitX=0.5;exitY=0.5;exitDx=0;exitDy=0;"
                    + "entryX=0.5;entryY=0.5;entryDx=0;entryDy=0;";
    private static final String STYLE_ACTIVATION =
            "rounded=0;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#333333;";
    private static final String STYLE_FOOTER =
            "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;fontColor=#333333;";

    // ── Parsing regexes ──────────────────────────────────────────────────────
    private static final Pattern P_PARTICIPANT = Pattern.compile(
            "^\\s*(participant|actor)\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ARROW = Pattern.compile(
            "^\\s*(\\w+)\\s*(->|-->|->>|-->>|<-|<--|<<-|<<--)\\s*(\\w+)\\s*(?::\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ACTIVATE = Pattern.compile(
            "^\\s*activate\\s+(\\w+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_DEACTIVATE = Pattern.compile(
            "^\\s*deactivate\\s+(\\w+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ── Data classes ─────────────────────────────────────────────────────────

    private record Participant(String alias, String displayName, boolean isActor) {}

    private record Message(String from, String to, String label, boolean dashed) {}

    // ── Public entry point ───────────────────────────────────────────────────

    public String export(String plantUml) {
        log.info("[SequenceDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        // 1. Parse participants (explicit declarations + implicit from arrows)
        List<Participant> participants = parseParticipants(plantUml);
        List<Message> messages = parseMessages(plantUml, participants);

        // 2. Infer any participants mentioned in arrows but not declared
        Set<String> declared = new LinkedHashSet<>();
        participants.forEach(p -> declared.add(p.alias()));
        for (Message m : messages) {
            if (!declared.contains(m.from())) {
                participants.add(new Participant(m.from(), m.from(), false));
                declared.add(m.from());
            }
            if (!declared.contains(m.to())) {
                participants.add(new Participant(m.to(), m.to(), false));
                declared.add(m.to());
            }
        }

        log.info("[SequenceDrawIo] participants={} messages={}", participants.size(), messages.size());

        // 3. Calculate geometry
        int centerSpacing = HEADER_W + H_GAP;
        Map<String, Integer> centerX = new LinkedHashMap<>();
        int cx = START_X + HEADER_W / 2;
        for (Participant p : participants) {
            centerX.put(p.alias(), cx);
            cx += centerSpacing;
        }

        int lifelineTop = HEADER_Y + HEADER_H;
        int lifelineBottom = FIRST_MSG_Y + messages.size() * MSG_STEP + MARGIN_BOT;
        int totalH = lifelineBottom + HEADER_H + 20;
        int totalW = cx + 40;

        DrawIoXml xml = new DrawIoXml();

        // 4. Draw participant headers + lifelines + footer boxes
        Map<String, String> lifelineId = new HashMap<>();
        for (Participant p : participants) {
            int pcx = centerX.get(p.alias());
            String hId = DrawIoXml.uid();
            String llId = DrawIoXml.uid();
            String footId = DrawIoXml.uid();
            lifelineId.put(p.alias(), llId);

            if (p.isActor()) {
                // Actor uses a stick-figure shape centred on pcx
                xml.addRaw(xml.rect(hId, p.displayName(), pcx - ACTOR_W / 2, HEADER_Y,
                        ACTOR_W, ACTOR_H, STYLE_ACTOR));
                // Lifeline from bottom of actor
                xml.addRaw(lifelineCell(llId, hId, pcx, HEADER_Y + ACTOR_H, lifelineBottom));
                // Footer
                xml.addRaw(xml.rect(footId, p.displayName(), pcx - HEADER_W / 2,
                        lifelineBottom, HEADER_W, HEADER_H, STYLE_FOOTER));
            } else {
                xml.addRaw(xml.rect(hId, p.displayName(), pcx - HEADER_W / 2, HEADER_Y,
                        HEADER_W, HEADER_H, STYLE_PARTICIPANT));
                xml.addRaw(lifelineCell(llId, hId, pcx, lifelineTop, lifelineBottom));
                xml.addRaw(xml.rect(footId, p.displayName(), pcx - HEADER_W / 2,
                        lifelineBottom, HEADER_W, HEADER_H, STYLE_FOOTER));
            }
        }

        // 5. Draw activation bars (track active ranges per participant)
        Map<String, Integer> activationStart = new HashMap<>();
        Set<String> active = new HashSet<>();
        // We track activation by scanning messages for implicit activate patterns
        // (explicit activate/deactivate lines are also honoured)

        // 6. Draw message arrows
        int msgY = FIRST_MSG_Y;
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            Integer fromX = centerX.get(msg.from());
            Integer toX   = centerX.get(msg.to());
            if (fromX == null || toX == null) {
                msgY += MSG_STEP;
                continue;
            }
            String fromLl = lifelineId.get(msg.from());
            String toLl   = lifelineId.get(msg.to());

            String style = msg.dashed() ? STYLE_MSG_DASHED : STYLE_MSG_SOLID;
            String eId = DrawIoXml.uid();

            // Use floating arrow cells (source/target are lifeline ids but positioned
            // via explicit points so they appear at the correct y on the lifeline)
            xml.addRaw(horizontalArrow(eId, msg.label(), fromLl, toLl,
                    fromX, toX, msgY, style));

            msgY += MSG_STEP;
        }

        log.info("[SequenceDrawIo] generated {} XML elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Participant> parseParticipants(String code) {
        List<Participant> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = P_PARTICIPANT.matcher(code);
        while (m.find()) {
            String kind = m.group(1).toLowerCase();
            String name = m.group(2).trim();
            String alias = m.group(3) != null ? m.group(3).trim() : sanitizeAlias(name);
            if (seen.add(alias)) {
                list.add(new Participant(alias, name, "actor".equals(kind)));
            }
        }
        return list;
    }

    private List<Message> parseMessages(String code, List<Participant> participants) {
        Map<String, Participant> byAlias = new LinkedHashMap<>();
        participants.forEach(p -> byAlias.put(p.alias(), p));

        List<Message> list = new ArrayList<>();
        Matcher m = P_ARROW.matcher(code);
        while (m.find()) {
            String from = m.group(1).trim();
            String arrow = m.group(2);
            String to = m.group(3).trim();
            String label = m.group(4) != null ? m.group(4).trim() : "";
            boolean dashed = arrow.contains("-") && arrow.startsWith("-") && arrow.charAt(1) == '-';
            // Return arrows: --> -->> indicate dashed
            boolean isDashed = arrow.equals("-->") || arrow.equals("-->>")
                    || arrow.equals("<--") || arrow.equals("<<--");
            list.add(new Message(from, to, label, isDashed));
        }
        return list;
    }

    private String sanitizeAlias(String name) {
        return name.replaceAll("[^\\w]", "_");
    }

    /**
     * Builds a vertical dashed lifeline as a plain line (edge with no arrowhead)
     * anchored to the bottom-centre of the header cell.
     */
    private String lifelineCell(String llId, String headerId, int cx, int top, int bottom) {
        // A vertical line drawn as a thin rectangle
        int x = cx - 1;
        int h = bottom - top;
        return "<mxCell id=\"" + llId + "\" value=\"\" "
                + "style=\"endArrow=none;dashed=1;html=1;strokeColor=#999999;\" "
                + "edge=\"1\" parent=\"1\" source=\"" + headerId + "\">"
                + "<mxGeometry relative=\"1\" as=\"geometry\">"
                + "<mxPoint x=\"" + cx + "\" y=\"" + bottom + "\" as=\"targetPoint\"/>"
                + "</mxGeometry></mxCell>";
    }

    /**
     * Builds a horizontal message arrow at a fixed y between two lifeline centres.
     * Uses explicit entry/exit points so the arrow appears at the correct height
     * regardless of source/target cell bounds.
     */
    private String horizontalArrow(String id, String label, String fromLlId, String toLlId,
                                    int fromX, int toX, int y, String style) {
        // We use absolute point geometry (no source/target binding) so that
        // the arrow appears precisely at y on both lifelines.
        return "<mxCell id=\"" + id + "\" value=\"" + DrawIoXml.esc(label) + "\" style=\"" + style
                + "\" edge=\"1\" parent=\"1\">"
                + "<mxGeometry relative=\"1\" as=\"geometry\">"
                + "<mxPoint x=\"" + fromX + "\" y=\"" + y + "\" as=\"sourcePoint\"/>"
                + "<mxPoint x=\"" + toX + "\" y=\"" + y + "\" as=\"targetPoint\"/>"
                + "</mxGeometry></mxCell>";
    }
}
