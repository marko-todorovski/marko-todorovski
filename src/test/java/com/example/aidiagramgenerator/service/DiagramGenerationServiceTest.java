package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.LlmResult;
import com.example.aidiagramgenerator.enums.DiagramType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramGenerationServiceTest {

    @Mock
    private MermaidValidator mermaidValidator;

    @Mock
    private AiModelService aiModelService;

    private DiagramGenerationService service;

    @BeforeEach
    void setUp() {
        service = new DiagramGenerationService(mermaidValidator, aiModelService);
    }

    // ── LLM success path ──────────────────────────────────────────────────────

    @Test
    void generateFromText_llmSuccess_setsGenerationModeToLlm() {
        String mermaidCode = "sequenceDiagram\n    User->>Service: request";
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.success(mermaidCode));
        when(aiModelService.getModelName()).thenReturn("GPT-4o");
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("User calls service", null);

        assertNotNull(result);
        assertEquals("LLM", result.getGenerationMode());
        assertEquals(mermaidCode, result.getMermaidCode());
    }

    @Test
    void generateFromText_llmSuccess_mermaidCodeMatchesLlmContent() {
        String expected = "flowchart LR\n    A --> B";
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.success(expected));
        when(aiModelService.getModelName()).thenReturn("GPT-4o");
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("A connects to B", null);

        assertEquals(expected, result.getMermaidCode());
    }

    @Test
    void generateFromText_llmSuccess_stripsMermaidFences() {
        String withFences = "```mermaid\nflowchart LR\n    A --> B\n```";
        String expected = "flowchart LR\n    A --> B";
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.success(withFences));
        when(aiModelService.getModelName()).thenReturn("GPT-4o");
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("A connects to B", null);

        assertEquals(expected, result.getMermaidCode());
        assertEquals("LLM", result.getGenerationMode());
    }

    // ── LLM failure → rule-based fallback ────────────────────────────────────

    @Test
    void generateFromText_llmFailure_fallsBackToRuleBased() {
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.failure());
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("User calls service", null);

        assertNotNull(result);
        assertEquals("RULE_BASED", result.getGenerationMode());
    }

    @Test
    void generateFromText_llmFailure_resultIsNotNull() {
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.failure());
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("anything", null);

        assertNotNull(result);
        assertNotNull(result.getMermaidCode());
        assertFalse(result.getMermaidCode().isBlank());
    }

    @Test
    void generateFromText_llmThrowsException_fallsBackToRuleBased() {
        when(aiModelService.callLLM(anyString())).thenThrow(new RuntimeException("network error"));
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("User calls service", null);

        assertNotNull(result);
        assertEquals("RULE_BASED", result.getGenerationMode());
    }

    // ── Rule-based always returns a result ───────────────────────────────────

    @Test
    void generateRuleBased_alwaysReturnsNonNull() {
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateRuleBased("anything", null);

        assertNotNull(result);
        assertEquals("RULE_BASED", result.getGenerationMode());
    }

    @Test
    void generateRuleBased_withExplicitType_honorsRequestedType() {
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateRuleBased("login flow", DiagramType.SEQUENCE);

        assertEquals(DiagramType.SEQUENCE, result.getDiagramType());
        assertEquals("RULE_BASED", result.getGenerationMode());
    }

    // ── generateFromText never returns null ───────────────────────────────────

    @Test
    void generateFromText_neverReturnsNull() {
        when(aiModelService.callLLM(anyString())).thenReturn(LlmResult.failure());
        doNothing().when(mermaidValidator).validate(anyString());

        DiagramGenerationService.DiagramResult result = service.generateFromText("something", null);

        assertNotNull(result);
    }
}
