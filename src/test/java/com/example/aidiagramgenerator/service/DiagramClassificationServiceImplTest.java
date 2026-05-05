package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.domain.ClassificationDecision;
import com.example.aidiagramgenerator.domain.ClassificationResponse;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the confidence-based {@link DiagramClassificationServiceImpl#classify(SemanticModel)}
 * method.
 *
 * <p>Tests are organised by classification layer / confidence tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagramClassificationService – confidence-based (SemanticModel)")
class DiagramClassificationServiceImplTest {

    @Mock
    private AiModelService aiModelService;

    private DiagramClassificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiagramClassificationServiceImpl(aiModelService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SemanticModel modelWith(List<EntityNode> entities, List<Relationship> rels, List<String> actions) {
        return new SemanticModel(entities, rels, actions);
    }

    private EntityNode entity(String name) {
        return new EntityNode(name);
    }

    private Relationship rel(String src, String tgt, String type) {
        return new Relationship(src, tgt, type);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Null / empty input guard
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Should throw when model is null")
        void shouldThrowOnNullModel() {
            assertThrows(IllegalArgumentException.class, () -> service.classify((SemanticModel) null));
        }

        @Test
        @DisplayName("Should return CLARIFY for completely empty model")
        void shouldClarifyForEmptyModel() {
            ClassificationResponse resp = service.classify(new SemanticModel());
            assertEquals(ClassificationDecision.CLARIFY, resp.getDecision());
            assertTrue(resp.getConfidence() < 40.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 1 – Explicit keyword signals (AUTO, confidence 90–100)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Explicit keyword signals → AUTO (90–100%)")
    class ExplicitSignals {

        @Test
        @DisplayName("Relationship type 'inherits' → CLASS, AUTO")
        void inheritanceRelationshipClassAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("Animal"), entity("Dog")),
                    List.of(rel("Dog", "Animal", "inherits")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.CLASS, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertTrue(resp.getConfidence() >= 90.0);
        }

        @Test
        @DisplayName("Relationship type 'extends' → CLASS, AUTO")
        void extendsRelationshipClassAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("Vehicle"), entity("Car")),
                    List.of(rel("Car", "Vehicle", "extends")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.CLASS, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
        }

        @Test
        @DisplayName("Action 'sends' → SEQUENCE, AUTO")
        void sendsActionSequenceAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("Client"), entity("Server")),
                    List.of(),
                    List.of("Client sends request to Server", "Server sends response"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.SEQUENCE, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertTrue(resp.getConfidence() >= 90.0);
        }

        @Test
        @DisplayName("Action 'calls' + 'returns' → SEQUENCE, AUTO, confidence 100")
        void multipleSequenceActionsMaxConfidence() {
            SemanticModel model = modelWith(
                    List.of(entity("UserService"), entity("AuthService")),
                    List.of(rel("UserService", "AuthService", "calls")),
                    List.of("UserService calls AuthService", "AuthService returns token"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.SEQUENCE, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertEquals(100.0, resp.getConfidence(), 0.01);
        }

        @Test
        @DisplayName("Entity named 'Actor' → USE_CASE, AUTO")
        void actorEntityUseCaseAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("Actor"), entity("OnlineBankingSystem")),
                    List.of(rel("Actor", "OnlineBankingSystem", "uses")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.USE_CASE, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
        }

        @Test
        @DisplayName("Relationship type 'one-to-many' → ER, AUTO")
        void oneToManyRelationshipErAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("Customer"), entity("Order")),
                    List.of(rel("Customer", "Order", "one-to-many")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.ER, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertTrue(resp.getConfidence() >= 90.0);
        }

        @Test
        @DisplayName("Entity named 'docker container' → DEPLOYMENT, AUTO")
        void dockerEntityDeploymentAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("docker container"), entity("kubernetes cluster")),
                    List.of(rel("app", "docker container", "runs")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.DEPLOYMENT, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertTrue(resp.getConfidence() >= 90.0);
        }

        @Test
        @DisplayName("Entity named 'api gateway' + rel 'port' → COMPONENT, AUTO")
        void apiGatewayComponentAuto() {
            SemanticModel model = modelWith(
                    List.of(entity("api gateway"), entity("user service")),
                    List.of(rel("api gateway", "user service", "port")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.COMPONENT, resp.getDiagramType());
            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 2 – Semantic pattern signals (SUGGEST, confidence 60–80)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Semantic pattern signals → SUGGEST (60–80%)")
    class SemanticSignals {

        @Test
        @DisplayName("Multiple structural words in actions → CLASS, SUGGEST or AUTO")
        void structuralWordsClassSuggest() {
            SemanticModel model = modelWith(
                    List.of(entity("Order")),
                    List.of(),
                    List.of("class with attribute", "abstract method", "encapsulation property"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.CLASS, resp.getDiagramType());
            assertTrue(resp.getConfidence() >= 60.0);
        }

        @Test
        @DisplayName("Database-related actions → ER, confidence ≥ 60")
        void databaseActionsErSuggest() {
            SemanticModel model = modelWith(
                    List.of(entity("user table"), entity("product")),
                    List.of(),
                    List.of("schema with column", "database record", "table row normalization"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.ER, resp.getDiagramType());
            assertTrue(resp.getConfidence() >= 60.0);
        }

        @Test
        @DisplayName("Infrastructure actions → DEPLOYMENT, confidence ≥ 60")
        void infrastructureActionsDeployment() {
            SemanticModel model = modelWith(
                    List.of(entity("application")),
                    List.of(),
                    List.of("deploy to infrastructure", "network subnet firewall", "replica pod namespace"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(DiagramType.DEPLOYMENT, resp.getDiagramType());
            assertTrue(resp.getConfidence() >= 60.0);
        }

        @Test
        @DisplayName("Semantic SUGGEST decision has non-empty message")
        void suggestDecisionHasMessage() {
            SemanticModel model = modelWith(
                    List.of(entity("schema")),
                    List.of(),
                    List.of("database column record table"));

            ClassificationResponse resp = service.classify(model);

            assertNotNull(resp.getMessage());
            assertFalse(resp.getMessage().isBlank());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 3 – Weak keyword scoring (CLARIFY / SUGGEST, confidence 30–50)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Weak keyword signals → CLARIFY (30–50%)")
    class WeakSignals {

        @Test
        @DisplayName("Single generic entity with no relationships → CLARIFY")
        void singleEntityNoRelsClarify() {
            SemanticModel model = modelWith(
                    List.of(entity("System")),
                    List.of(),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertEquals(ClassificationDecision.CLARIFY, resp.getDecision());
            assertTrue(resp.getConfidence() < 40.0);
        }

        @Test
        @DisplayName("Confidence < 40 for model with no recognisable signals")
        void noSignalsConfidenceBelow40() {
            SemanticModel model = modelWith(
                    List.of(entity("Foo"), entity("Bar")),
                    List.of(rel("Foo", "Bar", "linked")),
                    List.of("processes stuff"));

            ClassificationResponse resp = service.classify(model);

            assertTrue(resp.getConfidence() < 40.0,
                    "Expected confidence < 40 but was " + resp.getConfidence());
        }

        @Test
        @DisplayName("CLARIFY message asks for more input")
        void clarifyMessageAskForMoreInput() {
            ClassificationResponse resp = service.classify(new SemanticModel());

            assertEquals(ClassificationDecision.CLARIFY, resp.getDecision());
            assertTrue(resp.getMessage().toLowerCase().contains("confidence")
                    || resp.getMessage().toLowerCase().contains("ambiguous")
                    || resp.getMessage().toLowerCase().contains("more"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Decision tier thresholds
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Decision tier thresholds")
    class DecisionTiers {

        @Test
        @DisplayName("confidence >= 70 → AUTO")
        void highConfidenceIsAuto() {
            // Strong explicit signal guarantees ≥ 90
            SemanticModel model = modelWith(
                    List.of(entity("Client"), entity("Server")),
                    List.of(rel("Client", "Server", "calls")),
                    List.of("Client calls Server", "Server returns response"));

            ClassificationResponse resp = service.classify(model);

            assertEquals(ClassificationDecision.AUTO, resp.getDecision());
            assertTrue(resp.getConfidence() >= 70.0);
        }

        @Test
        @DisplayName("AUTO response message mentions diagram type name")
        void autoMessageContainsDiagramType() {
            SemanticModel model = modelWith(
                    List.of(entity("Dog"), entity("Animal")),
                    List.of(rel("Dog", "Animal", "inherits")),
                    List.of());

            ClassificationResponse resp = service.classify(model);

            assertTrue(resp.getMessage().toLowerCase().contains(
                    resp.getDiagramType().getDisplayName().toLowerCase())
                    || resp.getMessage().toLowerCase().contains(
                    resp.getDiagramType().name().toLowerCase()),
                    "AUTO message should mention the diagram type");
        }

        @Test
        @DisplayName("SUGGEST message includes confidence percentage")
        void suggestMessageIncludesConfidence() {
            SemanticModel model = modelWith(
                    List.of(entity("schema")),
                    List.of(),
                    List.of("database column record table row"));

            ClassificationResponse resp = service.classify(model);

            if (resp.getDecision() == ClassificationDecision.SUGGEST) {
                assertTrue(resp.getMessage().contains("%"),
                        "SUGGEST message should include confidence %");
            }
        }

        @Test
        @DisplayName("Response confidence matches reported tier")
        void confidenceMatchesTier() {
            SemanticModel explicit = modelWith(
                    List.of(entity("Pod"), entity("k8s")),
                    List.of(rel("Pod", "k8s", "kubernetes")),
                    List.of());
            ClassificationResponse resp = service.classify(explicit);

            switch (resp.getDecision()) {
                case AUTO    -> assertTrue(resp.getConfidence() >= 70.0);
                case SUGGEST -> assertTrue(resp.getConfidence() >= 40.0 && resp.getConfidence() < 70.0);
                case CLARIFY -> assertTrue(resp.getConfidence() < 40.0);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ClassificationResponse contract (equals / toString)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ClassificationResponse contract")
    class ResponseContract {

        @Test
        @DisplayName("toString contains decision, type, and confidence")
        void toStringContainsKeyFields() {
            SemanticModel model = modelWith(
                    List.of(entity("Server")),
                    List.of(rel("App", "Server", "deploys")),
                    List.of("deploy to infrastructure"));

            ClassificationResponse resp = service.classify(model);
            String s = resp.toString();

            assertTrue(s.contains(resp.getDecision().name()), "toString missing decision");
            assertTrue(s.contains(resp.getDiagramType().name()), "toString missing diagramType");
            assertTrue(s.contains("%"), "toString missing confidence %");
        }

        @Test
        @DisplayName("Never returns a null diagramType")
        void neverNullDiagramType() {
            SemanticModel model = modelWith(
                    List.of(entity("X"), entity("Y")),
                    List.of(rel("X", "Y", "related")),
                    List.of("does something"));

            ClassificationResponse resp = service.classify(model);

            assertNotNull(resp.getDiagramType());
        }
    }
}
