package com.example.aidiagramgenerator.service.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts PlantUML microservices architecture diagram source into Draw.io XML.
 *
 * <p>Recognised service types and their shapes:
 * <ul>
 *   <li>API Gateway — hexagon</li>
 *   <li>Service / microservice — rounded rectangle (blue)</li>
 *   <li>Database — cylinder</li>
 *   <li>Message broker / queue — trapezoid</li>
 *   <li>Client / frontend — rectangle (green)</li>
 * </ul>
 *
 * Layout uses a hierarchical tier approach: client → gateway → services → DBs/brokers.
 */
public class MicroservicesDrawIoExporter {

    private static final Logger log = LoggerFactory.getLogger(MicroservicesDrawIoExporter.class);

    private static final int SVC_W   = 150;
    private static final int SVC_H   = 50;
    private static final int H_GAP   = 80;
    private static final int V_GAP   = 80;
    private static final int COLS    = 4;
    private static final int START_X = 40;
    private static final int START_Y = 40;

    private static final String STYLE_SERVICE =
            "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
    private static final String STYLE_GATEWAY =
            "shape=hexagon;perimeter=mxPerimeter.HexagonPerimeter2;whiteSpace=wrap;html=1;"
                    + "fillColor=#f8cecc;strokeColor=#b85450;";
    private static final String STYLE_DB =
            "shape=cylinder3;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_BROKER =
            "shape=mxgraph.cisco.routers.router;html=1;fillColor=#fff2cc;strokeColor=#d6b656;";
    private static final String STYLE_CLIENT =
            "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;";
    private static final String STYLE_EDGE =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;";
    private static final String STYLE_ASYNC =
            "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=open;endFill=0;dashed=1;";

    // Parse PlantUML rectangle/component/node with labels
    private static final Pattern P_ENTITY = Pattern.compile(
            "^\\s*(?:rectangle|component|node|actor|database|queue|collections)\\s+\"?([\\w][\\w\\s.-]*)\"?(?:\\s+as\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern P_REL = Pattern.compile(
            "^\\s*(\\w+)\\s*(-->|->|--)\\s*(\\w+)(?:\\s*:\\s*(.+))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    // Also handle [Label] syntax from flowcharts
    private static final Pattern P_BRACKET = Pattern.compile(
            "(\\w+)\\s*\\[([^\\]]+)\\]",
            Pattern.CASE_INSENSITIVE);

    private enum ServiceType { CLIENT, GATEWAY, SERVICE, DB, BROKER }

    private record Service(String id, String name, ServiceType type) {}
    private record Link(String from, String to, String label, boolean async) {}

    public String export(String plantUml) {
        log.info("[MicroservicesDrawIo] parsing PlantUML ({} chars)", plantUml.length());

        Map<String, Service> services = new LinkedHashMap<>();
        List<Link> links = new ArrayList<>();

        // Parse entity declarations
        Matcher em = P_ENTITY.matcher(plantUml);
        while (em.find()) {
            String name  = em.group(1).trim();
            String alias = em.group(2) != null ? em.group(2).trim() : name.replaceAll("[^\\w]", "_");
            services.put(alias, new Service(DrawIoXml.uid(), name, classifyService(name)));
        }

        // Parse relationships
        Matcher rm = P_REL.matcher(plantUml);
        while (rm.find()) {
            String from   = rm.group(1).trim();
            String arrow  = rm.group(2).trim();
            String to     = rm.group(3).trim();
            String label  = rm.group(4) != null ? rm.group(4).trim() : "";
            boolean async = arrow.equals("-->");
            links.add(new Link(from, to, label, async));
            services.computeIfAbsent(from, k -> new Service(DrawIoXml.uid(), k, classifyService(k)));
            services.computeIfAbsent(to,   k -> new Service(DrawIoXml.uid(), k, classifyService(k)));
        }

        log.info("[MicroservicesDrawIo] services={} links={}", services.size(), links.size());

        DrawIoXml xml = new DrawIoXml();

        // Tier-based layout: client (0), gateway (1), services (2), db/broker (3)
        Map<ServiceType, List<Service>> tiers = new EnumMap<>(ServiceType.class);
        for (ServiceType t : ServiceType.values()) tiers.put(t, new ArrayList<>());
        services.values().forEach(s -> tiers.get(s.type()).add(s));

        ServiceType[] order = {ServiceType.CLIENT, ServiceType.GATEWAY, ServiceType.SERVICE, ServiceType.DB, ServiceType.BROKER};
        int y = START_Y;
        for (ServiceType tier : order) {
            List<Service> row = tiers.get(tier);
            if (row.isEmpty()) continue;
            int totalRowW = row.size() * (SVC_W + H_GAP) - H_GAP;
            int rowStartX = Math.max(START_X, START_X + (COLS * (SVC_W + H_GAP) - totalRowW) / 2);
            int x = rowStartX;
            for (Service s : row) {
                String style = switch (s.type()) {
                    case GATEWAY -> STYLE_GATEWAY;
                    case DB      -> STYLE_DB;
                    case BROKER  -> STYLE_BROKER;
                    case CLIENT  -> STYLE_CLIENT;
                    default      -> STYLE_SERVICE;
                };
                xml.addRaw(xml.rect(s.id(), s.name(), x, y, SVC_W, SVC_H, style));
                x += SVC_W + H_GAP;
            }
            y += SVC_H + V_GAP;
        }

        for (Link lk : links) {
            Service src = services.get(lk.from());
            Service tgt = services.get(lk.to());
            if (src == null || tgt == null) continue;
            String style = lk.async() ? STYLE_ASYNC : STYLE_EDGE;
            xml.addRaw(xml.edge(DrawIoXml.uid(), lk.label(), src.id(), tgt.id(), style));
        }

        int totalW = START_X * 2 + Math.max(1, COLS) * (SVC_W + H_GAP);
        int totalH = y + 60;
        log.info("[MicroservicesDrawIo] generated {} elements, canvas {}x{}", xml.getElementCount(), totalW, totalH);
        return xml.build(totalW, totalH);
    }

    private ServiceType classifyService(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("gateway") || lower.contains("api gw")) return ServiceType.GATEWAY;
        if (lower.contains("db") || lower.contains("database") || lower.contains("store")
                || lower.contains("postgres") || lower.contains("mysql") || lower.contains("mongo")
                || lower.contains("redis") || lower.contains("cache")) return ServiceType.DB;
        if (lower.contains("broker") || lower.contains("kafka") || lower.contains("rabbit")
                || lower.contains("queue") || lower.contains("bus") || lower.contains("event"))
            return ServiceType.BROKER;
        if (lower.contains("client") || lower.contains("frontend") || lower.contains("ui")
                || lower.contains("browser") || lower.contains("user") || lower.contains("app"))
            return ServiceType.CLIENT;
        return ServiceType.SERVICE;
    }
}
