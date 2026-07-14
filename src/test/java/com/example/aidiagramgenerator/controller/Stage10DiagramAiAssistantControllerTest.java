package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import com.example.aidiagramgenerator.service.DiagramAiAssistantClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage10DiagramAiAssistantControllerTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationUserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DomainDiagramRepository diagramRepository;

    @Autowired
    private DiagramVersionRepository versionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DiagramAiAssistantClient assistantClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        versionRepository.deleteAll();
        diagramRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
        reset(assistantClient);
        when(assistantClient.getModelName()).thenReturn("mock-ai");
    }

    @Test
    void aiEndpointsRequireAuthenticationOwnershipAndCsrf() throws Exception {
        ApplicationUser owner = saveUser("ai-owner@example.com");
        ApplicationUser other = saveUser("ai-other@example.com");
        MockHttpSession ownerSession = login(owner.getEmail());
        MockHttpSession otherSession = login(other.getEmail());
        UUID projectId = createProject(ownerSession);
        UUID diagramId = saveDiagram(ownerSession, projectId, "@startuml\nclass Saved\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass X\n@enduml"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass X\n@enduml"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(otherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass X\n@enduml"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void explainAndSuggestionsUseCurrentRequestSourceAndDoNotPersist() throws Exception {
        ApplicationUser owner = saveUser("ai-explain@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "@startuml\nclass DatabaseSaved\n@enduml");
        when(assistantClient.generateStructuredResponse(anyString()))
                .thenReturn("""
                        {"summary":"Explains unsaved","elements":[{"name":"Unsaved","type":"class","description":"from editor"}],"relationships":[],"flow":"simple","risks":[]}
                        """)
                .thenReturn("""
                        {"summary":"Two suggestions","suggestions":[{"title":"Add API","description":"Connect API to service","priority":"MEDIUM"}]}
                        """);

        String unsaved = "@startuml\nclass Unsaved\n@enduml";
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", unsaved))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Explains unsaved"))
                .andExpect(jsonPath("$.elements[0].name").value("Unsaved"))
                .andExpect(content().string(not(containsString("system prompt"))));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/suggestions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", unsaved, "diagramType", "CLASS", "focus", "architecture"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title").value("Add API"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(assistantClient, org.mockito.Mockito.times(2)).generateStructuredResponse(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(0)).contains("class Unsaved").doesNotContain("class DatabaseSaved");
        assertThat(diagramRepository.findById(diagramId).orElseThrow().getCurrentSourceCode()).contains("DatabaseSaved");
        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(1);
    }

    @Test
    void modifyValidatesInputOutputStripsFencesAndDoesNotPersist() throws Exception {
        ApplicationUser owner = saveUser("ai-modify@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "@startuml\nclass Account\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("instruction", "", "sourceCode", "@startuml\nclass A\n@enduml", "diagramType", "CLASS"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("instruction", "Add B", "sourceCode", "", "diagramType", "CLASS"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("instruction", "Add B", "sourceCode", "@startuml\n!includeurl https://example.com/x.puml\n@enduml", "diagramType", "CLASS"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSAFE_PLANTUML"));

        when(assistantClient.generateStructuredResponse(anyString()))
                .thenReturn("{\"sourceCode\":\"class MissingWrapper\",\"summary\":\"bad\",\"warnings\":[]}");
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyBody("Add B", "@startuml\nclass Account\n@enduml")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AI_OUTPUT_INVALID"));

        when(assistantClient.generateStructuredResponse(anyString()))
                .thenReturn("""
                        {"sourceCode":"```plantuml\\n@startuml\\nclass Account\\nclass PaymentService\\nAccount --> PaymentService\\n@enduml\\n```","summary":"Added PaymentService","warnings":[]}
                        """);
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyBody("Add PaymentService", "@startuml\nclass Account\n@enduml")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value(containsString("PaymentService")))
                .andExpect(jsonPath("$.sourceCode").value(not(containsString("```"))))
                .andExpect(jsonPath("$.modelUsed").value("mock-ai"));

        assertThat(diagramRepository.findById(diagramId).orElseThrow().getCurrentSourceCode()).isEqualTo("@startuml\nclass Account\n@enduml");
        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(1);
    }

    @Test
    void providerFailuresRateLimitAndOversizedRequestsMapSafely() throws Exception {
        ApplicationUser owner = saveUser("ai-errors@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "@startuml\nclass Account\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/modify", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modifyBody("x".repeat(2_001), "@startuml\nclass Account\n@enduml")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\n" + "class A\n".repeat(20_000) + "@enduml"))))
                .andExpect(status().isBadRequest());

        doThrow(new AiServiceException("request timeout"))
                .when(assistantClient).generateStructuredResponse(anyString());
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass Account\n@enduml"))))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"));

        doThrow(new AiServiceException("provider down"))
                .when(assistantClient).generateStructuredResponse(anyString());
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/explain", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass Account\n@enduml"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

        ApplicationUser limited = saveUser("ai-limit@example.com");
        MockHttpSession limitedSession = login(limited.getEmail());
        UUID limitedProject = createProject(limitedSession);
        UUID limitedDiagram = saveDiagram(limitedSession, limitedProject, "@startuml\nclass Limit\n@enduml");
        doReturn("{\"summary\":\"ok\",\"suggestions\":[]}")
                .when(assistantClient).generateStructuredResponse(anyString());
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/suggestions", limitedDiagram)
                            .session(limitedSession)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sourceCode", "@startuml\nclass Limit\n@enduml", "diagramType", "CLASS", "focus", "general"))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/ai/suggestions", limitedDiagram)
                        .session(limitedSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceCode", "@startuml\nclass Limit\n@enduml", "diagramType", "CLASS", "focus", "general"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"));
    }

    @Test
    void aiProposalCanBeSavedAsAiModifiedVersionThroughExistingVersionEndpoint() throws Exception {
        ApplicationUser owner = saveUser("ai-version@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "@startuml\nclass Account\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "prompt", "AI instruction: Add PaymentService",
                                "sourceCode", "@startuml\nclass Account\nclass PaymentService\n@enduml",
                                "sourceFormat", "PLANTUML",
                                "changeType", "AI_MODIFIED",
                                "modelUsed", "mock-ai"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.changeType").value("AI_MODIFIED"));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "prompt", "AI instruction: Add PaymentService",
                                "sourceCode", "@startuml\nclass Account\nclass PaymentService\n@enduml",
                                "sourceFormat", "PLANTUML",
                                "changeType", "AI_MODIFIED",
                                "modelUsed", "mock-ai"))))
                .andExpect(status().isNoContent());

        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(2);
        assertThat(diagramRepository.findById(diagramId).orElseThrow().getCurrentVersionNumber()).isEqualTo(2);
        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId).get(0).getChangeType())
                .isEqualTo(DiagramChangeType.AI_MODIFIED);
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "AI Project"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID saveDiagram(MockHttpSession session, UUID projectId, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/diagrams", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "AI Diagram",
                                "originalPrompt", "Create diagram",
                                "diagramType", "CLASS",
                                "sourceFormat", "PLANTUML",
                                "sourceCode", sourceCode,
                                "modelUsed", "test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ApplicationUser saveUser(String email) {
        return userRepository.saveAndFlush(new ApplicationUser(email, passwordEncoder.encode(PASSWORD)));
    }

    private String modifyBody(String instruction, String sourceCode) throws Exception {
        return json(Map.of("instruction", instruction, "sourceCode", sourceCode, "diagramType", "CLASS"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
