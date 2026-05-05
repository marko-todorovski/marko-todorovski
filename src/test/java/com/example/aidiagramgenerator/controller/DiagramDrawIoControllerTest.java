package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.export.DrawIoExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DiagramDrawIoController.
 */
@ExtendWith(MockitoExtension.class)
class DiagramDrawIoControllerTest {

    @Mock
    private DiagramRepository diagramRepository;

    @Mock
    private DomainDiagramRepository domainDiagramRepository;

    @Mock
    private DrawIoExportService drawIoExportService;

    private DiagramDrawIoController controller;

    private static final String SAMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<mxfile host=\"app.diagrams.net\">"
                    + "<diagram id=\"test\" name=\"Page-1\">"
                    + "<mxGraphModel><root>"
                    + "<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                    + "</root></mxGraphModel></diagram></mxfile>";

    @BeforeEach
    void setUp() {
        controller = new DiagramDrawIoController(diagramRepository, domainDiagramRepository, drawIoExportService);
    }

    // ── GET /api/diagram/{id}/drawio ──────────────────────────────────────────

    @Test
    @DisplayName("getDrawIoXml should return 200 with xml body when diagram exists")
    void shouldReturnXmlBodyWhenDiagramExists() {
        UUID id = UUID.randomUUID();
        Diagram diagram = buildDiagram(id);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(drawIoExportService.convertToDrawIoXml(diagram)).thenReturn(SAMPLE_XML);

        ResponseEntity<byte[]> response = controller.getDrawIoXml(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("<mxfile"));
    }

    @Test
    @DisplayName("getDrawIoXml should respond with application/xml content-type")
    void shouldRespondWithApplicationXmlContentType() {
        UUID id = UUID.randomUUID();
        Diagram diagram = buildDiagram(id);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(drawIoExportService.convertToDrawIoXml(diagram)).thenReturn(SAMPLE_XML);

        ResponseEntity<byte[]> response = controller.getDrawIoXml(id);

        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.isCompatibleWith(MediaType.APPLICATION_XML),
                "Content-Type should be compatible with application/xml");
    }

    @Test
    @DisplayName("getDrawIoXml should throw DiagramNotFoundException when diagram not found")
    void shouldThrow404WhenDiagramNotFound() {
        UUID id = UUID.randomUUID();
        when(diagramRepository.findById(id)).thenReturn(Optional.empty());
        when(domainDiagramRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DiagramNotFoundException.class, () -> controller.getDrawIoXml(id));
    }

    // ── GET /api/diagram/{id}/drawio/download ─────────────────────────────────

    @Test
    @DisplayName("downloadDrawIo should return byte[] body equal to the XML encoded as UTF-8")
    void shouldReturnByteArrayBodyForDownload() {
        UUID id = UUID.randomUUID();
        Diagram diagram = buildDiagram(id);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(drawIoExportService.convertToDrawIoXml(diagram)).thenReturn(SAMPLE_XML);
        when(drawIoExportService.generateFilename(diagram)).thenReturn("test-diagram.drawio");

        ResponseEntity<byte[]> response = controller.downloadDrawIo(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        byte[] body = response.getBody();
        assertNotNull(body);
        String decoded = new String(body, StandardCharsets.UTF_8);
        assertTrue(decoded.contains("<mxfile"), "Body should contain the Draw.io XML");
    }

    @Test
    @DisplayName("downloadDrawIo should set Content-Disposition to attachment")
    void shouldSetContentDispositionAttachment() {
        UUID id = UUID.randomUUID();
        Diagram diagram = buildDiagram(id);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(drawIoExportService.convertToDrawIoXml(diagram)).thenReturn(SAMPLE_XML);
        when(drawIoExportService.generateFilename(diagram)).thenReturn("my-diagram.drawio");

        ResponseEntity<byte[]> response = controller.downloadDrawIo(id);

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition, "Content-Disposition header should be set");
        assertTrue(disposition.startsWith("attachment"),
                "Content-Disposition should be 'attachment'");
        assertTrue(disposition.contains("my-diagram.drawio"),
                "Content-Disposition should include the filename");
    }

    @Test
    @DisplayName("downloadDrawIo should set Content-Type to application/xml")
    void shouldSetContentTypeApplicationXmlForDownload() {
        UUID id = UUID.randomUUID();
        Diagram diagram = buildDiagram(id);

        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(drawIoExportService.convertToDrawIoXml(diagram)).thenReturn(SAMPLE_XML);
        when(drawIoExportService.generateFilename(diagram)).thenReturn("diagram.drawio");

        ResponseEntity<byte[]> response = controller.downloadDrawIo(id);

        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.isCompatibleWith(MediaType.APPLICATION_XML));
    }

    @Test
    @DisplayName("downloadDrawIo should throw DiagramNotFoundException when diagram not found")
    void shouldThrow404WhenDiagramNotFoundForDownload() {
        UUID id = UUID.randomUUID();
        when(diagramRepository.findById(id)).thenReturn(Optional.empty());
        when(domainDiagramRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DiagramNotFoundException.class, () -> controller.downloadDrawIo(id));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Diagram buildDiagram(UUID id) {
        Diagram diagram = new Diagram(InputType.TEXT, "test input", DiagramType.CLASS,
                "classDiagram\n    class User", "A test class diagram");
        try {
            var idField = Diagram.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(diagram, id);
        } catch (Exception ignored) {
        }
        return diagram;
    }
}
