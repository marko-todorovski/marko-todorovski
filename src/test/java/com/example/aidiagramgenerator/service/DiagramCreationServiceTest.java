package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.service.DiagramGenerationService.DiagramResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagramCreationServiceTest {

    @Mock
    private DiagramGenerationService diagramGenerationService;

    @Mock
    private DiagramRepository diagramRepository;

    private DiagramCreationService service;

    @BeforeEach
    void setUp() {
        service = new DiagramCreationService(diagramGenerationService, diagramRepository);
    }

    @Test
    @DisplayName("text generation should generate, save, and return persisted ID")
    void shouldGenerateSaveAndReturnIdForText() {
        assertGenerateSaveAndReturnId(InputType.TEXT, "User calls service", "User calls service", DiagramType.SEQUENCE);
    }

    @Test
    @DisplayName("PDF generation should generate, save, and return persisted ID")
    void shouldGenerateSaveAndReturnIdForPdf() {
        assertGenerateSaveAndReturnId(InputType.PDF, "source.pdf", "Extracted PDF text", null);
    }

    @Test
    @DisplayName("XML generation should generate, save, and return persisted ID")
    void shouldGenerateSaveAndReturnIdForXml() {
        assertGenerateSaveAndReturnId(InputType.XML, "<diagram>User</diagram>", "User", null);
    }

    @Test
    @DisplayName("URL generation should generate, save, and return persisted ID")
    void shouldGenerateSaveAndReturnIdForUrl() {
        assertGenerateSaveAndReturnId(InputType.URL, "https://example.com", "Fetched page text", null);
    }

    @Test
    @DisplayName("save should create a new version when the same input already exists")
    void shouldVersionDuplicateInput() {
        UUID parentId = UUID.randomUUID();
        Diagram existing = new Diagram(
                InputType.TEXT,
                "same input",
                DiagramType.CLASS,
                "classDiagram\n    class Old",
                "old");
        setId(existing, parentId);
        DiagramResult result = diagramResult();

        when(diagramRepository.findOriginalByInputContentAndInputType("same input", InputType.TEXT))
                .thenReturn(Optional.of(existing));
        when(diagramRepository.findMaxVersionByInputContentAndInputType("same input", InputType.TEXT))
                .thenReturn(Optional.of(3));
        when(diagramRepository.save(any(Diagram.class))).thenAnswer(invocation -> {
            Diagram saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });

        DiagramResponse response = service.save(InputType.TEXT, "same input", result);

        ArgumentCaptor<Diagram> savedDiagram = ArgumentCaptor.forClass(Diagram.class);
        org.mockito.Mockito.verify(diagramRepository).save(savedDiagram.capture());
        assertEquals(4, savedDiagram.getValue().getVersionNumber());
        assertEquals(parentId, savedDiagram.getValue().getParentDiagramId());
        assertNotNull(response.getId());
    }

    private void assertGenerateSaveAndReturnId(InputType inputType,
                                               String inputContent,
                                               String textForGeneration,
                                               DiagramType requestedType) {
        UUID id = UUID.randomUUID();
        DiagramResult result = diagramResult();

        when(diagramGenerationService.generateFromText(textForGeneration, requestedType)).thenReturn(result);
        when(diagramRepository.findOriginalByInputContentAndInputType(inputContent, inputType))
                .thenReturn(Optional.empty());
        when(diagramRepository.save(any(Diagram.class))).thenAnswer(invocation -> {
            Diagram saved = invocation.getArgument(0);
            setId(saved, id);
            return saved;
        });

        DiagramResponse response = service.generateAndSave(inputType, inputContent, textForGeneration, requestedType);

        ArgumentCaptor<Diagram> savedDiagram = ArgumentCaptor.forClass(Diagram.class);
        org.mockito.Mockito.verify(diagramGenerationService).generateFromText(textForGeneration, requestedType);
        org.mockito.Mockito.verify(diagramRepository).save(savedDiagram.capture());
        assertEquals(inputType, savedDiagram.getValue().getInputType());
        assertEquals(inputContent, savedDiagram.getValue().getInputContent());
        assertEquals(1, savedDiagram.getValue().getVersionNumber());
        assertNull(savedDiagram.getValue().getParentDiagramId());
        assertEquals(id, response.getId());
        assertEquals(result.getDiagramType(), response.getDiagramType());
        assertEquals(result.getMermaidCode(), response.getMermaidCode());
    }

    private DiagramResult diagramResult() {
        return new DiagramResult(
                DiagramType.CLASS,
                "classDiagram\n    class User",
                "Generated diagram",
                List.of("user"),
                List.of("NODE_USER_DETECTED"),
                "RULE_BASED");
    }

    private void setId(Diagram diagram, UUID id) {
        try {
            Field field = Diagram.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(diagram, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
