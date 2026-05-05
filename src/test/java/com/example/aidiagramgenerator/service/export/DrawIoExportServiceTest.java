package com.example.aidiagramgenerator.service.export;

import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DrawIoExportService.
 */
@ExtendWith(MockitoExtension.class)
class DrawIoExportServiceTest {

    @Mock
    private DiagramRepository diagramRepository;

    private DrawIoExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new DrawIoExportService(diagramRepository);
    }

    @Test
    @DisplayName("Should export sequence diagram to Draw.io XML")
    void shouldExportSequenceDiagramToDrawIoXml() {
        String mermaidCode = """
            sequenceDiagram
                participant User
                participant Service
                participant Database
                User->>Service: request
                Service-->>User: response
                Service->>Database: query
                Database-->>Service: result
            """;

        Diagram diagram = createDiagram(DiagramType.SEQUENCE, mermaidCode);

        String xml = exportService.convertToDrawIoXml(diagram);

        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("Service"));
        assertTrue(xml.contains("Database"));
        assertTrue(xml.contains("edge=\"1\""));
    }

    @Test
    @DisplayName("Should export class diagram to Draw.io XML")
    void shouldExportClassDiagramToDrawIoXml() {
        String mermaidCode = """
            classDiagram
                class User {
                    +String name
                    +String email
                }
                class Service {
                    +handleRequest()
                    +processData()
                }
                User --> Service
            """;

        Diagram diagram = createDiagram(DiagramType.CLASS, mermaidCode);

        String xml = exportService.convertToDrawIoXml(diagram);

        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("+String name"));
        assertTrue(xml.contains("Service"));
        assertTrue(xml.contains("+handleRequest()"));
    }

    @Test
    @DisplayName("Should export ER diagram to Draw.io XML")
    void shouldExportErDiagramToDrawIoXml() {
        String mermaidCode = """
            erDiagram
                USER {
                    string id
                    string name
                    string email
                }
                SERVICE {
                    string id
                    string endpoint
                }
                USER ||--o{ SERVICE : uses
            """;

        Diagram diagram = createDiagram(DiagramType.ER, mermaidCode);

        String xml = exportService.convertToDrawIoXml(diagram);

        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("USER"));
        assertTrue(xml.contains("SERVICE"));
    }

    @Test
    @DisplayName("Should export architecture diagram to Draw.io XML")
    void shouldExportArchitectureDiagramToDrawIoXml() {
        String mermaidCode = """
            graph TD
                User[User]
                Service[Service / API]
                Database[(Database)]
                User --> Service
                Service --> Database
            """;

        Diagram diagram = createDiagram(DiagramType.ARCHITECTURE, mermaidCode);

        String xml = exportService.convertToDrawIoXml(diagram);

        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("Service / API"));
        assertTrue(xml.contains("Database"));
    }

    @Test
    @DisplayName("Should export C4 diagram to Draw.io XML")
    void shouldExportC4DiagramToDrawIoXml() {
        String mermaidCode = """
            C4Context
                title System Context Diagram
                
                Person(user, "User", "End user")
                System(service, "Service", "Core API")
                SystemDb(db, "Database", "Data store")
                
                Rel(user, service, "Uses")
                Rel(service, db, "Reads/Writes")
            """;

        Diagram diagram = createDiagram(DiagramType.C4, mermaidCode);

        String xml = exportService.convertToDrawIoXml(diagram);

        assertTrue(xml.contains("<mxfile"));
        assertTrue(xml.contains("User"));
        assertTrue(xml.contains("Service"));
        assertTrue(xml.contains("Database"));
    }

    @Test
    @DisplayName("Should throw DiagramNotFoundException when diagram not found")
    void shouldThrowDiagramNotFoundExceptionWhenDiagramNotFound() {
        UUID id = UUID.randomUUID();
        when(diagramRepository.findById(id)).thenReturn(Optional.empty());

        DiagramNotFoundException exception = assertThrows(
                DiagramNotFoundException.class,
                () -> exportService.exportToDrawIoXml(id));

        assertTrue(exception.getMessage().contains(id.toString()));
    }

    @Test
    @DisplayName("Should export diagram when found by ID")
    void shouldExportDiagramWhenFoundById() {
        UUID id = UUID.randomUUID();
        String mermaidCode = "classDiagram\n    class Test";
        Diagram diagram = createDiagram(DiagramType.CLASS, mermaidCode);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));

        String xml = exportService.exportToDrawIoXml(id);

        assertNotNull(xml);
        assertTrue(xml.contains("<mxfile"));
    }

    @Test
    @DisplayName("Should generate correct filename")
    void shouldGenerateCorrectFilename() {
        Diagram diagram = createDiagram(DiagramType.CLASS, "classDiagram");
        
        String filename = exportService.generateFilename(diagram);

        assertTrue(filename.startsWith("diagram-"));
        assertTrue(filename.endsWith(".drawio"));
    }

    private Diagram createDiagram(DiagramType type, String mermaidCode) {
        Diagram diagram = new Diagram(InputType.TEXT, "test input", type, mermaidCode, "test explanation");
        // Set a mock ID using reflection since ID is generated
        try {
            var idField = Diagram.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(diagram, UUID.randomUUID());
        } catch (Exception e) {
            // Ignore if reflection fails
        }
        return diagram;
    }

    // ── SemanticModel export ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SemanticModel export")
    class SemanticModelExport {

        @Test
        @DisplayName("Should convert SemanticModel with entities and relationships to Draw.io XML")
        void shouldConvertSemanticModelWithEntitiesAndRelationships() {
            SemanticModel model = new SemanticModel(
                    List.of(
                            new EntityNode("User", List.of("name", "email")),
                            new EntityNode("Order", List.of("id", "total")),
                            new EntityNode("Product")),
                    List.of(
                            new Relationship("User", "Order", "places"),
                            new Relationship("Order", "Product", "contains")),
                    List.of());

            String xml = exportService.convertToDrawIoXml(model);

            assertNotNull(xml);
            assertTrue(xml.contains("<mxfile"), "XML should start with mxfile element");
            assertTrue(xml.contains("User"), "XML should contain User entity");
            assertTrue(xml.contains("Order"), "XML should contain Order entity");
            assertTrue(xml.contains("Product"), "XML should contain Product entity");
            assertTrue(xml.contains("edge=\"1\""), "XML should contain at least one edge");
            // Relationship labels should appear
            assertTrue(xml.contains("places") || xml.contains("contains"),
                    "XML should contain relationship labels");
        }

        @Test
        @DisplayName("Should include entity attributes in nodes")
        void shouldIncludeEntityAttributes() {
            SemanticModel model = new SemanticModel(
                    List.of(new EntityNode("Customer", List.of("id", "name", "email"))),
                    List.of(),
                    List.of());

            String xml = exportService.convertToDrawIoXml(model);

            assertTrue(xml.contains("Customer"), "XML should contain Customer entity");
            assertTrue(xml.contains("id") && xml.contains("name") && xml.contains("email"),
                    "XML should contain entity attributes");
        }

        @Test
        @DisplayName("Should handle SemanticModel with only actions (no relationships)")
        void shouldHandleSemanticModelWithOnlyActions() {
            SemanticModel model = new SemanticModel(
                    List.of(new EntityNode("User")),
                    List.of(),
                    List.of("login", "logout", "register"));

            String xml = exportService.convertToDrawIoXml(model);

            assertNotNull(xml);
            assertTrue(xml.contains("<mxfile"), "XML should contain mxfile");
            assertTrue(xml.contains("User"), "XML should contain User entity");
        }

        @Test
        @DisplayName("Should produce valid mxGraphModel structure for empty SemanticModel")
        void shouldHandleEmptySemanticModel() {
            SemanticModel model = new SemanticModel();

            String xml = exportService.convertToDrawIoXml(model);

            assertNotNull(xml);
            assertTrue(xml.contains("<mxfile"), "Empty model should still produce valid XML wrapper");
            assertTrue(xml.contains("<mxGraphModel"), "XML should contain mxGraphModel");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when SemanticModel is null")
        void shouldThrowOnNullSemanticModel() {
            assertThrows(IllegalArgumentException.class,
                    () -> exportService.convertToDrawIoXml((SemanticModel) null));
        }

        @Test
        @DisplayName("Should generate unique semantic model filename with .drawio extension")
        void shouldGenerateSemanticModelFilename() {
            String filename1 = exportService.generateFilenameForSemanticModel();
            String filename2 = exportService.generateFilenameForSemanticModel();

            assertNotNull(filename1);
            assertTrue(filename1.startsWith("semantic-model-"), "Filename should start with 'semantic-model-'");
            assertTrue(filename1.endsWith(".drawio"), "Filename should end with '.drawio'");
            assertNotEquals(filename1, filename2, "Each call should produce a unique filename");
        }
    }
}
