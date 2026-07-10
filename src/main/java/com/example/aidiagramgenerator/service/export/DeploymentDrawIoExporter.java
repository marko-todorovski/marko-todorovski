package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML deployment diagram source into Draw.io XML.
 *
 * <p>Nodes render as 3D box shapes. Artifacts render as document shapes.
 * Communication links use plain edges.
 */
public class DeploymentDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(DeploymentDrawIoExporter.class);

    private static final int NODE_W  = 160;
    private static final int NODE_H  = 60;
    private static final int ART_W   = 120;
    private static final int ART_H   = 40;
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 60;
    private static final int COLS    = 3;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_NODE =
            "shape=mxgraph.cisco.servers.standard_server;html=1;"
                    + "fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_ARTIFACT =
            "shape=note;whiteSpace=wrap;html=1;"
                    + "fillColor=#fff2cc;strokeColor=#d6b656;";
    private static final String STYLE_DB =
            "shape=cylinder3;whiteSpace=wrap;html=1;"
                    + "fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_LINK =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;";
    private static final String STYLE_DEPLOY =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;dashed=1;";

    private static final Pattern P_NODE = Pattern.compile(
            "^\\s*node\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?(?:\\s*\\{[^}]*\\})?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_DB = Pattern.compile(
            "^\\s*database\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_ARTIFACT = Pattern.compile(
            "^\\s*artifact\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s*(-->|->|--)\\s*(\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private enum NodeType { NODE, DB, ARTIFACT }
    private record DeployNode(String id, String name, NodeType type) {}
    private record Link(String from, String to, String label) {}

    public String export(String plantUml) {
        log.info("[DeployDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, DeployNode> nodes = new LinkedHashMap<>();
        List<Link> links = new ArrayList<>();

        parse(P_NODE,     plantUml, nodes, NodeType.NODE);
        parse(P_DB,       plantUml, nodes, NodeType.DB);
        parse(P_ARTIFACT, plantUml, nodes, NodeType.ARTIFACT);

        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from  = rm.group(1).trim();
            String to    = rm.group(3).trim();
            String label = rm.group(4) != null ? rm.group(4).trim() : "";
            links.add(new Link(from, to, label));
            nodes.computeIfAbsent(from, k -> new DeployNode(DrawIoXml.uid(), k, NodeType.NODE));
            nodes.computeIfAbsent(to,   k -> new DeployNode(DrawIoXml.uid(), k, NodeType.NODE));
        }

        log.info("[DeployDrawIo] nodes={} links={}", nodes.size(), links.size());

        DrawIoXml xml = new DrawIoXml();
        List<DeployNode> list = new ArrayList<>(nodes.values());

        for (int i = 0; i < list.size(); i++) {
            DeployNode n = list.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x   = START_X + col * (NODE_W + H_GAP);
            int y   = START_Y + row * (NODE_H + V_GAP);
            String style = switch (n.type()) {
                case DB       -> STYLE_DB;
                case ARTIFACT -> STYLE_ARTIFACT;
                default       -> STYLE_NODE;
            };
            int w = n.type() == NodeType.ARTIFACT ? ART_W : NODE_W;
            int h = n.type() == NodeType.ARTIFACT ? ART_H : NODE_H;
            xml.addRaw(xml.rect(n.id(), n.name(), x, y, w, h, style));
        }

        for (Link lk : links) {
            DeployNode src = nodes.get(lk.from());
            DeployNode tgt = nodes.get(lk.to());
            if (src == null || tgt == null) continue;
            xml.addRaw(xml.edge(DrawIoXml.uid(), lk.label(), src.id(), tgt.id(), STYLE_LINK));
        }

        int cols = Math.min(list.size(), COLS);
        int rows = list.isEmpty() ? 1 : (list.size() + COLS - 1) / COLS;
        int totalW = START_X * 2 + cols * (NODE_W + H_GAP);
        int totalH = START_Y * 2 + rows * (NODE_H + V_GAP);

        log.info("[DeployDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    private void parse(Pattern p, String code, Map<String, DeployNode> out, NodeType type) {
        Matcher m = p.matcher(code);
        while (m.find()) {
            String name  = m.group(1).trim();
            String alias = m.group(2) != null ? m.group(2).trim() : name.replaceAll("[^\\w]", "_");
            out.put(alias, new DeployNode(DrawIoXml.uid(), name, type));
        }
    }
}
