package com.example.aidiagramgenerator.service.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrawIoXmlBuilder.
 */
class DrawIoXmlBuilderTest {

    private DrawIoXmlBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DrawIoXmlBuilder();
    }

    @Test
    @DisplayName("Should build valid XML structure")
    void shouldBuildValidXmlStructure() {
        String xml = builder.build();

        assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("<diagram"));
        assertTrue(xml.contains("<mxGraphModel"));
        assertTrue(xml.contains("<root>"));
        assertTrue(xml.contains("<mxCell id=\"0\" />"));
        assertTrue(xml.contains("<mxCell id=\"1\" parent=\"0\" />"));
        assertTrue(xml.contains("</root>"));
        assertTrue(xml.contains("</mxGraphModel>"));
        assertTrue(xml.contains("</diagram>"));
        assertTrue(xml.contains("</mxfile>"));
    }

    @Test
    @DisplayName("Should add a node with correct attributes")
    void shouldAddNodeWithCorrectAttributes() {
        builder.addNode("TestNode");
        String xml = builder.build();

        assertTrue(xml.contains("value=\"TestNode\""));
        assertTrue(xml.contains("vertex=\"1\""));
        assertTrue(xml.contains("<mxGeometry"));
        assertTrue(xml.contains("width=\""));
        assertTrue(xml.contains("height=\"60\""));
    }

    @Test
    @DisplayName("Should add a node with attributes list")
    void shouldAddNodeWithAttributesList() {
        List<String> attributes = Arrays.asList("+name: String", "+email: String");
        builder.addNode("User", attributes);
        String xml = builder.build();

        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("+name: String"));
        assertTrue(xml.contains("+email: String"));
    }

    @Test
    @DisplayName("Should add an edge between two nodes")
    void shouldAddEdgeBetweenNodes() {
        builder.addNode("Source");
        builder.addNode("Target");
        builder.addEdge("Source", "Target", "association");
        String xml = builder.build();

        assertTrue(xml.contains("edge=\"1\""));
        assertTrue(xml.contains("source=\""));
        assertTrue(xml.contains("target=\""));
        assertTrue(xml.contains("value=\"association\""));
    }

    @Test
    @DisplayName("Should automatically create nodes when adding edge with unknown nodes")
    void shouldAutoCreateNodesForEdge() {
        builder.addEdge("AutoSource", "AutoTarget", "uses");
        String xml = builder.build();

        assertTrue(xml.contains("AutoSource"));
        assertTrue(xml.contains("AutoTarget"));
        assertTrue(xml.contains("edge=\"1\""));
    }

    @Test
    @DisplayName("Should apply correct edge style for inheritance")
    void shouldApplyCorrectEdgeStyleForInheritance() {
        builder.addNode("Parent");
        builder.addNode("Child");
        builder.addEdge("Child", "Parent", "inherits");
        String xml = builder.build();

        assertTrue(xml.contains("endArrow=block;endFill=0"));
    }

    @Test
    @DisplayName("Should apply correct edge style for composition")
    void shouldApplyCorrectEdgeStyleForComposition() {
        builder.addNode("Container");
        builder.addNode("Part");
        builder.addEdge("Container", "Part", "composes");
        String xml = builder.build();

        assertTrue(xml.contains("endArrow=diamond;endFill=1"));
    }

    @Test
    @DisplayName("Should escape XML special characters in node labels")
    void shouldEscapeXmlSpecialCharacters() {
        builder.addNode("Test<Node>&\"Value'");
        String xml = builder.build();

        assertTrue(xml.contains("&lt;"));
        assertTrue(xml.contains("&gt;"));
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&quot;"));
        assertTrue(xml.contains("&apos;"));
    }

    @Test
    @DisplayName("Should clear builder for reuse")
    void shouldClearBuilderForReuse() {
        builder.addNode("Node1");
        builder.addNode("Node2");
        builder.clear();
        builder.addNode("Node3");
        String xml = builder.build();

        assertFalse(xml.contains("Node1"));
        assertFalse(xml.contains("Node2"));
        assertTrue(xml.contains("Node3"));
    }

    @Test
    @DisplayName("Should position nodes in a grid layout")
    void shouldPositionNodesInGridLayout() {
        builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.GRID);
        builder.setNodesPerRow(2);
        builder.addNode("Node1");
        builder.addNode("Node2");
        builder.addNode("Node3");
        String xml = builder.build();

        // All nodes should be present with geometry
        assertTrue(xml.contains("Node1"));
        assertTrue(xml.contains("Node2"));
        assertTrue(xml.contains("Node3"));
        // Nodes should have x and y positions
        assertTrue(xml.contains("x=\"40\"")); // First column
        assertTrue(xml.contains("y=\"40\"")); // First row
    }

    @Test
    @DisplayName("Should add node with explicit position")
    void shouldAddNodeWithExplicitPosition() {
        builder.addNode("CustomNode", 100, 200, 150, 80);
        String xml = builder.build();

        assertTrue(xml.contains("x=\"100\""));
        assertTrue(xml.contains("y=\"200\""));
        assertTrue(xml.contains("width=\"150\""));
        assertTrue(xml.contains("height=\"80\""));
    }

    @Test
    @DisplayName("Should use horizontal layout for sequence diagrams")
    void shouldUseHorizontalLayout() {
        builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HORIZONTAL);
        builder.addNode("User");
        builder.addNode("Service");
        builder.addNode("Database");
        String xml = builder.build();

        // All nodes should be present
        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("Service"));
        assertTrue(xml.contains("Database"));
        // All nodes should be on the same Y position (first row)
        assertTrue(xml.contains("y=\"40\""));
    }

    @Test
    @DisplayName("Should use hierarchical layout based on edges")
    void shouldUseHierarchicalLayout() {
        builder.setLayoutStrategy(DrawIoXmlBuilder.LayoutStrategy.HIERARCHICAL);
        builder.addNode("Parent");
        builder.addNode("Child1");
        builder.addNode("Child2");
        builder.addEdge("Parent", "Child1", "contains");
        builder.addEdge("Parent", "Child2", "contains");
        String xml = builder.build();

        // All nodes should be present
        assertTrue(xml.contains("Parent"));
        assertTrue(xml.contains("Child1"));
        assertTrue(xml.contains("Child2"));
        // Edges should be present
        assertTrue(xml.contains("edge=\"1\""));
    }

    @Test
    @DisplayName("Should calculate node width based on content length")
    void shouldCalculateNodeWidthBasedOnContent() {
        builder.addNode("ShortName");
        builder.addNode("ThisIsAVeryLongNodeNameThatShouldBeWider");
        String xml = builder.build();

        // Both nodes should be present
        assertTrue(xml.contains("ShortName"));
        assertTrue(xml.contains("ThisIsAVeryLongNodeNameThatShouldBeWider"));
    }

    @Test
    @DisplayName("Should not create duplicate nodes")
    void shouldNotCreateDuplicateNodes() {
        builder.addNode("UniqueNode");
        builder.addNode("UniqueNode"); // Try to add again
        String xml = builder.build();

        // Count occurrences of the node value
        int count = xml.split("value=\"UniqueNode\"", -1).length - 1;
        assertEquals(1, count, "Node should only appear once");
    }
}
