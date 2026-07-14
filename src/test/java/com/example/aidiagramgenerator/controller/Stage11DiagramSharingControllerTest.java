package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramShareRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import com.example.aidiagramgenerator.service.DiagramShareTokenService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage11DiagramSharingControllerTest {

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
    private DiagramShareRepository shareRepository;

    @Autowired
    private DiagramShareTokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        shareRepository.deleteAll();
        versionRepository.deleteAll();
        diagramRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCreateListRevokeAndPublicAvailabilityFlow() throws Exception {
        ApplicationUser owner = saveUser("share-owner@example.com");
        ApplicationUser other = saveUser("share-other@example.com");
        MockHttpSession ownerSession = login(owner.getEmail());
        MockHttpSession otherSession = login(other.getEmail());
        UUID projectId = createProject(ownerSession);
        UUID diagramId = saveDiagram(ownerSession, projectId, "Shared Diagram", "@startuml\nclass V1\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("allowDownloads", true))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("allowDownloads", true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(otherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("allowDownloads", true))))
                .andExpect(status().isNotFound());

        MvcResult createdResult = mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares?ownerId={ownerId}", diagramId, other.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "allowDownloads", true,
                                "titleOverride", "Public Title",
                                "descriptionOverride", "Public description"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.publicUrl").value(containsString("#/share/")))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        JsonNode created = read(createdResult);
        String rawToken = created.path("token").asText();
        UUID shareId = UUID.fromString(created.path("shareId").asText());
        DiagramShare stored = shareRepository.findById(shareId).orElseThrow();
        assertThat(stored.getTokenHash()).isEqualTo(tokenService.hashToken(rawToken));
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/shares", diagramId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares[0].shareId").value(shareId.toString()))
                .andExpect(jsonPath("$.shares[0].versionNumber").value(1))
                .andExpect(jsonPath("$.shares[0].token").doesNotExist())
                .andExpect(jsonPath("$.shares[0].tokenHash").doesNotExist())
                .andExpect(content().string(not(containsString(rawToken))))
                .andExpect(content().string(not(containsString(stored.getTokenHash()))));

        mockMvc.perform(get("/api/public/shares/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Public Title"))
                .andExpect(jsonPath("$.description").value("Public description"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.allowDownloads").value(true))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.diagramId").doesNotExist())
                .andExpect(jsonPath("$.sourceCode").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(shareRepository.findById(shareId).orElseThrow().getAccessCount()).isEqualTo(1);

        mockMvc.perform(get("/api/public/shares/{token}/preview", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("image/svg+xml"));
        assertThat(shareRepository.findById(shareId).orElseThrow().getAccessCount()).isEqualTo(1);

        mockMvc.perform(get("/api/public/shares/{token}/download?format=svg", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("public-title-v1.svg")));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares/{shareId}/revoke", diagramId, shareId)
                        .session(otherSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares/{shareId}/revoke", diagramId, shareId)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        assertThat(shareRepository.findById(shareId)).isPresent();

        mockMvc.perform(get("/api/public/shares/{token}", rawToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("This shared diagram is unavailable."));
        mockMvc.perform(get("/api/public/shares/{token}", "unknown-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("This shared diagram is unavailable."));
    }

    @Test
    void historicalSharesArePinnedAndDownloadPermissionIsEnforced() throws Exception {
        ApplicationUser owner = saveUser("share-history@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "History", "@startuml\nclass V1\n@enduml");

        createVersion(session, diagramId, "@startuml\nclass V2\n@enduml");
        String historicalToken = createShare(session, diagramId, Map.of("versionNumber", 1, "allowDownloads", false));

        mockMvc.perform(get("/api/public/shares/{token}", historicalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.allowDownloads").value(false));
        mockMvc.perform(get("/api/public/shares/{token}/download?format=svg", historicalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DOWNLOADS_DISABLED"));

        createVersion(session, diagramId, "@startuml\nclass V3\n@enduml");
        mockMvc.perform(get("/api/public/shares/{token}", historicalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1));

        MvcResult preview = mockMvc.perform(get("/api/public/shares/{token}/preview", historicalToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(preview.getResponse().getContentAsString()).contains("V1").doesNotContain("V3");
    }

    @Test
    void validationExpirationDeletionAndStaticBoundariesHold() throws Exception {
        ApplicationUser owner = saveUser("share-validation@example.com");
        MockHttpSession session = login(owner.getEmail());
        UUID projectId = createProject(session);
        UUID diagramId = saveDiagram(session, projectId, "Validation", "@startuml\nclass V1\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expiresAt", Instant.now().minus(1, ChronoUnit.DAYS).toString()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("titleOverride", "x".repeat(151)))))
                .andExpect(status().isBadRequest());

        String token = createShare(session, diagramId, Map.of("expiresAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()));
        DiagramShare share = shareRepository.findByTokenHash(tokenService.hashToken(token)).orElseThrow();
        share.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        shareRepository.saveAndFlush(share);

        mockMvc.perform(get("/api/public/shares/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_AVAILABLE"));

        String deleteToken = createShare(session, diagramId, Map.of("allowDownloads", true));
        mockMvc.perform(delete("/api/workspace/diagrams/{diagramId}", diagramId).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/shares/{token}", deleteToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_AVAILABLE"));

        String ownerController = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/aidiagramgenerator/controller/DiagramShareController.java"));
        String publicController = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/aidiagramgenerator/controller/PublicDiagramShareController.java"));
        assertThat(ownerController).doesNotContain("Repository").doesNotContain("@Transactional").doesNotContain("@RequestParam").doesNotContain("@RequestHeader");
        assertThat(publicController).doesNotContain("Repository").doesNotContain("@Transactional").doesNotContain("@RequestParam UUID ownerId").doesNotContain("@RequestHeader");
    }

    private UUID createProject(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Share Project"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID saveDiagram(MockHttpSession session, UUID projectId, String name, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/diagrams", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Shared test diagram",
                                "originalPrompt", "Create diagram",
                                "diagramType", "CLASS",
                                "sourceFormat", "PLANTUML",
                                "sourceCode", sourceCode,
                                "modelUsed", "test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private void createVersion(MockHttpSession session, UUID diagramId, String sourceCode) throws Exception {
        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "prompt", "edit",
                                "sourceCode", sourceCode,
                                "sourceFormat", "PLANTUML",
                                "changeType", "EDITED",
                                "modelUsed", "manual"))))
                .andExpect(status().isCreated());
    }

    private String createShare(MockHttpSession session, UUID diagramId, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).path("token").asText();
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
        ApplicationUser user = new ApplicationUser(email, passwordEncoder.encode(PASSWORD));
        return userRepository.saveAndFlush(user);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
