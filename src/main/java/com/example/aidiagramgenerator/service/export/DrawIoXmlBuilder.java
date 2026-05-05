package com.example.aidiagramgenerator.service.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builder class for constructing Draw.io compatible XML diagrams.
 * Builds nodes (vertices) and edges with proper positioning and styling.
 * Auto-calculates node positions to prevent overlapping.
 */
public class DrawIoXmlBuilder {

    private static final int DEFAULT_NODE_WIDTH = 140;
    private static final int DEFAULT_NODE_HEIGHT = 60;
    private static final int HORIZONTAL_PADDING = 60;
    private static final int VERTICAL_PADDING = 40;
    private static final int START_X = 40;
    private static final int START_Y = 40;
    private static final int ATTRIBUTE_LINE_HEIGHT = 18;

    private final List<NodeEntry> nodes = new ArrayList<>();
    private final List<EdgeEntry> edges = new ArrayList<>();
    private final Map<String, String> nodeIdMap = new HashMap<>();
    private final Map<String, NodeEntry> nodeByName = new HashMap<>();
    
    private int nodesPerRow = 4;
    private LayoutStrategy layoutStrategy = LayoutStrategy.GRID;
    private boolean positionsCalculated = false;

    public int getNodeCount() { return nodes.size(); }
    public int getEdgeCount() { return edges.size(); }

    /**
     * Layout strategies for node positioning.
     */
    public enum LayoutStrategy {
        /** Grid layout - nodes arranged in rows and columns */
        GRID,
        /** Horizontal layout - nodes arranged in a single row (good for sequence diagrams) */
        HORIZONTAL,
        /** Hierarchical layout - connected nodes arranged in levels */
        HIERARCHICAL
    }

    /**
     * Internal class representing a node entry.
     */
    private static class NodeEntry {
        final String id;
        final String name;
        final String label;
        int x;
        int y;
        int width;
        int height;
        final String style;
        final List<String> attributes;
        int level = -1; // For hierarchical layout
        boolean fixedPosition = false; // If true, skip auto-layout for this node

        NodeEntry(String id, String name, String label, int width, int height, String style, List<String> attributes) {
            this.id = id;
            this.name = name;
            this.label = label;
            this.x = 0;
            this.y = 0;
            this.width = width;
            this.height = height;
            this.style = style;
            this.attributes = attributes;
        }
    }

    /**
     * Internal class representing an edge entry.
     */
    private static class EdgeEntry {
        final String id;
        final String sourceName;
        final String sourceId;
        final String targetName;
        final String targetId;
        final String label;
        final String style;

        EdgeEntry(String id, String sourceName, String sourceId, String targetName, String targetId, String label, String style) {
            this.id = id;
            this.sourceName = sourceName;
            this.sourceId = sourceId;
            this.targetName = targetName;
            this.targetId = targetId;
            this.label = label;
            this.style = style;
        }
    }

    /**
     * Sets the layout strategy for node positioning.
     *
     * @param strategy the layout strategy to use
     * @return this builder
     */
    public DrawIoXmlBuilder setLayoutStrategy(LayoutStrategy strategy) {
        this.layoutStrategy = strategy;
        this.positionsCalculated = false;
        return this;
    }

    /**
     * Sets the number of nodes per row for grid layout.
     *
     * @param nodesPerRow number of nodes per row
     * @return this builder
     */
    public DrawIoXmlBuilder setNodesPerRow(int nodesPerRow) {
        this.nodesPerRow = nodesPerRow;
        this.positionsCalculated = false;
        return this;
    }

    /**
     * Adds a node (vertex) to the diagram.
     *
     * @param name the node label/name
     * @return this builder
     */
    public DrawIoXmlBuilder addNode(String name) {
        return addNode(name, new ArrayList<>());
    }

    /**
     * Adds a node (vertex) with attributes to the diagram.
     *
     * @param name       the node label/name
     * @param attributes list of attributes for the node
     * @return this builder
     */
    public DrawIoXmlBuilder addNode(String name, List<String> attributes) {
        if (nodeIdMap.containsKey(name)) {
            return this; // Node already exists
        }

        String id = generateId();
        nodeIdMap.put(name, id);

        // Calculate dimensions based on content
        int width = calculateNodeWidth(name, attributes);
        int height = calculateNodeHeight(attributes);

        String style = "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
        NodeEntry node = new NodeEntry(id, name, name, width, height, style, attributes);
        nodes.add(node);
        nodeByName.put(name, node);
        positionsCalculated = false;

        return this;
    }

