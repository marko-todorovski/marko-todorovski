package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.*;
import com.example.aidiagramgenerator.domain.LayoutProfile.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlantUmlGenerationServiceImpl}.
 * Uses a fixed seed (42L) for deterministic output wherever randomisation is involved.
 */
class PlantUmlGenerationServiceImplTest {

    private PlantUmlGenerationServiceImpl service;

    private static final long SEED = 42L;

    @BeforeEach
    void setUp() {
        service = new PlantUmlGenerationServiceImpl();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SemanticModel modelWith(List<EntityNode> entities,
                                    List<Relationship> relationships,
                                    List<String> actions) {
        return new SemanticModel(entities, relationships, actions);
    }

    private StyleProfile styleFor(DiagramType type, String direction, String spacing) {
        return new StyleProfile(type, direction, "solid", spacing);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class Validation {

        @Test
        @DisplayName("Should throw when model is null")
        void shouldThrowOnNullModel() {
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");
            assertThrows(IllegalArgumentException.class,
                    () -> service.generate(null, style, SEED));
        }

        @Test
        @DisplayName("Should throw when style is null")
        void shouldThrowOnNullStyle() {
            SemanticModel model = modelWith(List.of(), List.of(), List.of());
            assertThrows(IllegalArgumentException.class,
                    () -> service.generate(model, null, SEED));
        }

        @Test
        @DisplayName("Should throw when layout profile is null")
        void shouldThrowOnNullLayout() {
            SemanticModel model = modelWith(List.of(), List.of(), List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");
            assertThrows(IllegalArgumentException.class,
                    () -> service.generate(model, style, (LayoutProfile) null));
        }
    }

    // ── Class Diagram ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Class diagram generation")
    class ClassDiagram {

        @Test
        @DisplayName("Should produce valid @startuml/@enduml wrapper")
        void shouldProduceValidWrapper() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.startsWith("@startuml"), "Should start with @startuml");
            assertTrue(uml.stripTrailing().endsWith("@enduml"), "Should end with @enduml");
        }

        @Test
        @DisplayName("Should include all entity names")
        void shouldIncludeEntityNames() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Order")),
                    List.of(new Relationship("User", "Order", "association")),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("class User"), "Should declare User class");
            assertTrue(uml.contains("class Order"), "Should declare Order class");
        }

