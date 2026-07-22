package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.ProjectInvitationStatus;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramShareRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectInvitationRepository;
import com.example.aidiagramgenerator.repository.ProjectMemberRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
class Stage12ProjectCollaborationControllerTest {

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
    private ProjectMemberRepository memberRepository;

    @Autowired
    private ProjectInvitationRepository invitationRepository;

    @Autowired
    private DomainDiagramRepository diagramRepository;

    @Autowired
    private DiagramVersionRepository versionRepository;

    @Autowired
    private DiagramShareRepository shareRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        shareRepository.deleteAll();
        versionRepository.deleteAll();
        diagramRepository.deleteAll();
        invitationRepository.deleteAll();
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerMembershipIsCreatedAndSharedProjectsAppearWithRoles() throws Exception {
        ApplicationUser owner = saveUser("owner-stage12@example.com");
        ApplicationUser editor = saveUser("editor-stage12@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession editorSession = loginSession(editor.getEmail());
        UUID projectId = createProject(ownerSession, "Team Workspace");

        assertThat(memberRepository.findByProjectIdAndUserId(projectId, owner.getId()))
                .get()
                .extracting(member -> member.getRole())
                .isEqualTo(ProjectRole.OWNER);

        String token = createInvitation(ownerSession, projectId, editor.getEmail(), "EDITOR").path("invitationToken").asText();

        mockMvc.perform(post("/api/invitations/{token}/accept", token).session(editorSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/invitations/{token}/accept", token).session(editorSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.role").value("EDITOR"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/projects").session(editorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(projectId.toString()))
                .andExpect(jsonPath("$[0].currentUserRole").value("EDITOR"))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        mockMvc.perform(get("/api/projects/{projectId}/members", projectId).session(editorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void invitationRulesProtectTokensEmailsAndOwnerOnlyCreation() throws Exception {
        ApplicationUser owner = saveUser("invite-owner@example.com");
        ApplicationUser editor = saveUser("invite-editor@example.com");
        ApplicationUser viewer = saveUser("invite-viewer@example.com");
        ApplicationUser stranger = saveUser("invite-stranger@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession editorSession = loginSession(editor.getEmail());
        MockHttpSession viewerSession = loginSession(viewer.getEmail());
        MockHttpSession strangerSession = loginSession(stranger.getEmail());
        UUID projectId = createProject(ownerSession, "Invitations");

        String editorToken = createInvitation(ownerSession, projectId, editor.getEmail(), "EDITOR").path("invitationToken").asText();
        mockMvc.perform(post("/api/invitations/{token}/accept", editorToken).session(editorSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/invitations", projectId)
                        .session(editorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "blocked@example.com", "role", "VIEWER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/projects/{projectId}/invitations", projectId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "owner-role@example.com", "role", "OWNER"))))
                .andExpect(status().isBadRequest());

        String viewerToken = createInvitation(ownerSession, projectId, viewer.getEmail(), "VIEWER").path("invitationToken").asText();
        assertThat(invitationRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId))
                .allSatisfy(invitation -> assertThat(invitation.getTokenHash()).hasSize(64));

        mockMvc.perform(get("/api/invitations/{token}", viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Invitations"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(content().string(not(containsString(viewer.getEmail()))))
                .andExpect(content().string(not(containsString("tokenHash"))));

        mockMvc.perform(post("/api/invitations/{token}/accept", viewerToken).session(strangerSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/invitations/{token}/reject", viewerToken).session(viewerSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/invitations/{token}", viewerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/{projectId}/invitations", projectId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("invitationToken"))))
                .andExpect(content().string(not(containsString("tokenHash"))));
    }

    @Test
    void memberManagementAndDiagramPermissionsFollowRoles() throws Exception {
        ApplicationUser owner = saveUser("perm-owner@example.com");
        ApplicationUser editor = saveUser("perm-editor@example.com");
        ApplicationUser viewer = saveUser("perm-viewer@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession editorSession = loginSession(editor.getEmail());
        MockHttpSession viewerSession = loginSession(viewer.getEmail());
        UUID projectId = createProject(ownerSession, "Permissions");
        UUID diagramId = saveDiagram(ownerSession, projectId, "Collaborative");
        acceptInvite(ownerSession, editorSession, projectId, editor.getEmail(), "EDITOR");
        acceptInvite(ownerSession, viewerSession, projectId, viewer.getEmail(), "VIEWER");

        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}", diagramId).session(viewerSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/workspace/diagrams/{diagramId}/versions", diagramId).session(viewerSession))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/workspace/diagrams/{diagramId}/metadata", diagramId)
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Viewer Edit", "description", "No"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_PERMISSION_DENIED"));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/versions", diagramId)
                        .session(editorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "prompt", "editor edit",
                                "sourceCode", "@startuml\nclass EditorEdit\n@enduml",
                                "sourceFormat", "PLANTUML",
                                "changeType", "EDITED",
                                "modelUsed", "manual"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdByDisplayName").value("Test User"));

        mockMvc.perform(post("/api/workspace/diagrams/{diagramId}/shares", diagramId)
                        .session(editorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("versionNumber", 1, "allowDownloads", true))))
                .andExpect(status().isForbidden());

        JsonNode members = read(mockMvc.perform(get("/api/projects/{projectId}/members", projectId).session(ownerSession))
                .andExpect(status().isOk())
                .andReturn());
        UUID editorMemberId = findMemberId(members, editor.getId());
        UUID viewerMemberId = findMemberId(members, viewer.getId());
        UUID ownerMemberId = findMemberId(members, owner.getId());

        mockMvc.perform(put("/api/projects/{projectId}/members/{memberId}/role", projectId, viewerMemberId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "EDITOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));

        mockMvc.perform(put("/api/projects/{projectId}/members/{memberId}/role", projectId, ownerMemberId)
                        .session(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "VIEWER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/{projectId}/members/{memberId}", projectId, ownerMemberId)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/projects/{projectId}/members/{memberId}", projectId, editorMemberId)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}", projectId).session(editorSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/{projectId}/leave", projectId).session(ownerSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{projectId}/leave", projectId).session(viewerSession).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void acceptInvite(MockHttpSession ownerSession, MockHttpSession inviteeSession, UUID projectId, String email, String role) throws Exception {
        String token = createInvitation(ownerSession, projectId, email, role).path("invitationToken").asText();
        mockMvc.perform(post("/api/invitations/{token}/accept", token).session(inviteeSession).with(csrf()))
                .andExpect(status().isOk());
    }

    private JsonNode createInvitation(MockHttpSession session, UUID projectId, String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/invitations", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", role, "expiresInDays", 7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationToken").exists())
                .andExpect(jsonPath("$.invitationUrl").value(containsString("#/invitations/")))
                .andReturn();
        return read(result);
    }

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", ""))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
    }

    private UUID saveDiagram(MockHttpSession session, UUID projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/{projectId}/diagrams", projectId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", "Test",
                                "originalPrompt", "Create class",
                                "diagramType", "CLASS",
                                "sourceFormat", "PLANTUML",
                                "sourceCode", "@startuml\nclass A\n@enduml",
                                "modelUsed", "test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).path("id").asText());
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

    private ApplicationUser saveUser(String email) {
        ApplicationUser user = new ApplicationUser(email, passwordEncoder.encode(PASSWORD));
        user.setFirstName("Test");
        user.setLastName("User");
        return userRepository.saveAndFlush(user);
    }

    private UUID findMemberId(JsonNode members, UUID userId) {
        for (JsonNode member : members) {
            if (userId.toString().equals(member.path("userId").asText())) {
                return UUID.fromString(member.path("id").asText());
            }
        }
        throw new AssertionError("Missing member " + userId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
