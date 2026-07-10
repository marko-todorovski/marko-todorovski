package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests covering the manual diagram generation flow.
 *
 * <p>Tests the full path: {@code POST /api/diagram/generate} → database → export endpoints.
 *
 * <p>Runs against an H2 in-memory database (dev profile). Every test method is wrapped
 * in a transaction that is rolled back afterwards to keep tests isolated.
 */
@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
@Transactional
class ManualDiagramGenerationFlowTest {

    private static final String GENERATE_URL = "/api/diagram/generate";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ── @MethodSource factory methods (must be static in the outer class) ────

    /**
     * All 11 frontend diagram type strings that the UI can send.
     * Each should produce HTTP 200 with a saved diagram.
     */
    static Stream<Arguments> allFrontendDiagramTypes() {
        return Stream.of(
                Arguments.of("CLASS"),
                Arguments.of("SEQUENCE"),
                Arguments.of("ER"),
                Arguments.of("COMPONENT"),
                Arguments.of("DEPLOYMENT"),
                Arguments.of("USE_CASE"),
                Arguments.of("OBJECT"),
                Arguments.of("ACTIVITY"),
                Arguments.of("STATE"),
                Arguments.of("COLLABORATION"),
                Arguments.of("MICROSERVICES_ARCHITECTURE")
        );
    }

    /**
     * Pairs of (frontend type input, PlantUML keyword expected in the generated code).
     * Tests that aliases resolve to the correct backend type by inspecting the template output.
     */
    static Stream<Arguments> diagramTypeAliasMappings() {
        return Stream.of(
                // Lowercase with space — same as frontend label text
                Arguments.of("class diagram",              "class "),
                Arguments.of("sequence diagram",           "actor "),
                // Uppercase enum name — direct
                Arguments.of("CLASS",                      "class "),
                Arguments.of("SEQUENCE",                   "actor "),
                // Suffixed alias
                Arguments.of("CLASS_DIAGRAM",              "class "),
                Arguments.of("SEQUENCE_DIAGRAM",           "actor "),
                Arguments.of("ER_DIAGRAM",                 "entity "),
                Arguments.of("USE_CASE_DIAGRAM",           "actor "),
                Arguments.of("COMPONENT_DIAGRAM",          "[Web App]"),
                Arguments.of("DEPLOYMENT_DIAGRAM",         "node "),
                // Frontend composite alias
                Arguments.of("MICROSERVICES_ARCHITECTURE", "[API Gateway]")
        );
    }

    // ── 1. All 11 supported diagram types ─────────────────────────────────────

    @Nested
    @DisplayName("All 11 supported frontend diagram types generate successfully")
    class AllDiagramTypes {

        @ParameterizedTest(name = "diagramType=''{0}'' → 200 with id and PlantUML code")
        @MethodSource("com.example.aidiagramgenerator.controller.ManualDiagramGenerationFlowTest#allFrontendDiagramTypes")
        @DisplayName("each frontend type string produces a persisted diagram")
        void shouldGenerateForEveryFrontendDiagramType(String frontendType) throws Exception {
            // No text supplied — uses the built-in DEFAULT_TEMPLATE; avoids any AI call.
            String body = "{\"diagramType\": \"" + frontendType + "\"}";

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.plantUmlCode", startsWith("@startuml")))
                    .andExpect(jsonPath("$.data.diagramType").isNotEmpty());
        }
    }

    // ── 2. Input field aliases: text / description / inputText ───────────────

    @Nested
    @DisplayName("Input field aliases: text / description / inputText")
    class FieldNormalization {

        @Test
        @DisplayName("'text' field is accepted and produces a diagram")
        void shouldAcceptTextField() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"User logs in to the system\", \"diagramType\": \"CLASS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNotEmpty());
        }

