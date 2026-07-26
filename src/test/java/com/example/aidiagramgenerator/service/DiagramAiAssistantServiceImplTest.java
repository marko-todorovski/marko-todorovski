package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.exception.DiagramAiException;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how the AI assistant service surfaces failures from the AI client.
 */
class DiagramAiAssistantServiceImplTest {

    private final DomainDiagramRepository diagramRepository = mock(DomainDiagramRepository.class);
    private final DiagramAiAssistantClient assistantClient = mock(DiagramAiAssistantClient.class);
    private final DiagramRenderingService renderingService = mock(DiagramRenderingService.class);
    private final DiagramAiAssistantRateLimiter rateLimiter = mock(DiagramAiAssistantRateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);

    private DiagramAiAssistantServiceImpl service;
    private UUID diagramId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        service = new DiagramAiAssistantServiceImpl(
                diagramRepository, assistantClient, renderingService, rateLimiter, objectMapper, projectAccessService);
        diagramId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        Diagram diagram = new Diagram("prompt", DiagramType.CLASS, "@startuml\nclass A\n@enduml");
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(diagram));
    }

    @Test
    void genericProviderFailureIsSurfacedAsAiUnavailable() {
        doThrow(new AiServiceException("provider exploded"))
                .when(assistantClient).generateStructuredResponse(anyString());

        assertThatThrownBy(() -> service.explainDiagram(diagramId, ownerId, "@startuml\nclass A\n@enduml"))
                .isInstanceOf(DiagramAiException.class)
                .satisfies(ex -> {
                    DiagramAiException aiException = (DiagramAiException) ex;
                    org.assertj.core.api.Assertions.assertThat(aiException.getCode()).isEqualTo("AI_UNAVAILABLE");
                });
    }

    @Test
    void timeoutMessageIsMappedToAiTimeout() {
        doThrow(new AiServiceException("Connection timed out"))
                .when(assistantClient).generateStructuredResponse(anyString());

        assertThatThrownBy(() -> service.explainDiagram(diagramId, ownerId, "@startuml\nclass A\n@enduml"))
                .isInstanceOf(DiagramAiException.class)
                .satisfies(ex -> {
                    DiagramAiException aiException = (DiagramAiException) ex;
                    org.assertj.core.api.Assertions.assertThat(aiException.getCode()).isEqualTo("AI_TIMEOUT");
                });
    }

    @Test
    void nullMessageProviderFailureDoesNotThrowAndMapsToAiUnavailable() {
        doThrow(new AiServiceException(null))
                .when(assistantClient).generateStructuredResponse(anyString());

        assertThatThrownBy(() -> service.explainDiagram(diagramId, ownerId, "@startuml\nclass A\n@enduml"))
                .isInstanceOf(DiagramAiException.class)
                .satisfies(ex -> {
                    DiagramAiException aiException = (DiagramAiException) ex;
                    org.assertj.core.api.Assertions.assertThat(aiException.getCode()).isEqualTo("AI_UNAVAILABLE");
                });
    }

    @Test
    void blankAiResponseIsRejectedAsInvalidOutput() {
        when(assistantClient.generateStructuredResponse(anyString())).thenReturn("   ");

        assertThatThrownBy(() -> service.explainDiagram(diagramId, ownerId, "@startuml\nclass A\n@enduml"))
                .isInstanceOf(DiagramAiException.class)
                .satisfies(ex -> {
                    DiagramAiException aiException = (DiagramAiException) ex;
                    org.assertj.core.api.Assertions.assertThat(aiException.getCode()).isEqualTo("AI_OUTPUT_INVALID");
                });
    }
}
