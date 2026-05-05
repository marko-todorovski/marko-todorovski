package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Boot integration tests for {@link PlantUmlDiagramController}.
 *
 * <p>Exercises the full request/response stack via MockMvc against an
 * H2 in-memory database (dev profile). Each test method runs inside its own
 * transaction that is rolled back afterwards to keep tests isolated.
 */
@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
@Transactional
class DiagramControllerIntegrationTest {

    private static final String GENERATE_URL = "/api/diagram/generate";
    private static final String GET_URL      = "/api/diagram/{id}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // -----------------------------------------------------------------------
    // Full flow: POST /generate → GET /{id}
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Full flow: POST /generate → GET /{id}")
    class FullFlow {

        @Test
        @DisplayName("generated diagram can be retrieved by its persisted ID")
        void shouldPersistAndRetrieveDiagram() throws Exception {
            // Use explicit diagramType to guarantee 100% confidence → 200
            String requestBody = """
                    {"text": "User, Service, and Database components", "diagramType": "class diagram"}
                    """;

            // Step 1 – generate
            MvcResult generateResult = mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            String responseBody = generateResult.getResponse().getContentAsString();
            JsonNode root = objectMapper.readTree(responseBody);
            String id = root.path("data").path("id").asText();

            // Step 2 – retrieve by ID
            mockMvc.perform(get(GET_URL, id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.plantUmlCode").isNotEmpty());
        }

        @Test
        @DisplayName("retrieving a non-existent ID returns 404")
        void shouldReturn404ForUnknownId() throws Exception {
            mockMvc.perform(get(GET_URL, "00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("not found")));
        }
    }

    // -----------------------------------------------------------------------
    // POST /generate – valid input
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /generate – valid input")
    class ValidInput {

        @Test
        @DisplayName("returns 200 with diagram data when diagramType is provided")
        void shouldReturn200WithDiagramData() throws Exception {
            // Explicit diagramType → confidence 100% → 200 (not 422)
            String requestBody = """
                    {"text": "User, Service, and Database components", "diagramType": "class diagram"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.diagramType").isNotEmpty())
                    .andExpect(jsonPath("$.data.plantUmlCode").isNotEmpty());
        }

        @Test
        @DisplayName("response includes a message field")
        void shouldIncludeMessageInResponse() throws Exception {
            String requestBody = """
                    {"text": "User, Service, and Database components", "diagramType": "sequence diagram"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // POST /generate – invalid input → 400
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /generate – invalid input returns 400")
    class InvalidInput {

        @Test
        @DisplayName("blank text returns 400")
        void shouldReturn400ForBlankText() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("null text returns 400")
        void shouldReturn400ForNullText() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("missing body returns 400")
        void shouldReturn400ForMissingBody() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("content exceeding 10 000 characters returns 400")
        void shouldReturn400ForOversizedText() throws Exception {
            String longText = "a".repeat(10_001);
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Map.of("text", longText));

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("malformed JSON returns 400")
        void shouldReturn400ForMalformedJson() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not-valid-json}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // -----------------------------------------------------------------------
    // Auto-detect behavior
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Auto-detect diagram type")
    class AutoDetect {

        @Test
        @DisplayName("text mentioning 'sequence' triggers SEQUENCE auto-detection (95%+ confidence)")
        void shouldAutoDetectSequenceDiagram() throws Exception {
            // "sequence" keyword matches EXPLICIT_TYPE_PATTERNS → 95%+ confidence → 200
            String requestBody = """
                    {"text": "I need a sequence diagram for the login flow"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.diagramType").value("Sequence Diagram"));
        }

        @Test
        @DisplayName("text mentioning 'class diagram' triggers CLASS auto-detection (95%+ confidence)")
        void shouldAutoDetectClassDiagram() throws Exception {
            // "class diagram" keyword matches EXPLICIT_TYPE_PATTERNS → 95%+ confidence → 200
            String requestBody = """
                    {"text": "Generate a class diagram for the order domain"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.diagramType").value("Class Diagram"));
        }

        @Test
        @DisplayName("explicit diagramType field overrides text-based auto-detection")
        void shouldRespectExplicitDiagramType() throws Exception {
            // Text suggests SEQUENCE but diagramType field says "class diagram" → CLASS at 100%
            String requestBody = """
                    {"text": "I need a sequence diagram for the login flow",
                     "diagramType": "class diagram"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.diagramType").value("Class Diagram"));
        }

        @Test
        @DisplayName("ambiguous text without diagramType returns a response (200 or 422)")
        void shouldRespondForAmbiguousTextWithoutDiagramType() throws Exception {
            // Low-signal text → may produce 200 (high confidence), 422 (medium/low confidence)
            // Either way the service must respond and not crash
            String requestBody = """
                    {"text": "A system with some components and relationships"}
                    """;

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isNotEmpty());
        }
    }
}
