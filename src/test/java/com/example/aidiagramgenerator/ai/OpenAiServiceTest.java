package com.example.aidiagramgenerator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiServiceTest {

    private static final String VALID_API_KEY = "sk-test-1234567890";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final String DIAGRAM_MODEL = "gpt-4o";

    private MockRestServiceServer mockServer;
    private OpenAiService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiService(builder, new ObjectMapper(), VALID_API_KEY, API_URL, MODEL, DIAGRAM_MODEL);
    }

    // ── isApiKeyConfigured ────────────────────────────────────────────────────

    @Test
    void isApiKeyConfigured_returnsTrueWhenKeyPresent() {
        assertTrue(service.isApiKeyConfigured());
    }

    @Test
    void isApiKeyConfigured_returnsFalseForNullKey() {
        assertFalse(new OpenAiService(RestClient.builder(), new ObjectMapper(), null, API_URL, MODEL, DIAGRAM_MODEL).isApiKeyConfigured());
    }

    @Test
    void isApiKeyConfigured_returnsFalseForBlankKey() {
        assertFalse(new OpenAiService(RestClient.builder(), new ObjectMapper(), "   ", API_URL, MODEL, DIAGRAM_MODEL).isApiKeyConfigured());
    }

    // ── getApiUrl ─────────────────────────────────────────────────────────────

    @Test
    void getApiUrl_returnsConfiguredUrl() {
        assertEquals(API_URL, service.getApiUrl());
    }

    // ── getModelName ──────────────────────────────────────────────────────────

    @Test
    void getModelName_returnsGPT4o() {
        assertEquals("GPT-4o", service.getModelName());
    }

    // ── callLLM — success ─────────────────────────────────────────────────────

    @Test
    void callLLM_success_returnsSuccessResult() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"sequenceDiagram\\n    A->>B: hello\"}}]}",
                        MediaType.APPLICATION_JSON));

        LlmResult result = service.callLLM("Generate a sequence diagram");

        assertTrue(result.isSuccess());
        assertEquals("sequenceDiagram\n    A->>B: hello", result.getContent());
        mockServer.verify();
    }

    @Test
    void callLLM_success_contentIsNonNullNonEmpty() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"flowchart LR\\n    A --> B\"}}]}",
                        MediaType.APPLICATION_JSON));

        LlmResult result = service.callLLM("Make a flowchart");

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertFalse(result.getContent().isBlank());
        mockServer.verify();
    }

    // ── callLLM — fallback: no API key (no HTTP call) ─────────────────────────

    @Test
    void callLLM_noApiKey_returnsFailure() {
        LlmResult result = new OpenAiService(RestClient.builder(), new ObjectMapper(), "", API_URL, MODEL, DIAGRAM_MODEL)
                .callLLM("any prompt");

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
    }

    @Test
    void callLLM_nullApiKey_returnsFailure() {
        LlmResult result = new OpenAiService(RestClient.builder(), new ObjectMapper(), null, API_URL, MODEL, DIAGRAM_MODEL)
                .callLLM("any prompt");

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
    }

    // ── callLLM — fallback: HTTP errors ───────────────────────────────────────

    @Test
    void callLLM_apiReturns401_returnsFailure() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());

        LlmResult result = service.callLLM("generate diagram");

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        mockServer.verify();
    }

    @Test
    void callLLM_apiReturns500_returnsFailure() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        LlmResult result = service.callLLM("generate diagram");

        assertFalse(result.isSuccess());
        assertNull(result.getContent());
        mockServer.verify();
    }

    @Test
    void callLLM_apiReturnsEmptyBody_returnsFailure() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        LlmResult result = service.callLLM("generate diagram");

        assertFalse(result.isSuccess());
        mockServer.verify();
    }

    @Test
    void callLLM_apiReturnsInvalidJson_returnsFailure() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not valid json", MediaType.APPLICATION_JSON));

        LlmResult result = service.callLLM("generate diagram");

        assertFalse(result.isSuccess());
        mockServer.verify();
    }

    @Test
    void callLLM_apiReturnsJsonWithoutContent_returnsFailure() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"error\":\"no choices\"}", MediaType.APPLICATION_JSON));

        LlmResult result = service.callLLM("generate diagram");

        assertFalse(result.isSuccess());
        mockServer.verify();
    }

    // ── callLLM — never throws ────────────────────────────────────────────────

    @Test
    void callLLM_neverThrowsEvenOnHttpError() {
        mockServer.expect(requestTo(API_URL))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> service.callLLM("any prompt"));
    }

    // ── generateStructuredResponse — throws on HTTP failure (existing contract)

    @Test
    void generateStructuredResponse_throwsOnServerError() {
        mockServer.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThrows(AiServiceException.class,
                () -> service.generateStructuredResponse("structured prompt"));
        mockServer.verify();
    }
}       
