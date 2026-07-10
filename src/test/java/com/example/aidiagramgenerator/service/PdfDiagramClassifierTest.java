package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link PdfDiagramClassifier}.
 *
 * <p>Verifies that each documentation PDF is classified into the correct diagram type
 * and that the priority ordering prevents misclassification for types with overlapping keywords.
 */
@DisplayName("PdfDiagramClassifier — documentation PDF classification")
class PdfDiagramClassifierTest {

    private PdfDiagramClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PdfDiagramClassifier();
    }

    // ── Null / blank guard ────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns null for null input")
    void returnsNullForNullInput() {
        assertNull(classifier.detect(null));
    }

    @Test
    @DisplayName("Returns null for blank input")
    void returnsNullForBlankInput() {
        assertNull(classifier.detect("   "));
    }

    // ── Documentation PDF classification ─────────────────────────────────────

    static Stream<Arguments> documentationPdfCases() {
        return Stream.of(
                Arguments.of(
                        "activity_diagram_documentation.pdf",
                        "Activity Diagram Documentation\n\n" +
                        "This document describes the activity diagram used to model workflow and " +
                        "process flows. It covers activities, decision points, forks, joins, " +
                        "and swimlanes. Activity diagrams are useful for modelling business processes.",
                        DiagramType.ACTIVITY
                ),
                Arguments.of(
                        "collaboration_diagram_documentation.pdf",
                        "Collaboration Diagram Documentation\n\n" +
                        "This document explains collaboration diagrams, which show object communication " +
                        "and message passing between objects. The collaboration logic is depicted with " +
                        "numbered messages and object links to illustrate how objects interact.",
                        DiagramType.COLLABORATION
                ),
                Arguments.of(
                        "component_diagram_documentation.pdf",
                        "Component Diagram Documentation\n\n" +
                        "This document covers component diagrams that represent software components " +
                        "and their interfaces. Components are the modular units of a system that " +
                        "expose well-defined interfaces for communication.",
                        DiagramType.COMPONENT
                ),
                Arguments.of(
                        "deployment_diagram_documentation.pdf",
                        "Deployment Diagram Documentation\n\n" +
                        "This document describes deployment diagrams showing the physical deployment " +
                        "of software onto nodes. It covers deployed artifacts, execution environments, " +
                        "and communication paths between nodes.",
                        DiagramType.DEPLOYMENT
                ),
                Arguments.of(
                        "er_diagram_documentation.pdf",
                        "ER Diagram Documentation\n\n" +
                        "This document explains entity-relationship diagrams. It covers entities, " +
                        "attributes, cardinality, primary key and foreign key constraints, and how " +
                        "relationships between entities are modelled in a relational database schema.",
                        DiagramType.ER
                ),
                Arguments.of(
                        "microservices_diagram_documentation.pdf",
                        "Microservices Architecture Documentation\n\n" +
                        "This document describes the microservices architecture. It covers the " +
                        "API Gateway pattern, service registry, message broker for asynchronous " +
                        "communication, and how independent services collaborate.",
                        DiagramType.MICROSERVICES
                ),
                Arguments.of(
                        "object_diagram_documentation.pdf",
                        "Object Diagram Documentation\n\n" +
                        "This document explains object diagrams, which show object instances at a " +
                        "specific point in time. It covers object snapshot views, attribute values " +
                        "assigned to objects, and links between object instances.",
                        DiagramType.OBJECT
                ),
                Arguments.of(
                        "sequence_diagram_documentation.pdf",
                        "Sequence Diagram Documentation\n\n" +
                        "This document describes sequence diagrams that illustrate how participants " +
                        "interact over time. It explains the interaction flow, lifelines, activation " +
                        "bars, and messages exchanged between participants.",
                        DiagramType.SEQUENCE
                ),
                Arguments.of(
                        "state_diagram_documentation.pdf",
                        "State Diagram Documentation\n\n" +
                        "This document covers state diagrams used to model state behavior of a system. " +
                        "It describes states, transitions triggered by events, guard conditions, " +
                        "entry and exit actions, and composite states.",
                        DiagramType.STATE
                ),
                Arguments.of(
                        "use_case_diagram_documentation.pdf",
                        "Use Case Diagram Documentation\n\n" +
                        "This document describes use case diagrams that capture system functionality " +
                        "from the perspective of actors. It covers use cases, actors, system boundary, " +
                        "include and extend relationships.",
                        DiagramType.USE_CASE
                ),
                Arguments.of(
                        "class_diagram_documentation.pdf",
                        "Class Diagram Documentation\n\n" +
                        "This document explains class diagrams used to model the static structure " +
                        "of a system. It covers classes, attributes and methods, inheritance, " +
                        "associations, aggregations, and compositions between classes.",
                        DiagramType.CLASS
                )
        );
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource("documentationPdfCases")
    @DisplayName("Documentation PDF classified correctly")
    void documentationPdfsClassifiedCorrectly(String filename, String text, DiagramType expected) {
        assertEquals(expected, classifier.detect(text),
                "Expected " + filename + " to be classified as " + expected);
    }

    // ── Priority ordering: previously misclassified cases ────────────────────

    @Test
    @DisplayName("ACTIVITY is detected before USE_CASE when both keywords present")
    void activityBeforeUseCase() {
        String text = "Activity Diagram Documentation\n" +
                "This activity diagram models workflow with actors and use cases in a system.";
        assertEquals(DiagramType.ACTIVITY, classifier.detect(text));
    }

    @Test
    @DisplayName("STATE is detected before SEQUENCE when both keywords present")
    void stateBeforeSequence() {
        String text = "State Diagram Documentation\n" +
                "This diagram shows states and transitions, with participants exchanging messages.";
        assertEquals(DiagramType.STATE, classifier.detect(text));
    }

    @Test
    @DisplayName("OBJECT is detected before CLASS when both keywords present")
    void objectBeforeClass() {
        String text = "Object Diagram Documentation\n" +
                "Shows object instances with attribute values alongside class definitions.";
        assertEquals(DiagramType.OBJECT, classifier.detect(text));
    }

    @Test
    @DisplayName("COLLABORATION is detected before USE_CASE when both keywords present")
    void collaborationBeforeUseCase() {
        String text = "Collaboration Diagram Documentation\n" +
                "Depicts object communication with actors and use cases in the system boundary.";
        assertEquals(DiagramType.COLLABORATION, classifier.detect(text));
    }

    @Test
    @DisplayName("MICROSERVICES is detected before COMPONENT when both keywords present")
    void microservicesBeforeComponent() {
        String text = "Microservices Architecture Documentation\n" +
                "Describes microservices architecture with an API Gateway and components.";
        assertEquals(DiagramType.MICROSERVICES, classifier.detect(text));
    }

    @Test
    @DisplayName("Returns null when no PDF-specific keywords are present")
    void returnsNullWhenNoKeywordsMatch() {
        // Deliberately generic text with no diagram-specific vocabulary
        String text = "This is a general document with no specific diagram type keywords.";
        assertNull(classifier.detect(text));
    }

    // ── Title/header keyword matching ─────────────────────────────────────────

    @Test
    @DisplayName("Matches on exact title phrase: 'Activity Diagram Documentation'")
    void matchesActivityTitle() {
        assertEquals(DiagramType.ACTIVITY, classifier.detect("Activity Diagram Documentation"));
    }

    @Test
    @DisplayName("Matches on exact title phrase: 'Microservices Architecture Documentation'")
    void matchesMicroservicesTitle() {
        assertEquals(DiagramType.MICROSERVICES,
                classifier.detect("Microservices Architecture Documentation"));
    }

    @Test
    @DisplayName("Matches case-insensitively")
    void matchesCaseInsensitively() {
        assertEquals(DiagramType.STATE, classifier.detect("STATE DIAGRAM DOCUMENTATION"));
        assertEquals(DiagramType.OBJECT, classifier.detect("OBJECT DIAGRAM with ATTRIBUTE VALUES"));
    }
}
