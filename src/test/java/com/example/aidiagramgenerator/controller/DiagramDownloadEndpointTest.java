package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.service.DiagramAnalyticsService;
import com.example.aidiagramgenerator.service.DiagramGenerationService;
import com.example.aidiagramgenerator.service.MermaidRenderer;
import com.example.aidiagramgenerator.service.PdfExtractionService;
import com.example.aidiagramgenerator.service.export.DrawIoExportService;
import com.example.aidiagramgenerator.repository.DiagramEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the diagram download endpoint.
 */
@ExtendWith(MockitoExtension.class)
class DiagramDownloadEndpointTest {

    @Mock
    private DiagramGenerationService diagramGenerationService;

    @Mock
    private DiagramRepository diagramRepository;

    @Mock
    private DiagramEvaluationRepository diagramEvaluationRepository;

    @Mock
    private DiagramAnalyticsService diagramAnalyticsService;

    @Mock
    private MermaidRenderer mermaidRenderer;

    @Mock
    private RestClient restClient;

    @Mock
    private DrawIoExportService drawIoExportService;

    @Mock
    private PdfExtractionService pdfExtractionService;

    private DiagramController controller;

    @BeforeEach
    void setUp() {
        controller = new DiagramController(
                diagramGenerationService,
                diagramRepository,
                diagramEvaluationRepository,
                diagramAnalyticsService,
                mermaidRenderer,
                restClient,
                drawIoExportService,
                pdfExtractionService);
    }

    @Test
    @DisplayName("generateAndDownload should return Draw.io XML file with correct headers")
    void shouldReturnDrawIoXmlFileWithCorrectHeaders() {
        // Arrange
        TextDiagramRequest request = new TextDiagramRequest(
                "User logs in to the system and accesses database",
                DiagramType.SEQUENCE);

        DiagramGenerationService.DiagramResult result = new DiagramGenerationService.DiagramResult(
                DiagramType.SEQUENCE,
                "sequenceDiagram\n    participant User\n    User->>System: login",
                "Generated sequence diagram",
                List.of("user", "login"),
                List.of("SEQUENCE_KEYWORD_MATCH"));

        String expectedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mxfile><diagram></diagram></mxfile>";

        when(diagramGenerationService.generateFromText(any(), any())).thenReturn(result);
        when(drawIoExportService.convertToDrawIoXml(any(Diagram.class))).thenReturn(expectedXml);

        // Act
        ResponseEntity<byte[]> response = controller.generateAndDownload(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/xml", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"diagram.drawio\"", 
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertNotNull(response.getBody());
        assertTrue(new String(response.getBody()).contains("<mxfile>"));
    }

    @Test
    @DisplayName("generateAndDownload should auto-detect diagram type when not specified")
    void shouldAutoDetectDiagramType() {
        // Arrange
        TextDiagramRequest request = new TextDiagramRequest(
                "The system has users, services, and databases", null);

        DiagramGenerationService.DiagramResult result = new DiagramGenerationService.DiagramResult(
                DiagramType.CLASS,
                "classDiagram\n    class User",
                "Auto-detected CLASS diagram",
                List.of("user", "service", "database"),
                List.of("DEFAULT_FALLBACK"));

        when(diagramGenerationService.generateFromText(any(), any())).thenReturn(result);
        when(drawIoExportService.convertToDrawIoXml(any(Diagram.class))).thenReturn(
                "<?xml version=\"1.0\"?><mxfile></mxfile>");

        // Act
        ResponseEntity<byte[]> response = controller.generateAndDownload(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("generateAndDownload should return valid XML content")
    void shouldReturnValidXmlContent() {
        // Arrange
        TextDiagramRequest request = new TextDiagramRequest(
                "Create a class diagram with User and Service", DiagramType.CLASS);

        DiagramGenerationService.DiagramResult result = new DiagramGenerationService.DiagramResult(
                DiagramType.CLASS,
                "classDiagram\n    class User\n    class Service\n    User --> Service",
                "Class diagram generated",
                List.of("user", "service"),
                List.of("NODE_USER_DETECTED", "NODE_SERVICE_DETECTED"));

        String expectedXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <mxfile host="app.diagrams.net">
                  <diagram id="test" name="Page-1">
                    <mxGraphModel>
                      <root>
                        <mxCell id="0" />
                        <mxCell id="1" parent="0" />
                      </root>
                    </mxGraphModel>
                  </diagram>
                </mxfile>
                """;

        when(diagramGenerationService.generateFromText(any(), any())).thenReturn(result);
        when(drawIoExportService.convertToDrawIoXml(any(Diagram.class))).thenReturn(expectedXml);

        // Act
        ResponseEntity<byte[]> response = controller.generateAndDownload(request);

        // Assert
        String xmlContent = new String(response.getBody());
        assertTrue(xmlContent.contains("<?xml"));
        assertTrue(xmlContent.contains("<mxfile"));
        assertTrue(xmlContent.contains("<diagram"));
        assertTrue(xmlContent.contains("<mxGraphModel"));
    }

    @Test
    @DisplayName("InputType detection should identify input types correctly")
    void shouldDetectInputTypesCorrectly() {
        // Test natural language detection
        assertEquals(InputType.NATURAL_LANGUAGE, InputType.detect("Create a class diagram"));
        assertEquals(InputType.NATURAL_LANGUAGE, InputType.detect("User sends message to Server"));
        
        // Test URL detection
        assertEquals(InputType.URL, InputType.detect("https://github.com/example/repo"));
        assertEquals(InputType.URL, InputType.detect("http://example.com/diagram"));
        
        // Test XML detection
        assertEquals(InputType.XML, InputType.detect("<diagram><node name='User'/></diagram>"));
        assertEquals(InputType.XML, InputType.detect("<?xml version='1.0'?><root/>"));
        
        // Test null/blank handling
        assertEquals(InputType.NATURAL_LANGUAGE, InputType.detect(null));
        assertEquals(InputType.NATURAL_LANGUAGE, InputType.detect(""));
        assertEquals(InputType.NATURAL_LANGUAGE, InputType.detect("   "));
    }
}
