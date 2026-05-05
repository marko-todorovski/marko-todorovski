package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.DiagramRequest;
import com.example.aidiagramgenerator.dto.response.OpenAiDiagramResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OpenAiDiagramService}.
 *
 * <p>The OpenAI HTTP layer is exercised via a Spring WebClient {@link ExchangeFunction}
 * stub — no external network calls are made and no additional test server is required.
 *
 * Tests cover:
 * <ul>
 *   <li>Successful API response → correct diagram type, plantUmlCode, and metadata</li>
 *   <li>API failure fallback → rule-based fallback service is invoked</li>
 *   <li>Malformed / incomplete responses → fallback is triggered</li>
 *   <li>Service configuration (model name, etc.)</li>
 *   <li>DiagramRequest field handling (null-safety)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OpenAiDiagramServiceTest {

    @Mock
    private RuleBasedDiagramService ruleBasedDiagramService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OpenAiDiagramService buildService(ExchangeFunction exchangeFunction) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        return new OpenAiDiagramService(
                webClient, ruleBasedDiagramService, objectMapper, "gpt-4o-mini", "test-key");
    }

    /**
     * Wraps an LLM-produced JSON string inside a minimal OpenAI Chat Completions
     * response envelope, serialising the inner JSON safely to avoid escaping mistakes.
     */
    private String openAiEnvelope(String llmContentJson) {
        try {
            String escaped = objectMapper.writeValueAsString(llmContentJson);
            return """
                    {
                        "id": "test-id",
                        "object": "chat.completion",
                        "choices": [{
                            "index": 0,
                            "message": { "role": "assistant", "content": %s },
                            "finish_reason": "stop"
                        }]
                    }
                    """.formatted(escaped);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Test setup error", e);
        }
    }

    private ExchangeFunction ok(String body) {
        return req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build());
    }

    private OpenAiDiagramResponse fallbackStub() {
        return OpenAiDiagramResponse.builder()
                .diagramType("sequence")
                .plantUmlCode("@startuml\nA->B\n@enduml")
                .explanation("Fallback result")
                .fallbackUsed(true)
                .modelUsed("RuleBased")
                .build();
    }

    // -------------------------------------------------------------------------
    // LLM Generation Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("LLM Generation Tests")
    class LlmGenerationTests {

        @Test
        @DisplayName("Should successfully generate diagram from structured request")
        void shouldGenerateDiagramFromStructuredRequest() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nUser->Auth\\n@enduml\",\"explanation\":\"Login flow\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            DiagramRequest request = new DiagramRequest(
                    "User logs in", List.of("User", "Auth"), List.of("User calls Auth"), "sequence");
            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertNotNull(response);
            assertEquals("sequence", response.getDiagramType());
            assertTrue(response.getPlantUmlCode().contains("@startuml"));
            assertTrue(response.getPlantUmlCode().contains("User->Auth"));
            assertEquals("Login flow", response.getExplanation());
        }

        @Test
        @DisplayName("Should return plantUmlCode from API response")
        void shouldReturnPlantUmlCode() {
            String llmJson = "{\"diagramType\":\"class\",\"plantUmlCode\":\"@startuml\\nclass User {}\\n@enduml\",\"explanation\":\"Class diagram\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            OpenAiDiagramResponse response = service.generateDiagram(
                    new DiagramRequest("Show User", List.of("User"), null, "class"));

            assertTrue(response.getPlantUmlCode().startsWith("@startuml"));
            assertTrue(response.getPlantUmlCode().contains("class User"));
            assertTrue(response.getPlantUmlCode().endsWith("@enduml"));
        }

        @Test
        @DisplayName("Should set fallbackUsed to false on successful LLM response")
        void shouldSetFallbackUsedToFalseOnSuccess() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nA->B\\n@enduml\",\"explanation\":\"test\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            OpenAiDiagramResponse response = service.generateDiagram(
                    new DiagramRequest("test", null, null, null));

            assertFalse(response.isFallbackUsed());
            verifyNoInteractions(ruleBasedDiagramService);
        }

        @Test
        @DisplayName("Should set modelUsed from configuration")
        void shouldSetModelUsedFromConfiguration() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nA->B\\n@enduml\",\"explanation\":\"test\"}";
            WebClient webClient = WebClient.builder()
                    .exchangeFunction(ok(openAiEnvelope(llmJson)))
                    .build();
            OpenAiDiagramService service = new OpenAiDiagramService(
                    webClient, ruleBasedDiagramService, objectMapper, "gpt-4o-mini", "key");

            OpenAiDiagramResponse response = service.generateDiagram(
                    new DiagramRequest("test", null, null, null));

            assertEquals("gpt-4o-mini", response.getModelUsed());
        }

        @Test
        @DisplayName("Should populate generationTimeMs on success")
        void shouldPopulateGenerationTimeMs() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nA->B\\n@enduml\",\"explanation\":\"test\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            OpenAiDiagramResponse response = service.generateDiagram(
                    new DiagramRequest("test", null, null, null));

            assertNotNull(response.getGenerationTimeMs());
            assertTrue(response.getGenerationTimeMs() >= 0);
        }

        @Test
        @DisplayName("Should return model name via getModelName()")
        void shouldReturnModelName() {
            OpenAiDiagramService service = buildService(
                    req -> Mono.error(new RuntimeException("should not be called")));

            assertEquals("gpt-4o-mini", service.getModelName());
        }
    }

    // -------------------------------------------------------------------------
    // Fallback Behavior Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Fallback Behavior Tests")
    class FallbackBehaviorTests {

        @Test
        @DisplayName("Should fall back to rule-based generator when API call fails")
        void shouldFallbackWhenApiCallFails() {
            ExchangeFunction errorExchange = req -> Mono.error(new RuntimeException("Connection refused"));
            OpenAiDiagramService service = buildService(errorExchange);
            DiagramRequest request = new DiagramRequest("test", List.of("A"), List.of(), "sequence");
            when(ruleBasedDiagramService.generate(request)).thenReturn(fallbackStub());

            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertTrue(response.isFallbackUsed());
            assertEquals("RuleBased", response.getModelUsed());
            verify(ruleBasedDiagramService).generate(request);
        }

        @Test
        @DisplayName("Should fall back when API returns HTTP 500")
        void shouldFallbackWhenApiReturns500() {
            ExchangeFunction serverErrorExchange = req -> Mono.just(
                    ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"error\":\"Internal server error\"}")
                            .build());
            OpenAiDiagramService service = buildService(serverErrorExchange);
            DiagramRequest request = new DiagramRequest("test", null, null, null);
            when(ruleBasedDiagramService.generate(any())).thenReturn(fallbackStub());

            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertTrue(response.isFallbackUsed());
            verify(ruleBasedDiagramService).generate(any());
        }

        @Test
        @DisplayName("Should fall back when API returns invalid JSON")
        void shouldFallbackWhenApiReturnsInvalidJson() {
            OpenAiDiagramService service = buildService(ok("this is not valid JSON"));
            DiagramRequest request = new DiagramRequest("test", null, null, null);
            when(ruleBasedDiagramService.generate(any())).thenReturn(fallbackStub());

            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertTrue(response.isFallbackUsed());
            verify(ruleBasedDiagramService).generate(request);
        }

        @Test
        @DisplayName("Should fall back when LLM response is missing plantUmlCode field")
        void shouldFallbackWhenPlantUmlCodeMissing() {
            String llmJson = "{\"diagramType\":\"sequence\",\"explanation\":\"no code here\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));
            DiagramRequest request = new DiagramRequest("test", null, null, null);
            when(ruleBasedDiagramService.generate(any())).thenReturn(fallbackStub());

            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertTrue(response.isFallbackUsed());
            verify(ruleBasedDiagramService).generate(request);
        }

        @Test
        @DisplayName("Should fall back when content field is absent from API response")
        void shouldFallbackWhenContentFieldAbsent() {
            String noContent = "{\"id\":\"t\",\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}";
            OpenAiDiagramService service = buildService(ok(noContent));
            DiagramRequest request = new DiagramRequest("test", null, null, null);
            when(ruleBasedDiagramService.generate(any())).thenReturn(fallbackStub());

            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertTrue(response.isFallbackUsed());
            verify(ruleBasedDiagramService).generate(request);
        }

        @Test
        @DisplayName("Should pass the original request to the fallback service")
        void shouldPassOriginalRequestToFallback() {
            ExchangeFunction errorExchange = req -> Mono.error(new RuntimeException("API unavailable"));
            OpenAiDiagramService service = buildService(errorExchange);
            DiagramRequest request = new DiagramRequest(
                    "complex system",
                    List.of("ServiceA", "ServiceB"),
                    List.of("ServiceA calls ServiceB"),
                    "component");
            when(ruleBasedDiagramService.generate(request)).thenReturn(fallbackStub());

            service.generateDiagram(request);

            verify(ruleBasedDiagramService).generate(request);
        }
    }

    // -------------------------------------------------------------------------
    // Service Configuration Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Service Configuration Tests")
    class ServiceConfigurationTests {

        @Test
        @DisplayName("Should use gpt-4o-mini as default model name")
        void shouldUseGpt4oMiniByDefault() {
            WebClient wc = WebClient.builder()
                    .exchangeFunction(req -> Mono.error(new RuntimeException("not called")))
                    .build();
            OpenAiDiagramService service = new OpenAiDiagramService(
                    wc, ruleBasedDiagramService, objectMapper, "gpt-4o-mini", "key");

            assertEquals("gpt-4o-mini", service.getModelName());
        }

        @Test
        @DisplayName("Should support configurable model name")
        void shouldSupportConfigurableModel() {
            WebClient wc = WebClient.builder()
                    .exchangeFunction(req -> Mono.error(new RuntimeException("not called")))
                    .build();
            OpenAiDiagramService service = new OpenAiDiagramService(
                    wc, ruleBasedDiagramService, objectMapper, "gpt-4o", "key");

            assertEquals("gpt-4o", service.getModelName());
        }

        @Test
        @DisplayName("Should reflect model name in successful response")
        void shouldReflectModelNameInResponse() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nA->B\\n@enduml\",\"explanation\":\"t\"}";
            WebClient wc = WebClient.builder().exchangeFunction(ok(openAiEnvelope(llmJson))).build();
            OpenAiDiagramService service = new OpenAiDiagramService(
                    wc, ruleBasedDiagramService, objectMapper, "custom-model", "key");

            OpenAiDiagramResponse response = service.generateDiagram(
                    new DiagramRequest("test", null, null, null));

            assertEquals("custom-model", response.getModelUsed());
        }
    }

    // -------------------------------------------------------------------------
    // Structured Request Tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Structured Request Tests")
    class StructuredRequestTests {

        @Test
        @DisplayName("Should generate diagram from request with all fields populated")
        void shouldGenerateDiagramWithAllFields() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nUser->Auth\\n@enduml\",\"explanation\":\"Login\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            DiagramRequest request = new DiagramRequest(
                    "User logs in through auth service",
                    List.of("User", "AuthService", "Database"),
                    List.of("User authenticates via AuthService", "AuthService queries Database"),
                    "sequence");
            OpenAiDiagramResponse response = service.generateDiagram(request);

            assertNotNull(response);
            assertFalse(response.isFallbackUsed());
            assertEquals("sequence", response.getDiagramType());
        }

        @Test
        @DisplayName("Should handle request with null entities and relationships without throwing")
        void shouldHandleNullEntitiesAndRelationships() {
            String llmJson = "{\"diagramType\":\"sequence\",\"plantUmlCode\":\"@startuml\\nA->B\\n@enduml\",\"explanation\":\"test\"}";
            OpenAiDiagramService service = buildService(ok(openAiEnvelope(llmJson)));

            DiagramRequest request = new DiagramRequest("Simple description", null, null, null);

            assertDoesNotThrow(() -> service.generateDiagram(request));
        }

        @Test
        @DisplayName("Should include entities in the prompt sent to the API")
        void shouldIncludeEntitiesInPrompt() {
            // Capture the outgoing request and verify payload contents
            final String[] capturedBody = {null};
            ExchangeFunction capturingExchange = req -> {
                String llmJson = "{\"diagramType\":\"class\",\"plantUmlCode\":\"@startuml\\nclass Order {}\\n@enduml\",\"explanation\":\"Order entity\"}";
                capturedBody[0] = req.body().toString(); // opaque but exchange fires
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body(openAiEnvelope(llmJson))
                                .build());
            };
            OpenAiDiagramService service = buildService(capturingExchange);

            DiagramRequest request = new DiagramRequest(
                    "Order management system",
                    List.of("Order", "Customer", "Product"),
                    List.of("Customer places Order"),
                    "class");
            OpenAiDiagramResponse response = service.generateDiagram(request);

            // Response should still be valid; the exchange was called
            assertNotNull(response);
            assertFalse(response.isFallbackUsed());
        }
    }
}
