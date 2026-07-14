package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage7FrontendContractTest {

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
    }

    @Test
    void csrfEndpointIsAvailableForAnonymousFrontendBootstrap() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginSessionCanAccessProjectsAndLogoutPreventsAccess() throws Exception {
        saveUser("frontend-user@example.com");
        MockHttpSession session = login("frontend-user@example.com");

        mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Frontend Project", "description", "Visible in dashboard"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Frontend Project"))
                .andExpect(jsonPath("$.diagramCount").value(0))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].name").value("Frontend Project"))
                .andExpect(jsonPath("$[0].diagramCount").value(0))
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                .andExpect(content().string(not(containsString("passwordHash"))));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attachAndDiagramDetailsExposeFrontendFields() throws Exception {
        saveUser("frontend-diagram@example.com");
        MockHttpSession session = login("frontend-diagram@example.com");
        UUID projectId = createProject(session);
        UUID generatedDiagramId = generatePublicDiagramId();

        MvcResult attachResult = mockMvc.perform(post("/api/projects/{projectId}/diagrams/{diagramId}/attach", projectId, generatedDiagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Attached Diagram", "description", "Saved from generator"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(generatedDiagramId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("Attached Diagram"))
                .andExpect(jsonPath("$.diagramType").value("Class Diagram"))
                .andExpect(jsonPath("$.sourceFormat").value("PLANTUML"))
                .andExpect(jsonPath("$.currentSourceCode").isNotEmpty())
                .andExpect(jsonPath("$.currentVersionNumber").value(1))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        UUID diagramId = UUID.fromString(read(attachResult).path("id").asText());

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}", diagramId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(diagramId.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.originalPrompt").exists())
                .andExpect(jsonPath("$.currentSourceCode").isNotEmpty())
                .andExpect(jsonPath("$.modelUsed").exists())
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void workspaceErrorShapeAndPublicGenerationRemainStable() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/diagram/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diagramType", "CLASS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void staticFrontendContainsStage7HelpersWithoutClientSideAuthStorageOrOwnerId() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));

        org.assertj.core.api.Assertions.assertThat(html)
                .contains("fetchCsrfToken")
                .contains("apiFetch")
                .contains("credentials: 'same-origin'")
                .contains("LoginView")
                .contains("RegisterView")
                .contains("DashboardView")
                .contains("ProjectDetailsView")
                .contains("DiagramEditorView")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("csrf(csrf -> csrf.disable())");
    }

    private ApplicationUser saveUser(String email) {
        return userRepository.saveAndFlush(new ApplicationUser(email, passwordEncoder.encode(PASSWORD)));
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

    private UUID createProject(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Workspace", "description", "Frontend tests"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID generatePublicDiagramId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/diagram/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diagramType", DiagramType.CLASS.name()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = read(result);
        return UUID.fromString(body.path("data").path("id").asText());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