        @Test
        @DisplayName("'description' is accepted as an alias for 'text'")
        void shouldAcceptDescriptionAlias() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\": \"User logs in to the system\", \"diagramType\": \"CLASS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNotEmpty());
        }

        @Test
        @DisplayName("'inputText' is accepted as an alias for 'text'")
        void shouldAcceptInputTextAlias() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"inputText\": \"User logs in to the system\", \"diagramType\": \"CLASS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").isNotEmpty());
        }

        @Test
        @DisplayName("all three aliases produce equivalent response shapes")
        void allThreeAliasesProduceEquivalentResponseShapes() throws Exception {
            for (String field : List.of("text", "description", "inputText")) {
                mockMvc.perform(post(GENERATE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"" + field + "\": \"A system with users and orders\", "
                                        + "\"diagramType\": \"SEQUENCE\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.id").isNotEmpty())
                        .andExpect(jsonPath("$.data.plantUmlCode").isNotEmpty());
            }
        }
    }

    // ── 3. Frontend type strings map to the correct backend DiagramType ───────

    @Nested
    @DisplayName("Frontend diagram type aliases map to the correct backend type")
    class TypeAliasMapping {

        @ParameterizedTest(name = "''{0}'' → PlantUML containing ''{1}''")
        @MethodSource("com.example.aidiagramgenerator.controller.ManualDiagramGenerationFlowTest#diagramTypeAliasMappings")
        @DisplayName("alias resolves to the expected template, verified via PlantUML code snippet")
        void shouldMapAliasToDiagramContainingKeyword(String inputType, String expectedSnippet)
                throws Exception {
            // Escape special chars for JSON string safety (all our values are clean)
            String body = "{\"diagramType\": \"" + inputType + "\"}";

            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.plantUmlCode", containsString(expectedSnippet)));
        }
    }

    // ── 4. Unsupported / invalid diagram types return 400 ─────────────────────

    @Nested
    @DisplayName("Unsupported diagram type returns 400 with a clear error message")
    class UnsupportedType {

        @Test
        @DisplayName("FLOWCHART returns 400")
        void shouldReturn400ForFlowchart() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"some process\", \"diagramType\": \"FLOWCHART\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("GANTT returns 400")
        void shouldReturn400ForGantt() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"project timeline\", \"diagramType\": \"GANTT\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("PIE_CHART with forceGenerate=true returns 400")
        void shouldReturn400ForPieChartWithForceGenerate() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"pie breakdown\", \"diagramType\": \"PIE_CHART\", "
                                    + "\"forceGenerate\": true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("forceGenerate=true without diagramType returns 400")
        void shouldReturn400ForForceGenerateWithoutDiagramType() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"some system\", \"forceGenerate\": true}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("error message contains the rejected type name")
        void errorMessageContainsRejectedTypeName() throws Exception {
            mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"something\", \"diagramType\": \"UNKNOWN_TYPE\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("UNKNOWN_TYPE")));
        }
    }

    // ── 5. Generated diagram ID is usable for PNG, SVG, and Draw.io export ───

    @Nested
    @DisplayName("Generated diagram ID enables all export formats")
    class ExportEndpoints {

        @Test
        @DisplayName("SEQUENCE template ID enables JSON, PNG, SVG, and Draw.io endpoints")
        void sequenceTemplateIdEnablesAllExportFormats() throws Exception {
            // Generate without text — uses DEFAULT_TEMPLATE, no AI needed
            MvcResult generateResult = mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"diagramType\": \"SEQUENCE\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(generateResult.getResponse().getContentAsString());
            String id = root.path("data").path("id").asText();
            assertThat(id).isNotBlank();

            // JSON metadata — same ID echoed back
            mockMvc.perform(get("/api/diagram/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.plantUmlCode").isNotEmpty());

            // PNG — image/png body
            mockMvc.perform(get("/api/diagram/{id}/png", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

            // SVG — image/svg+xml body
            mockMvc.perform(get("/api/diagram/{id}/svg", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.parseMediaType("image/svg+xml")));

            // Draw.io XML — application/xml containing <mxfile
            mockMvc.perform(get("/api/diagram/{id}/drawio", id))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(content().string(containsString("<mxfile")));
        }

        @Test
        @DisplayName("CLASS template ID enables all export formats")
        void classTemplateIdEnablesAllExportFormats() throws Exception {
            MvcResult generateResult = mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"diagramType\": \"CLASS\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            String id = objectMapper.readTree(generateResult.getResponse().getContentAsString())
                    .path("data").path("id").asText();
            assertThat(id).isNotBlank();

            mockMvc.perform(get("/api/diagram/{id}/png", id)).andExpect(status().isOk());
            mockMvc.perform(get("/api/diagram/{id}/svg", id)).andExpect(status().isOk());
            mockMvc.perform(get("/api/diagram/{id}/drawio", id))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<mxfile")));
        }

        @Test
        @DisplayName("Draw.io endpoint uses the same ID regardless of whether text was provided")
        void drawIoEndpointSharesIdWithGenerateResponse() throws Exception {
            // With text (falls back to template since AI unavailable in test env)
            MvcResult result = mockMvc.perform(post(GENERATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\": \"Users, Orders, and Products\", "
                                    + "\"diagramType\": \"ER\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            String id = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("id").asText();
            assertThat(id).isNotBlank();

            // The Draw.io endpoint must find this diagram (not 404)
            mockMvc.perform(get("/api/diagram/{id}/drawio", id))
                    .andExpect(status().isOk());
        }
    }
}
