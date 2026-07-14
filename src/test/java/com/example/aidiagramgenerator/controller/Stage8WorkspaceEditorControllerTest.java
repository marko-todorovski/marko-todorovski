package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage8WorkspaceEditorControllerTest {

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
    void previewRequiresAuthenticationCsrfRendersSvgAndDoesNotPersist() throws Exception {
        saveUser("preview@example.com");
        MockHttpSession session = login("preview@example.com");

        mockMvc.perform(post("/api/workspace/preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("@startuml\nclass Preview\n@enduml")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/workspace/preview")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("@startuml\nclass Preview\n@enduml")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspace/preview")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("@startuml\nclass Preview\n@enduml")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")));

        assertThat(diagramRepository.count()).isZero();
        assertThat(versionRepository.count()).isZero();
    }

    @Test
    void previewRejectsInvalidAndOversizedSourcesSafely() throws Exception {
        saveUser("preview-invalid@example.com");
        MockHttpSession session = login("preview-invalid@example.com");

        mockMvc.perform(post("/api/workspace/preview")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("class Invalid")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspace/preview")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("@startuml\n" + "class A\n".repeat(20_000) + "@enduml")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versionContractSupportsEditorDuplicateWhitespaceMetadataAndRestoreFlows() throws Exception {
        saveUser("editor@example.com");
        MockHttpSession session = login("editor@example.com");
        UUID projectId = createProject(session);
        UUID diagramId = savePlantUmlDiagram(session, projectId, "Editable", "@startuml\nclass V1\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody("first edit", "@startuml\nclass V2\n@enduml")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody("first edit", "@startuml\nclass V2\n@enduml")))
                .andExpect(status().isNoContent());

        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(2);

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody("first edit", "@startuml\nclass V2\n\n@enduml")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(3));

        mockMvc.perform(put("/api/workspace/diagrams/{diagramId}/metadata", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Renamed Editable", "description", "Metadata only"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Editable"));

        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(3);

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}/restore", diagramId, 1)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(4))
                .andExpect(jsonPath("$.restoredVersion.changeType").value("RESTORED"));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions", diagramId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(4))
                .andExpect(jsonPath("$[0].sourceCode").doesNotExist());
    }

    @Test
    void staticFrontendContainsEditorContractsWithoutUnsafeAuthOrPreviewPatterns() throws Exception {
        String frontend = staticFrontendSource();

        assertThat(frontend)
                .contains("DiagramEditorView")
                .contains("apiFetchBlob")
                .contains("credentials: 'same-origin'")
                .contains("/api/workspace/preview")
                .contains("beforeunload")
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("window.setTimeout(() => renderPreview(editorSource), 650)")
                .contains("spellCheck=\"false\"")
                .contains("Editing and live preview are currently available for PlantUML diagrams only")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("csrf(csrf -> csrf.disable())");

        assertThat(frontend.indexOf("dangerouslySetInnerHTML"))
                .isEqualTo(frontend.lastIndexOf("dangerouslySetInnerHTML"));
    }

    private String staticFrontendSource() throws Exception {
        StringBuilder source = new StringBuilder();
        source.append(Files.readString(Path.of("src/main/resources/static/index.html")));
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/main/resources/static/js"))) {
            files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> {
                        try {
                            source.append('\n').append(Files.readString(path));
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
        return source.toString();
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
                        .content(json(Map.of("name", "Editor Project", "description", "Stage 8"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID savePlantUmlDiagram(MockHttpSession session, UUID projectId, String name, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/diagrams", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Editable diagram",
                                "originalPrompt", "Create editable diagram",
                                "diagramType", "CLASS",
                                "sourceFormat", "PLANTUML",
                                "sourceCode", sourceCode,
                                "modelUsed", "test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private String previewBody(String sourceCode) throws Exception {
        return json(Map.of("sourceCode", sourceCode, "outputFormat", "SVG"));
    }

    private String versionBody(String prompt, String sourceCode) throws Exception {
        return json(Map.of(
                "prompt", prompt,
                "sourceCode", sourceCode,
                "sourceFormat", "PLANTUML",
                "changeType", "EDITED",
                "modelUsed", "manual-editor"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
