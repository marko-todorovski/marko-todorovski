package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.DiagramRequest;
import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UrlDiagramRequest;
import com.example.aidiagramgenerator.dto.request.XmlDiagramRequest;
import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.dto.response.OpenAiDiagramResponse;
import com.example.aidiagramgenerator.enums.DiagramType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramServiceImplTest {

    @Mock
    private OpenAiDiagramService openAiDiagramService;

    @Mock
    private RuleBasedDiagramService ruleBasedDiagramService;

    private DiagramServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiagramServiceImpl(openAiDiagramService, ruleBasedDiagramService, 2);
    }

    private DiagramRequest sampleRequest() {
        return new DiagramRequest("Create a class diagram", Collections.emptyList(), Collections.emptyList(), "class");
    }

    private OpenAiDiagramResponse llmResponse(String type, String plantUml) {
        return OpenAiDiagramResponse.builder()
                .diagramType(type)
                .plantUmlCode(plantUml)
                .explanation("LLM explanation")
                .fallbackUsed(false)
                .modelUsed("gpt-4o")
                .generationTimeMs(200L)
                .build();
    }

    private OpenAiDiagramResponse ruleBasedResponse() {
        return OpenAiDiagramResponse.builder()
                .diagramType("sequence")
                .plantUmlCode("@startuml\nA -> B\n@enduml")
                .explanation("Rule-based explanation")
                .fallbackUsed(true)
                .modelUsed("RuleBased")
                .generationTimeMs(5L)
                .build();
    }

    // ── LLM success ──────────────────────────────────────────────────────────

    @Test
    void llmSuccess_returnsMappedResponse() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", "@startuml\nclass User\n@enduml"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertEquals(DiagramType.CLASS, result.getDiagramType());
        assertEquals("@startuml\nclass User\n@enduml", result.getMermaidCode());
        assertEquals("LLM explanation", result.getExplanation());
        verifyNoInteractions(ruleBasedDiagramService);
    }

    @Test
    void llmSuccess_mapsSequenceType() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("sequence", "@startuml\nA -> B\n@enduml"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertEquals(DiagramType.SEQUENCE, result.getDiagramType());
    }

    @Test
    void llmSuccess_mapsErType() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("er", "@startuml\nentity User\n@enduml"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertEquals(DiagramType.ER, result.getDiagramType());
    }

    @Test
    void llmSuccess_mapsUnknownTypeToSequence() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("unknown_type", "@startuml\nA -> B\n@enduml"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertEquals(DiagramType.SEQUENCE, result.getDiagramType());
    }

    @Test
    void llmSuccess_prefersPlantUmlOverMermaid() {
        OpenAiDiagramResponse response = OpenAiDiagramResponse.builder()
                .diagramType("class")
                .plantUmlCode("@startuml\nclass A\n@enduml")
                .mermaidCode("classDiagram\nclass A")
                .explanation("Both codes")
                .fallbackUsed(false)
                .modelUsed("gpt-4o")
                .build();
        when(openAiDiagramService.generateDiagram(any())).thenReturn(response);

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertEquals("@startuml\nclass A\n@enduml", result.getMermaidCode());
    }

    @Test
    void llmSuccess_fallsBackToMermaidIfNoPlantUml() {
        OpenAiDiagramResponse response = OpenAiDiagramResponse.builder()
                .diagramType("class")
                .plantUmlCode(null)
                .mermaidCode("classDiagram\nclass A")
                .explanation("Mermaid only")
                .fallbackUsed(false)
                .modelUsed("gpt-4o")
                .build();
        when(openAiDiagramService.generateDiagram(any())).thenReturn(response);

        // null plantUmlCode triggers fallback
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
    }

    // ── LLM returns null ─────────────────────────────────────────────────────

    @Test
    void llmReturnsNull_triggersRuleBasedFallback() {
        when(openAiDiagramService.generateDiagram(any())).thenReturn(null);
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
        assertTrue(result.getExplanation().contains("null"));
        verify(ruleBasedDiagramService).generate(any());
    }

    // ── LLM returns empty code ───────────────────────────────────────────────

    @Test
    void llmReturnsEmptyCode_triggersRuleBasedFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", ""));
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
        assertTrue(result.getExplanation().contains("empty"));
        verify(ruleBasedDiagramService).generate(any());
    }

    @Test
    void llmReturnsBlankCode_triggersRuleBasedFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", "   "));
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
        verify(ruleBasedDiagramService).generate(any());
    }

    // ── LLM throws exception ─────────────────────────────────────────────────

    @Test
    void llmThrowsRuntimeException_triggersRuleBasedFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenThrow(new RuntimeException("API connection refused"));
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
        verify(ruleBasedDiagramService).generate(any());
    }

    // ── LLM times out ────────────────────────────────────────────────────────

    @Test
    void llmTimesOut_triggersRuleBasedFallback() {
        when(openAiDiagramService.generateDiagram(any())).thenAnswer(invocation -> {
            Thread.sleep(5000); // longer than 2s timeout
            return llmResponse("class", "@startuml\nclass A\n@enduml");
        });
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertTrue(result.getExplanation().contains("Fallback"));
        assertTrue(result.getExplanation().contains("timed out"));
        verify(ruleBasedDiagramService).generate(any());
    }

    // ── Both LLM and rule-based fail ─────────────────────────────────────────

    @Test
    void bothFail_returnsEmergencyDiagram() {
        when(openAiDiagramService.generateDiagram(any())).thenReturn(null);
        when(ruleBasedDiagramService.generate(any()))
                .thenThrow(new RuntimeException("Database down"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertEquals(DiagramType.SEQUENCE, result.getDiagramType());
        assertNotNull(result.getMermaidCode());
        assertTrue(result.getMermaidCode().contains("sequenceDiagram"));
        assertTrue(result.getExplanation().contains("Emergency"));
    }

    // ── Response is never null ───────────────────────────────────────────────

    @Test
    void responseIsNeverNull_evenWhenEverythingFails() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenThrow(new RuntimeException("Total failure"));
        when(ruleBasedDiagramService.generate(any()))
                .thenThrow(new RuntimeException("Also failed"));

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertNotNull(result.getDiagramType());
        assertNotNull(result.getMermaidCode());
        assertNotNull(result.getExplanation());
    }

    // ── Fallback explanation includes reason ─────────────────────────────────

    @Test
    void fallbackExplanation_includesOriginalReason() {
        when(openAiDiagramService.generateDiagram(any())).thenReturn(null);
        when(ruleBasedDiagramService.generate(any())).thenReturn(ruleBasedResponse());

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertTrue(result.getExplanation().startsWith("Fallback:"));
        assertTrue(result.getExplanation().contains("Rule-based explanation"));
    }

    // ── Internal fallback logging ────────────────────────────────────────────

    @Test
    void llmInternalFallback_logsButReturnsResponse() {
        OpenAiDiagramResponse internalFallback = OpenAiDiagramResponse.builder()
                .diagramType("sequence")
                .plantUmlCode("@startuml\nA -> B\n@enduml")
                .explanation("Internal fallback used")
                .fallbackUsed(true)
                .modelUsed("RuleBased")
                .build();
        when(openAiDiagramService.generateDiagram(any())).thenReturn(internalFallback);

        DiagramResponse result = service.generateWithFallback(sampleRequest());

        assertNotNull(result);
        assertEquals(DiagramType.SEQUENCE, result.getDiagramType());
        assertEquals("Internal fallback used", result.getExplanation());
        verifyNoInteractions(ruleBasedDiagramService);
    }

    // ── generateFromText ─────────────────────────────────────────────────────

    @Test
    void generateFromText_delegatesToGenerateWithFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", "@startuml\nclass User\n@enduml"));

        TextDiagramRequest request = new TextDiagramRequest("Create class diagram", DiagramType.CLASS);
        DiagramResponse result = service.generateFromText(request);

        assertNotNull(result);
        assertEquals(DiagramType.CLASS, result.getDiagramType());
        verify(openAiDiagramService).generateDiagram(any());
    }

    @Test
    void generateFromText_nullDiagramType_doesNotThrow() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("sequence", "@startuml\nA -> B\n@enduml"));

        TextDiagramRequest request = new TextDiagramRequest("Create diagram", null);
        DiagramResponse result = service.generateFromText(request);

        assertNotNull(result);
    }

    // ── generateFromXml ──────────────────────────────────────────────────────

    @Test
    void generateFromXml_delegatesToGenerateWithFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", "@startuml\nclass A\n@enduml"));

        XmlDiagramRequest request = new XmlDiagramRequest("<xml>data</xml>");
        DiagramResponse result = service.generateFromXml(request);

        assertNotNull(result);
        verify(openAiDiagramService).generateDiagram(any());
    }

    // ── generateFromUrl ──────────────────────────────────────────────────────

    @Test
    void generateFromUrl_delegatesToGenerateWithFallback() {
        when(openAiDiagramService.generateDiagram(any()))
                .thenReturn(llmResponse("class", "@startuml\nclass A\n@enduml"));

        UrlDiagramRequest request = new UrlDiagramRequest("https://example.com/api");
        DiagramResponse result = service.generateFromUrl(request);

        assertNotNull(result);
        verify(openAiDiagramService).generateDiagram(any());
    }
}
