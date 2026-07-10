package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramEvaluationRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.ConfidenceDiagramService;
import com.example.aidiagramgenerator.service.DiagramSuggestionService;
import com.example.aidiagramgenerator.service.MermaidRenderer;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantUmlDiagramAssetEndpointTest {

    @Mock
    private ConfidenceDiagramService confidenceDiagramService;

    @Mock
    private DiagramSuggestionService suggestionService;

    @Mock
    private DiagramRenderingService renderingService;

    @Mock
    private DomainDiagramRepository domainDiagramRepository;

    @Mock
    private DiagramRepository diagramRepository;

    @Mock
    private MermaidRenderer mermaidRenderer;

    @Mock
    private DomainDiagramEvaluationRepository evaluationRepository;

    private PlantUmlDiagramController controller;

    @BeforeEach
    void setUp() {
        controller = new PlantUmlDiagramController(
                confidenceDiagramService,
                suggestionService,
                renderingService,
                domainDiagramRepository,
                diagramRepository,
                mermaidRenderer,
                evaluationRepository);
    }

    @Test
    @DisplayName("SVG endpoint should fall back to DiagramRepository-backed Mermaid diagrams")
    void shouldRenderSvgForMermaidDiagramWhenDomainDiagramMissing() {
        UUID id = UUID.randomUUID();
        com.example.aidiagramgenerator.entity.Diagram diagram = buildMermaidDiagram();
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>PDF</text></svg>";

        when(domainDiagramRepository.findById(id)).thenReturn(Optional.empty());
        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(mermaidRenderer.renderToSvg(diagram.getMermaidCode())).thenReturn(svg);

        ResponseEntity<byte[]> response = controller.getDiagramSvg(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("image/svg+xml")));
        assertEquals(svg, new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("PNG endpoint should not 404 for DiagramRepository-backed Mermaid diagrams")
    void shouldRenderPngRouteForMermaidDiagramWhenDomainDiagramMissing() {
        UUID id = UUID.randomUUID();
        com.example.aidiagramgenerator.entity.Diagram diagram = buildMermaidDiagram();
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>PDF</text></svg>";

        when(domainDiagramRepository.findById(id)).thenReturn(Optional.empty());
        when(diagramRepository.findById(id)).thenReturn(Optional.of(diagram));
        when(mermaidRenderer.renderToSvg(diagram.getMermaidCode())).thenReturn(svg);

        ResponseEntity<byte[]> response = controller.getDiagramPng(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("image/svg+xml")));
        assertEquals(svg, new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("asset endpoints should return 404 when ID is absent from both repositories")
    void shouldReturn404WhenDiagramMissingFromBothRepositories() {
        UUID id = UUID.randomUUID();

        when(domainDiagramRepository.findById(id)).thenReturn(Optional.empty());
        when(diagramRepository.findById(id)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getDiagramSvg(id).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.getDiagramPng(id).getStatusCode());
    }

    private com.example.aidiagramgenerator.entity.Diagram buildMermaidDiagram() {
        return new com.example.aidiagramgenerator.entity.Diagram(
                InputType.PDF,
                "source.pdf",
                DiagramType.CLASS,
                "classDiagram\n    class User",
                "Generated from PDF");
    }
}