        @Test
        @DisplayName("Should include entity attributes")
        void shouldIncludeAttributes() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User", List.of("name", "email"))),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("name"), "Should contain attribute 'name'");
            assertTrue(uml.contains("email"), "Should contain attribute 'email'");
        }

        @Test
        @DisplayName("Should deduplicate entities with same name")
        void shouldDeduplicateEntities() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("User"), new EntityNode("Order")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            // Count occurrences of "class User"
            int count = countOccurrences(uml, "class User");
            assertEquals(1, count, "User class should appear exactly once");
        }

        @Test
        @DisplayName("Should group related entities")
        void shouldGroupRelatedEntities() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Order"),
                            new EntityNode("Product"), new EntityNode("Category")),
                    List.of(new Relationship("User", "Order", "association"),
                            new Relationship("Product", "Category", "association")),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            // Should contain grouping keywords (the exact keyword varies based on layout)
            assertTrue(uml.contains("Group 1") || uml.contains("Group 2"),
                    "Should contain group labels for connected components");
        }

        @Test
        @DisplayName("Should include relationship arrows")
        void shouldIncludeRelationships() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A"), new EntityNode("B")),
                    List.of(new Relationship("A", "B", "inheritance")),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("A") && uml.contains("B"),
                    "Should reference both entities");
            assertTrue(uml.contains("inheritance"),
                    "Should label inheritance relationship");
        }
    }

    // ── Sequence Diagram ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sequence diagram generation")
    class SequenceDiagram {

        @Test
        @DisplayName("Should declare deduplicated participants")
        void shouldDeclareDedupedParticipants() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Client"), new EntityNode("Client"), new EntityNode("Server")),
                    List.of(new Relationship("Client", "Server", "calls")),
                    List.of("login"));
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "left-right", "compact");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("@startuml"));
            // Client is an actor-type word; declared without quotes as "actor Client"
            int count = countOccurrences(uml, "actor Client");
            assertEquals(1, count, "Client participant should be declared once as actor");
        }

        @Test
        @DisplayName("Should include message arrows between participants")
        void shouldIncludeMessageArrows() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A"), new EntityNode("B")),
                    List.of(new Relationship("A", "B", "sends")),
                    List.of("doWork"));
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("A") && uml.contains("B"),
                    "Should reference participants");
            assertTrue(uml.contains("doWork()"),
                    "Should use action as message label");
        }

        @Test
        @DisplayName("Request message (sends) should use solid -> arrow")
        void requestMessageUsesSolidArrow() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("ATM")),
                    List.of(new Relationship("User", "ATM", "sends", "sendPin()", null)),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("User -> ATM : sendPin()"),
                    "Request should use solid '->' arrow");
            assertFalse(uml.contains("User ->> ATM"),
                    "Request should not use async '->>' arrow");
        }

        @Test
        @DisplayName("Return message (returns) should use dotted --> arrow")
        void returnMessageUsesDottedArrow() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("ATM")),
                    List.of(new Relationship("ATM", "User", "returns", "cashDispensed()", null)),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("ATM --> User : cashDispensed()"),
                    "Return should use dotted '-->' arrow");
        }

        @Test
        @DisplayName("Confirmation message should use dotted --> arrow")
        void confirmationMessageUsesDottedArrow() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Bank"), new EntityNode("ATM")),
                    List.of(new Relationship("Bank", "ATM", "confirms", "approved()", null)),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("Bank --> ATM : approved()"),
                    "Confirmation should use dotted '-->' arrow");
        }

        @Test
        @DisplayName("Mixed flow: request -> then confirmation -->")
        void mixedRequestAndConfirmationArrows() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("ATM"), new EntityNode("Bank")),
                    List.of(
                            new Relationship("User", "ATM", "sends", "sendPin()", null),
                            new Relationship("ATM", "Bank", "sends", "verifyPin()", null),
                            new Relationship("Bank", "ATM", "confirms", "pinValid()", null),
                            new Relationship("ATM", "User", "returns", "accessGranted()", null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("User -> ATM : sendPin()"), "User->ATM request should use ->");
            assertTrue(uml.contains("ATM -> Bank : verifyPin()"), "ATM->Bank request should use ->");
            assertTrue(uml.contains("Bank --> ATM : pinValid()"), "Bank->ATM confirmation should use -->");
            assertTrue(uml.contains("ATM --> User : accessGranted()"), "ATM->User return should use -->");
        }

        @Test
        @DisplayName("Should emit alt/else/end blocks from ALT fragment relationships")
        void shouldGenerateAltBlock() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("ATM"), new EntityNode("Bank"), new EntityNode("User")),
                    List.of(
                            new Relationship("__ALT__", "__START__", "alt_start", "amount > 1000", null),
                            new Relationship("ATM", "Bank", "sends", "requestConfirmation()", null),
                            new Relationship("__ALT__", "__ELSE__", "alt_else", "", null),
                            new Relationship("ATM", "User", "sends", "dispenseCash()", null),
                            new Relationship("__ALT__", "__END__", "alt_end", null, null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("alt amount > 1000"), "Should emit alt block with condition");
            assertTrue(uml.contains("else"), "Should emit else branch");
            assertTrue(uml.contains("end"), "Should close alt block with end keyword");
            assertTrue(uml.contains("requestConfirmation()"), "Should include if-branch message");
            assertTrue(uml.contains("dispenseCash()"), "Should include else-branch message");
        }

        @Test
        @DisplayName("Should emit alt/end without else when no else branch present")
        void shouldGenerateAltWithoutElse() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Server"), new EntityNode("User")),
                    List.of(
                            new Relationship("__ALT__", "__START__", "alt_start", "credentials valid", null),
                            new Relationship("Server", "User", "sends", "dashboardData()", null),
                            new Relationship("__ALT__", "__END__", "alt_end", null, null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("alt credentials valid"), "Should emit alt with condition");
            assertTrue(uml.contains("end"), "Should close alt block");
            assertFalse(uml.contains("else"), "Should not emit else when no else branch");
            assertTrue(uml.contains("dashboardData()"), "Should include if-branch message");
        }

        @Test
        @DisplayName("Should not declare __ALT__ as a participant")
        void altMarkersNotDeclaredAsParticipants() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("ATM"), new EntityNode("Bank")),
                    List.of(
                            new Relationship("__ALT__", "__START__", "alt_start", "balance > 0", null),
                            new Relationship("ATM", "Bank", "sends", "check()", null),
                            new Relationship("__ALT__", "__END__", "alt_end", null, null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertFalse(uml.contains("participant \"__ALT__\""), "Should not declare __ALT__ as participant");
            assertFalse(uml.contains("participant \"__START__\""), "Should not declare __START__ as participant");
        }

        @Test
        @DisplayName("Should emit par/else/end blocks from PAR fragment relationships")
        void shouldGenerateParBlock() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("WebServer"), new EntityNode("SQLServer"), new EntityNode("TS")),
                    List.of(
                            new Relationship("__PAR__", "__START__", "par_start", null, null),
                            new Relationship("WebServer", "SQLServer", "sends", "sendData()", null),
                            new Relationship("__PAR__", "__ELSE__", "par_else", null, null),
                            new Relationship("WebServer", "TS", "sends", "sendData()", null),
                            new Relationship("__PAR__", "__END__", "par_end", null, null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("par"), "Should emit par keyword");
            assertTrue(uml.contains("else"), "Should emit else to separate parallel branches");
            assertTrue(uml.contains("end"), "Should close par block with end");
            assertTrue(uml.contains("sendData()"), "Should include parallel branch messages");
        }

        @Test
        @DisplayName("Should not declare __PAR__ as a participant")
        void parMarkersNotDeclaredAsParticipants() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("WebServer"), new EntityNode("SQLServer")),
                    List.of(
                            new Relationship("__PAR__", "__START__", "par_start", null, null),
                            new Relationship("WebServer", "SQLServer", "sends", "sendData()", null),
                            new Relationship("__PAR__", "__END__", "par_end", null, null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertFalse(uml.contains("participant \"__PAR__\""), "Should not declare __PAR__ as participant");
            assertFalse(uml.contains("participant \"__START__\""), "Should not declare __START__ as participant");
        }

        @Test
        @DisplayName("Should declare single-word actor without quotes")
        void shouldDeclareActorWithoutQuotes() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Server")),
                    List.of(new Relationship("User", "Server", "sends", "login()", null)),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("actor User"), "User should be declared as 'actor User' (no quotes)");
            assertFalse(uml.contains("actor \"User\""), "Should not use quoted form for simple actor name");
        }

        @Test
        @DisplayName("Should declare multi-word participant with short alias")
        void shouldDeclareMultiWordParticipantWithShortAlias() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Web Server"), new EntityNode("SQL Server")),
                    List.of(
                            new Relationship("User", "Web Server", "sends", "request()", null),
                            new Relationship("Web Server", "SQL Server", "sends", "query()", null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("participant \"Web Server\" as WS"),
                    "Web Server should be declared with short alias WS");
            assertTrue(uml.contains("participant \"SQL Server\" as SS"),
                    "SQL Server should be declared with short alias SS");
        }

        @Test
        @DisplayName("Should use short alias in arrows for multi-word participants")
        void shouldUseShortAliasInArrows() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Web Server"), new EntityNode("SQL Server")),
                    List.of(
                            new Relationship("User", "Web Server", "sends", "request()", null),
                            new Relationship("Web Server", "SQL Server", "sends", "query()", null),
                            new Relationship("SQL Server", "Web Server", "returns", "result()", null)
                    ),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("WS"), "Web Server alias WS should appear in output");
            assertTrue(uml.contains("SS"), "SQL Server alias SS should appear in output");
            assertFalse(uml.contains("Web_Server"), "Underscore alias should not appear when short alias exists");
            assertFalse(uml.contains("SQL_Server"), "Underscore alias should not appear when short alias exists");
        }

        @Test
        @DisplayName("Transaction Server should be declared with alias TS")
        void shouldDeclareTransactionServerWithAlias() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Transaction Server")),
                    List.of(new Relationship("User", "Transaction Server", "sends", "pay()", null)),
                    List.of());
            StyleProfile style = styleFor(DiagramType.SEQUENCE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("participant \"Transaction Server\" as TS"),
                    "Transaction Server should be declared with alias TS");
        }
    }

    // ── ER Diagram ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ER diagram generation")
    class ErDiagram {

        @Test
        @DisplayName("Should produce entity blocks with PK")
        void shouldProduceEntityBlocksWithPK() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Customer", List.of("id", "name", "email"))),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.ER, "top-down", "expanded");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("entity \"Customer\""), "Should declare entity");
            assertTrue(uml.contains("* id : PK"), "First attribute should be PK");
        }

        @Test
        @DisplayName("Should deduplicate ER entities")
        void shouldDeduplicateErEntities() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Order"), new EntityNode("Order")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.ER, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            int count = countOccurrences(uml, "entity \"Order\"");
            assertEquals(1, count, "Order entity should appear once");
        }

        @Test
        @DisplayName("Should include cardinality notation for relationships")
        void shouldIncludeCardinality() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Order")),
                    List.of(new Relationship("User", "Order", "composition")),
                    List.of());
            StyleProfile style = styleFor(DiagramType.ER, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("||--o{"), "Should contain composition cardinality");
        }

        @Test
        @DisplayName("Should group related ER entities")
        void shouldGroupRelatedErEntities() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User"), new EntityNode("Order"),
                            new EntityNode("Product"), new EntityNode("Review")),
                    List.of(new Relationship("User", "Order", "association"),
                            new Relationship("Product", "Review", "association")),
                    List.of());
            StyleProfile style = styleFor(DiagramType.ER, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("Group 1") || uml.contains("Group 2"),
                    "Should contain group labels");
        }
    }

    // ── Use Case Diagram ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("UseCase diagram generation")
    class UseCaseDiagram {

        @Test
        @DisplayName("Should declare actor and use cases")
        void shouldDeclareActorAndUseCases() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Admin"), new EntityNode("ManageUsers")),
                    List.of(),
                    List.of("create", "delete"));
            StyleProfile style = styleFor(DiagramType.USE_CASE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("actor Admin"), "Should declare actor");
            assertTrue(uml.contains("(ManageUsers)"),
                    "Should declare use case from entity");
            assertTrue(uml.contains("(Create)") || uml.contains("(Delete)"),
                    "Should declare action-derived use cases");
        }

        @Test
        @DisplayName("Should deduplicate use case actions")
        void shouldDeduplicateActions() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User")),
                    List.of(),
                    List.of("login", "login", "register"));
            StyleProfile style = styleFor(DiagramType.USE_CASE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            // "  (Login)" (indented inside rectangle) should appear exactly once — the declaration
            int declarations = countOccurrences(uml, "  (Login)");
            assertEquals(1, declarations,
                    "login use case declaration should appear exactly once");
        }

        @Test
        @DisplayName("Should render include and extend dependencies")
        void shouldRenderIncludeAndExtendDependencies() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("Student")),
                    List.of(
                            new Relationship("download materials", "login", "include"),
                            new Relationship("view grades", "send notification", "extend")),
                    List.of("download materials", "login", "view grades", "send notification"));
            StyleProfile style = styleFor(DiagramType.USE_CASE, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("(Download Materials) ..> (Login) : <<include>>"),
                    "Should render include dependency with PlantUML use-case syntax");
            assertTrue(uml.contains("(View Grades) ..> (Send Notification) : <<extend>>"),
                    "Should render extend dependency with PlantUML use-case syntax");
        }
    }

    // ── Layout Direction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Layout direction from StyleProfile")
    class LayoutDirection {

        @Test
        @DisplayName("top-down should produce 'top to bottom direction'")
        void shouldRespectTopDown() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("top to bottom direction"),
                    "Should contain top-to-bottom directive");
        }

        @Test
        @DisplayName("left-right should produce 'left to right direction'")
        void shouldRespectLeftRight() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "left-right", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("left to right direction"),
                    "Should contain left-to-right directive");
        }
    }

    // ── Spacing Rules ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Spacing rules from StyleProfile")
    class SpacingRules {

        @Test
        @DisplayName("compact spacing should use small nodesep/ranksep")
        void shouldApplyCompactSpacing() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "compact");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("skinparam nodesep 25"),
                    "Compact should set nodesep 25");
            assertTrue(uml.contains("skinparam ranksep 25"),
                    "Compact should set ranksep 25");
        }

        @Test
        @DisplayName("expanded spacing should use large nodesep/ranksep")
        void shouldApplyExpandedSpacing() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "expanded");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("skinparam nodesep 80"),
                    "Expanded should set nodesep 80");
            assertTrue(uml.contains("skinparam ranksep 80"),
                    "Expanded should set ranksep 80");
        }

        @Test
        @DisplayName("normal spacing should use moderate nodesep/ranksep")
        void shouldApplyNormalSpacing() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("A")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml = service.generate(model, style, SEED);

            assertTrue(uml.contains("skinparam nodesep 50"),
                    "Normal should set nodesep 50");
            assertTrue(uml.contains("skinparam ranksep 50"),
                    "Normal should set ranksep 50");
        }
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deterministic output with seed")
    class Determinism {

        @Test
        @DisplayName("Same seed should produce identical output")
        void sameSeedSameOutput() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("User", List.of("name")),
                            new EntityNode("Order", List.of("total"))),
                    List.of(new Relationship("User", "Order", "association")),
                    List.of("purchase"));
            StyleProfile style = styleFor(DiagramType.CLASS, "top-down", "normal");

            String uml1 = service.generate(model, style, SEED);
            String uml2 = service.generate(model, style, SEED);

            assertEquals(uml1, uml2, "Same seed must produce identical PlantUML");
        }
    }

    // ── Explicit LayoutProfile ────────────────────────────────────────────────

    @Nested
    @DisplayName("Explicit LayoutProfile overload")
    class ExplicitLayout {

        @Test
        @DisplayName("Should use the supplied LayoutProfile direction and spacing")
        void shouldUseExplicitLayout() {
            SemanticModel model = modelWith(
                    List.of(new EntityNode("X")),
                    List.of(),
                    List.of());
            StyleProfile style = styleFor(DiagramType.CLASS, "left-right", "compact");

            LayoutProfile layout = LayoutProfile.builder()
                    .direction(Direction.LEFT_TO_RIGHT)
                    .nodeSpacing(100)
                    .rankSpacing(100)
                    .arrowStyle(ArrowStyle.SOLID)
                    .groupingStyle(GroupingStyle.PACKAGE)
                    .notePosition(NotePosition.TOP)
                    .build();

            String uml = service.generate(model, style, layout);

            assertTrue(uml.contains("left to right direction"),
                    "Should use explicit LR direction");
            assertTrue(uml.contains("skinparam nodesep 100"),
                    "Should use explicit node spacing");
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
