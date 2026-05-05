package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.DiagramSuggestion;
import com.example.aidiagramgenerator.domain.DiagramSuggestion.ClassificationSource;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation;
import com.example.aidiagramgenerator.dto.response.DiagramExplanation.RelationshipInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiagramExplanationServiceImplTest {

    private DiagramExplanationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiagramExplanationServiceImpl();
    }

    private SemanticModel sampleModel() {
        List<EntityNode> entities = List.of(
                new EntityNode("User"),
                new EntityNode("Order"),
                new EntityNode("Product"));
        List<Relationship> relationships = List.of(
                new Relationship("User", "Order", "creates"),
                new Relationship("Order", "Product", "contains"));
        List<String> actions = List.of("creates", "contains", "ships");
        return new SemanticModel(entities, relationships, actions);
    }

    private DiagramSuggestion suggestion(DiagramType type, int confidence, String reasoning) {
        return new DiagramSuggestion(type, confidence, reasoning, ClassificationSource.KEYWORD_SCORING);
    }

    @Nested
    class EntityExtraction {

        @Test
        void extractsEntityNamesFromSemanticModel() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 85, sampleModel(),
                    suggestion(DiagramType.CLASS, 85, "Class diagram detected"));

            assertEquals(List.of("User", "Order", "Product"), result.getExtractedEntities());
        }

        @Test
        void returnsEmptyEntitiesWhenModelIsNull() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 100, null, null);

            assertNotNull(result.getExtractedEntities());
            assertTrue(result.getExtractedEntities().isEmpty());
        }
    }

    @Nested
    class RelationshipDetection {

        @Test
        void extractsRelationshipsFromSemanticModel() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 85, sampleModel(),
                    suggestion(DiagramType.CLASS, 85, "Detected"));

            List<RelationshipInfo> rels = result.getDetectedRelationships();
            assertEquals(2, rels.size());
            assertEquals("User", rels.get(0).source());
            assertEquals("Order", rels.get(0).target());
            assertEquals("creates", rels.get(0).type());
            assertEquals("Order", rels.get(1).source());
            assertEquals("Product", rels.get(1).target());
        }

        @Test
        void returnsEmptyRelationshipsWhenModelIsNull() {
            DiagramExplanation result = service.explain(
                    DiagramType.SEQUENCE, 50, null,
                    suggestion(DiagramType.SEQUENCE, 50, "Maybe sequence"));

            assertNotNull(result.getDetectedRelationships());
            assertTrue(result.getDetectedRelationships().isEmpty());
        }
    }

    @Nested
    class ActionDetection {

        @Test
        void extractsActionsFromSemanticModel() {
            DiagramExplanation result = service.explain(
                    DiagramType.SEQUENCE, 90, sampleModel(),
                    suggestion(DiagramType.SEQUENCE, 90, "Sequence detected"));

            assertEquals(List.of("creates", "contains", "ships"), result.getDetectedActions());
        }

        @Test
        void returnsEmptyActionsWhenModelIsNull() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 30, null,
                    suggestion(DiagramType.CLASS, 30, "Low confidence"));

            assertNotNull(result.getDetectedActions());
            assertTrue(result.getDetectedActions().isEmpty());
        }
    }

    @Nested
    class ConfidenceLevel {

        @Test
        void highConfidence() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 85, sampleModel(),
                    suggestion(DiagramType.CLASS, 85, "High"));

            assertEquals(85, result.getConfidenceScore());
            assertEquals("HIGH", result.getConfidenceLevel());
        }

        @Test
        void mediumConfidence() {
            DiagramExplanation result = service.explain(
                    DiagramType.SEQUENCE, 55, null,
                    suggestion(DiagramType.SEQUENCE, 55, "Medium"));

            assertEquals(55, result.getConfidenceScore());
            assertEquals("MEDIUM", result.getConfidenceLevel());
        }

        @Test
        void lowConfidence() {
            DiagramExplanation result = service.explain(
                    DiagramType.ER, 25, null,
                    suggestion(DiagramType.ER, 25, "Low"));

            assertEquals(25, result.getConfidenceScore());
            assertEquals("LOW", result.getConfidenceLevel());
        }

        @Test
        void boundaryAt70IsHigh() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 70, sampleModel(),
                    suggestion(DiagramType.CLASS, 70, "Boundary"));

            assertEquals("HIGH", result.getConfidenceLevel());
        }

        @Test
        void boundaryAt40IsMedium() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 40, null,
                    suggestion(DiagramType.CLASS, 40, "Boundary"));

            assertEquals("MEDIUM", result.getConfidenceLevel());
        }
    }

    @Nested
    class TypeReasoning {

        @Test
        void usesSuggestionReasoningWhenAvailable() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 85, sampleModel(),
                    suggestion(DiagramType.CLASS, 85, "Input mentions class structure and inheritance"));

            assertEquals("Input mentions class structure and inheritance", result.getTypeReasoning());
        }

        @Test
        void generatesReasoningForExplicitType() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 100, sampleModel(), null);

            assertTrue(result.getTypeReasoning().contains("explicitly requested"));
        }

        @Test
        void generatesReasoningForClassDiagramWhenSuggestionEmpty() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 80, sampleModel(),
                    suggestion(DiagramType.CLASS, 80, ""));

            assertTrue(result.getTypeReasoning().contains("structural relationships"));
        }

        @Test
        void generatesReasoningForSequenceDiagram() {
            DiagramExplanation result = service.explain(
                    DiagramType.SEQUENCE, 80, sampleModel(),
                    suggestion(DiagramType.SEQUENCE, 80, null));

            assertTrue(result.getTypeReasoning().contains("interactions"));
        }

        @Test
        void generatesReasoningForErDiagram() {
            DiagramExplanation result = service.explain(
                    DiagramType.ER, 80, sampleModel(),
                    suggestion(DiagramType.ER, 80, "  "));

            assertTrue(result.getTypeReasoning().contains("data entities"));
        }

        @Test
        void generatesReasoningForUseCaseDiagram() {
            DiagramExplanation result = service.explain(
                    DiagramType.USE_CASE, 80, sampleModel(),
                    suggestion(DiagramType.USE_CASE, 80, null));

            assertTrue(result.getTypeReasoning().contains("user goals"));
        }

        @Test
        void generatesReasoningForComponentDiagram() {
            DiagramExplanation result = service.explain(
                    DiagramType.COMPONENT, 80, sampleModel(),
                    suggestion(DiagramType.COMPONENT, 80, null));

            assertTrue(result.getTypeReasoning().contains("modules"));
        }

        @Test
        void generatesReasoningForDeploymentDiagram() {
            DiagramExplanation result = service.explain(
                    DiagramType.DEPLOYMENT, 80, sampleModel(),
                    suggestion(DiagramType.DEPLOYMENT, 80, null));

            assertTrue(result.getTypeReasoning().contains("infrastructure"));
        }
    }

    @Nested
    class ClassificationSourceField {

        @Test
        void usesSuggestionSource() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 85, sampleModel(),
                    suggestion(DiagramType.CLASS, 85, "Reason"));

            assertEquals("KEYWORD_SCORING", result.getClassificationSource());
        }

        @Test
        void usesExplicitWhenNoSuggestion() {
            DiagramExplanation result = service.explain(
                    DiagramType.CLASS, 100, sampleModel(), null);

            assertEquals("EXPLICIT", result.getClassificationSource());
        }

        @Test
        void reflectsSemanticPatternSource() {
            DiagramSuggestion semanticSuggestion = new DiagramSuggestion(
                    DiagramType.SEQUENCE, 75, "Semantic patterns detected",
                    ClassificationSource.SEMANTIC_PATTERN);

            DiagramExplanation result = service.explain(
                    DiagramType.SEQUENCE, 75, sampleModel(), semanticSuggestion);

            assertEquals("SEMANTIC_PATTERN", result.getClassificationSource());
        }
    }
}