    /**
     * Adds a node with specific position (bypasses auto-layout for this node).
     *
     * @param name   the node label/name
     * @param x      x position
     * @param y      y position
     * @param width  width of the node
     * @param height height of the node
     * @return this builder
     */
    public DrawIoXmlBuilder addNode(String name, int x, int y, int width, int height) {
        if (nodeIdMap.containsKey(name)) {
            return this;
        }

        String id = generateId();
        nodeIdMap.put(name, id);
        
        String style = "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";
        NodeEntry node = new NodeEntry(id, name, name, width, height, style, new ArrayList<>());
        node.x = x;
        node.y = y;
        node.fixedPosition = true; // Mark as fixed position
        nodes.add(node);
        nodeByName.put(name, node);
        
        return this;
    }

    /**
     * Calculates appropriate node width based on content.
     */
    private int calculateNodeWidth(String name, List<String> attributes) {
        int maxLength = name.length();
        if (attributes != null) {
            for (String attr : attributes) {
                maxLength = Math.max(maxLength, attr.length());
            }
        }
        // Approximate: 8 pixels per character, minimum width, with padding
        return Math.max(DEFAULT_NODE_WIDTH, Math.min(maxLength * 8 + 30, 250));
    }

    /**
     * Calculates appropriate node height based on content.
     */
    private int calculateNodeHeight(List<String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return DEFAULT_NODE_HEIGHT;
        }
        // Base height + line height per attribute
        return DEFAULT_NODE_HEIGHT + (attributes.size() * ATTRIBUTE_LINE_HEIGHT);
    }

    /**
     * Adds an edge (relationship) between two nodes.
     *
     * @param sourceName source node name
     * @param targetName target node name
     * @param label      edge label (relationship type)
     * @return this builder
     */
    public DrawIoXmlBuilder addEdge(String sourceName, String targetName, String label) {
        // If nodes don't exist yet, create them
        if (!nodeIdMap.containsKey(sourceName)) {
            addNode(sourceName);
        }
        if (!nodeIdMap.containsKey(targetName)) {
            addNode(targetName);
        }

        String sourceId = nodeIdMap.get(sourceName);
        String targetId = nodeIdMap.get(targetName);
        String id = generateId();
        String style = getEdgeStyle(label);
        edges.add(new EdgeEntry(id, sourceName, sourceId, targetName, targetId, label, style));
        positionsCalculated = false;

        return this;
    }

    /**
     * Calculates and applies positions to all nodes based on the selected layout strategy.
     */
    private void calculatePositions() {
        if (positionsCalculated || nodes.isEmpty()) {
            return;
        }

        switch (layoutStrategy) {
            case HORIZONTAL -> calculateHorizontalLayout();
            case HIERARCHICAL -> calculateHierarchicalLayout();
            default -> calculateGridLayout();
        }

        positionsCalculated = true;
    }

    /**
     * Calculates grid layout positions with proper spacing based on node dimensions.
     */
    private void calculateGridLayout() {
        // Filter nodes that need auto-layout
        List<NodeEntry> autoLayoutNodes = nodes.stream()
                .filter(n -> !n.fixedPosition)
                .toList();

        if (autoLayoutNodes.isEmpty()) {
            return;
        }

        // Group nodes into rows
        List<List<NodeEntry>> rows = new ArrayList<>();
        List<NodeEntry> currentRow = new ArrayList<>();
        
        for (int i = 0; i < autoLayoutNodes.size(); i++) {
            currentRow.add(autoLayoutNodes.get(i));
            if (currentRow.size() >= nodesPerRow || i == autoLayoutNodes.size() - 1) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }

        int currentY = START_Y;
        
        for (List<NodeEntry> row : rows) {
            // Find maximum height in this row
            int maxHeight = row.stream().mapToInt(n -> n.height).max().orElse(DEFAULT_NODE_HEIGHT);
            
            int currentX = START_X;
            
            // Position each node in the row
            for (NodeEntry node : row) {
                node.x = currentX;
                node.y = currentY + (maxHeight - node.height) / 2; // Center vertically within row
                currentX += node.width + HORIZONTAL_PADDING;
            }
            
            currentY += maxHeight + VERTICAL_PADDING;
        }
    }

    /**
     * Calculates horizontal layout - all nodes in a single row (good for sequence diagrams).
     */
    private void calculateHorizontalLayout() {
        // Filter nodes that need auto-layout
        List<NodeEntry> autoLayoutNodes = nodes.stream()
                .filter(n -> !n.fixedPosition)
                .toList();

        if (autoLayoutNodes.isEmpty()) {
            return;
        }

        int currentX = START_X;
        int maxHeight = autoLayoutNodes.stream().mapToInt(n -> n.height).max().orElse(DEFAULT_NODE_HEIGHT);
        
        for (NodeEntry node : autoLayoutNodes) {
            node.x = currentX;
            node.y = START_Y + (maxHeight - node.height) / 2; // Center vertically
            currentX += node.width + HORIZONTAL_PADDING;
        }
    }

    /**
     * Calculates hierarchical layout based on edge connections.
     * Source nodes are placed at the top, targets below.
     */
    private void calculateHierarchicalLayout() {
        // Filter nodes that need auto-layout
        List<NodeEntry> autoLayoutNodes = nodes.stream()
                .filter(n -> !n.fixedPosition)
                .toList();

        if (autoLayoutNodes.isEmpty()) {
            return;
        }

        if (edges.isEmpty()) {
            calculateGridLayout();
            return;
        }

        // Find root nodes (nodes that are only sources, never targets)
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (EdgeEntry edge : edges) {
            sources.add(edge.sourceName);
            targets.add(edge.targetName);
        }
        
        Set<String> rootNodes = new HashSet<>(sources);
        rootNodes.removeAll(targets);
        
        // If no clear roots, use all sources
        if (rootNodes.isEmpty()) {
            rootNodes.addAll(sources);
        }

        // Assign levels using BFS
        Map<String, Integer> nodeLevels = new HashMap<>();
        List<String> queue = new ArrayList<>(rootNodes);
        for (String root : rootNodes) {
            nodeLevels.put(root, 0);
        }
        
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            int currentLevel = nodeLevels.getOrDefault(current, 0);
            
            for (EdgeEntry edge : edges) {
                if (edge.sourceName.equals(current)) {
                    int newLevel = currentLevel + 1;
                    if (!nodeLevels.containsKey(edge.targetName) || nodeLevels.get(edge.targetName) < newLevel) {
                        nodeLevels.put(edge.targetName, newLevel);
                        if (!queue.contains(edge.targetName)) {
                            queue.add(edge.targetName);
                        }
                    }
                }
            }
        }

        // Handle unconnected nodes - only for auto-layout nodes
        for (NodeEntry node : autoLayoutNodes) {
            if (!nodeLevels.containsKey(node.name)) {
                nodeLevels.put(node.name, 0);
            }
            node.level = nodeLevels.get(node.name);
        }

        // Group nodes by level (only auto-layout nodes)
        Map<Integer, List<NodeEntry>> levelGroups = new HashMap<>();
        int maxLevel = 0;
        for (NodeEntry node : autoLayoutNodes) {
            levelGroups.computeIfAbsent(node.level, k -> new ArrayList<>()).add(node);
            maxLevel = Math.max(maxLevel, node.level);
        }

        // Position nodes level by level
        int currentY = START_Y;
        for (int level = 0; level <= maxLevel; level++) {
            List<NodeEntry> levelNodes = levelGroups.getOrDefault(level, new ArrayList<>());
            if (levelNodes.isEmpty()) continue;

            int maxHeight = levelNodes.stream().mapToInt(n -> n.height).max().orElse(DEFAULT_NODE_HEIGHT);
            int currentX = START_X;
            
            for (NodeEntry node : levelNodes) {
                node.x = currentX;
                node.y = currentY;
                currentX += node.width + HORIZONTAL_PADDING;
            }
            
            currentY += maxHeight + VERTICAL_PADDING + 20; // Extra padding between levels
        }
    }

    /**
     * Determines the edge style based on relationship type.
     */
    private String getEdgeStyle(String relationshipType) {
        if (relationshipType == null) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;";
        }
        
        String type = relationshipType.toLowerCase();
        if (type.contains("inherit") || type.contains("extends")) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=0;";
        } else if (type.contains("implement") || type.contains("realizes")) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=0;dashed=1;";
        } else if (type.contains("depend") || type.contains("uses")) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;dashed=1;";
        } else if (type.contains("compose") || type.contains("contains")) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=diamond;endFill=1;";
        } else if (type.contains("aggregate") || type.contains("has")) {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=diamond;endFill=0;";
        } else {
            return "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;";
        }
    }

    /**
     * Builds the final Draw.io XML string.
     *
     * @return Draw.io compatible XML string
     */
    public String build() {
        // Calculate positions before building XML
        calculatePositions();

        StringBuilder xml = new StringBuilder();
        
        // XML declaration and root elements
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mxfile host=\"app.diagrams.net\" modified=\"").append(java.time.Instant.now()).append("\" ");
        xml.append("agent=\"AI Diagram Generator\" version=\"21.0.0\" type=\"device\">\n");
        xml.append("  <diagram id=\"").append(UUID.randomUUID()).append("\" name=\"Page-1\">\n");
        xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" ");
        xml.append("guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" ");
        xml.append("page=\"1\" pageScale=\"1\" pageWidth=\"850\" pageHeight=\"1100\" math=\"0\" shadow=\"0\">\n");
        xml.append("      <root>\n");
        
        // Root cells (required by Draw.io)
        xml.append("        <mxCell id=\"0\" />\n");
        xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");
        
        // Build nodes
        for (NodeEntry node : nodes) {
            xml.append(buildNodeXml(node));
        }
        
        // Build edges
        for (EdgeEntry edge : edges) {
            xml.append(buildEdgeXml(edge));
        }
        
        // Close elements
        xml.append("      </root>\n");
        xml.append("    </mxGraphModel>\n");
        xml.append("  </diagram>\n");
        xml.append("</mxfile>\n");
        
        return xml.toString();
    }

    /**
     * Builds XML for a single node.
     */
    private String buildNodeXml(NodeEntry node) {
        StringBuilder xml = new StringBuilder();
        
        // Build the label with attributes if present
        String displayLabel = escapeXml(node.label);
        if (node.attributes != null && !node.attributes.isEmpty()) {
            StringBuilder labelBuilder = new StringBuilder();
            labelBuilder.append("<b>").append(escapeXml(node.label)).append("</b>");
            for (String attr : node.attributes) {
                labelBuilder.append("<br/>").append(escapeXml(attr));
            }
            displayLabel = labelBuilder.toString();
        }
        
        xml.append("        <mxCell id=\"").append(node.id).append("\" ");
        xml.append("value=\"").append(displayLabel).append("\" ");
        xml.append("style=\"").append(node.style).append("\" ");
        xml.append("vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"").append(node.x);
        xml.append("\" y=\"").append(node.y);
        xml.append("\" width=\"").append(node.width);
        xml.append("\" height=\"").append(node.height);
        xml.append("\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");
        
        return xml.toString();
    }

    /**
     * Builds XML for a single edge.
     */
    private String buildEdgeXml(EdgeEntry edge) {
        StringBuilder xml = new StringBuilder();
        
        xml.append("        <mxCell id=\"").append(edge.id).append("\" ");
        if (edge.label != null && !edge.label.isEmpty()) {
            xml.append("value=\"").append(escapeXml(edge.label)).append("\" ");
        }
        xml.append("style=\"").append(edge.style).append("\" ");
        xml.append("edge=\"1\" parent=\"1\" ");
        xml.append("source=\"").append(edge.sourceId).append("\" ");
        xml.append("target=\"").append(edge.targetId).append("\">\n");
        xml.append("          <mxGeometry relative=\"1\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");
        
        return xml.toString();
    }

    /**
     * Generates a unique ID for Draw.io elements.
     */
    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Escapes XML special characters.
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Clears the builder for reuse.
     *
     * @return this builder
     */
    public DrawIoXmlBuilder clear() {
        nodes.clear();
        edges.clear();
        nodeIdMap.clear();
        nodeByName.clear();
        positionsCalculated = false;
        return this;
    }
}
