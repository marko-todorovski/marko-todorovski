package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class WorkspaceApiControllerTest {

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
    void projectCrudRequiresAuthenticationCsrfAndUsesSessionOwner() throws Exception {
        ApplicationUser owner = saveUser("owner-project@example.com");
        ApplicationUser other = saveUser("other-project@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession otherSession = loginSession(other.getEmail());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/projects")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Missing CSRF"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects")
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        UUID ownerProjectId = createProject(ownerSession, "Banking System", "Core diagrams");
        createProject(otherSession, "Other User Project", null);

        mockMvc.perform(get("/api/projects").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownerProjectId.toString()))
                .andExpect(jsonPath("$[0].diagramCount").value(0))
                .andExpect(content().string(not(containsString("passwordHash"))));

        mockMvc.perform(get("/api/projects/{projectId}", ownerProjectId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Banking System"));

        mockMvc.perform(get("/api/projects/{projectId}", ownerProjectId).session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

        mockMvc.perform(put("/api/projects/{projectId}?ownerId={ownerId}", ownerProjectId, other.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Updated Banking", "description", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Banking"));

        mockMvc.perform(delete("/api/projects/{projectId}", ownerProjectId).session(ownerSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void maliciousOwnerIdInCreateProjectBodyDoesNotControlOwnership() throws Exception {
        ApplicationUser attacker = saveUser("attacker@example.com");
        ApplicationUser victim = saveUser("victim@example.com");
        MockHttpSession attackerSession = loginSession(attacker.getEmail());
        MockHttpSession victimSession = loginSession(victim.getEmail());

        mockMvc.perform(post("/api/projects?ownerId={victimId}", victim.getId())
                        .session(attackerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerId": "%s",
                                  "name": "Attack Project",
                                  "description": "Should belong to attacker"
                                }
                                """.formatted(victim.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Attack Project"));

        mockMvc.perform(get("/api/projects").session(attackerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Attack Project"));

        mockMvc.perform(get("/api/projects").session(victimSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void diagramWorkspaceFlowEnforcesOwnershipAndSerializationBoundaries() throws Exception {
        ApplicationUser owner = saveUser("diagram-owner@example.com");
        ApplicationUser other = saveUser("diagram-other@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession otherSession = loginSession(other.getEmail());
        UUID ownerProjectId = createProject(ownerSession, "Diagrams", null);
        UUID otherProjectId = createProject(otherSession, "Other Diagrams", null);

        UUID diagramId = savePlantUmlDiagram(ownerSession, ownerProjectId, "Account Class Diagram", "@startuml\nclass Account\n@enduml");

        assertThat(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagramId)).hasSize(1);

        mockMvc.perform(delete("/api/projects/{projectId}", ownerProjectId).session(ownerSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_EMPTY"));

        mockMvc.perform(post("/api/projects/{projectId}/diagrams", ownerProjectId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Mermaid",
                                "diagramType", "CLASS",
                                "sourceFormat", "MERMAID",
                                "sourceCode", "classDiagram\nclass A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DIAGRAM"))
                .andExpect(jsonPath("$.message").value(containsString("Mermaid")));

        mockMvc.perform(post("/api/projects/{projectId}/diagrams", ownerProjectId)
                        .session(otherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveDiagramBody("Attack", "@startuml\nclass Attack\n@enduml")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/{projectId}/diagrams", ownerProjectId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(diagramId.toString()))
                .andExpect(jsonPath("$[0].projectId").value(ownerProjectId.toString()))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("versions"))))
                .andExpect(content().string(not(containsString("owner"))));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}", diagramId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(diagramId.toString()))
                .andExpect(jsonPath("$.currentSourceCode").value("@startuml\nclass Account\n@enduml"));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}", diagramId).session(otherSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/workspace/diagrams/{diagramId}/metadata", diagramId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Renamed", "description", "Only metadata"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.currentVersionNumber").value(1));

        mockMvc.perform(put("/api/workspace/diagrams/{diagramId}/metadata", diagramId)
                        .session(otherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Attack", "description", "Nope"))))
                .andExpect(status().isNotFound());

        Diagram unowned = diagramRepository.saveAndFlush(new Diagram(
                "Generated public diagram",
                DiagramType.CLASS,
                "@startuml\nclass PublicGenerated\n@enduml"));

        mockMvc.perform(post("/api/projects/{projectId}/diagrams/{diagramId}/attach", ownerProjectId, unowned.getId())
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Attached", "description", "From generator"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unowned.getId().toString()))
                .andExpect(jsonPath("$.projectId").value(ownerProjectId.toString()))
                .andExpect(jsonPath("$.currentVersionNumber").value(1));

        UUID ownedByOther = savePlantUmlDiagram(otherSession, otherProjectId, "Other Owned", "@startuml\nclass Other\n@enduml");
        mockMvc.perform(post("/api/projects/{projectId}/diagrams/{diagramId}/attach", ownerProjectId, ownedByOther)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Steal", "description", "No"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/workspace/diagrams/{diagramId}", diagramId).session(otherSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/workspace/diagrams/{diagramId}", diagramId).session(ownerSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void versionHistoryReadCreateAndRestoreFlow() throws Exception {
        ApplicationUser owner = saveUser("version-owner@example.com");
        ApplicationUser other = saveUser("version-other@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession otherSession = loginSession(other.getEmail());
        UUID projectId = createProject(ownerSession, "Versions", null);
        UUID diagramId = savePlantUmlDiagram(ownerSession, projectId, "Versioned", "@startuml\nclass V1\n@enduml");

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "prompt", "manual edit",
                                "sourceCode", "@startuml\nclass V2\n@enduml",
                                "sourceFormat", "PLANTUML",
                                "changeType", "EDITED",
                                "modelUsed", "manual"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions", diagramId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].sourceCode").doesNotExist());

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}", diagramId, 1).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.sourceCode").value("@startuml\nclass V1\n@enduml"));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}", diagramId, 1).session(otherSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}/restore", diagramId, 1)
                        .session(ownerSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}/restore", diagramId, 0)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DIAGRAM_VERSION"));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions/{versionNumber}/restore", diagramId, 1)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.restoredVersion.sourceCode").value("@startuml\nclass V1\n@enduml"));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions", diagramId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}", diagramId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersionNumber").value(3))
                .andExpect(jsonPath("$.currentSourceCode").value("@startuml\nclass V1\n@enduml"));
    }

    @Test
    void publicGenerationAuthAndStaticCompatibilityRemainIntact() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/diagram/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diagramType", "CLASS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/diagrams/from-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "User sends login request", "diagramType", "SEQUENCE"))))
                .andExpect(status().isCreated());

        register("new-auth@example.com", "correct-password", "New", "Auth")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-auth@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private UUID createProject(MockHttpSession session, String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", description == null ? "" : description))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID savePlantUmlDiagram(MockHttpSession session, UUID projectId, String name, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/diagrams", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveDiagramBody(name, sourceCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentVersionNumber").value(1))
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private String saveDiagramBody(String name, String sourceCode) throws Exception {
        return json(Map.of(
                "name", name,
                "description", "Test diagram",
                "originalPrompt", "Create a class diagram",
                "diagramType", "CLASS",
                "sourceFormat", "PLANTUML",
                "sourceCode", sourceCode,
                "modelUsed", "test"));
    }

    private MockHttpSession loginSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email,
            String password,
            String firstName,
            String lastName) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", password,
                        "firstName", firstName,
                        "lastName", lastName))));
    }

    private ApplicationUser saveUser(String email) {
        ApplicationUser user = new ApplicationUser(email, passwordEncoder.encode(PASSWORD));
        user.setFirstName("Test");
        user.setLastName("User");
        return userRepository.saveAndFlush(user);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
