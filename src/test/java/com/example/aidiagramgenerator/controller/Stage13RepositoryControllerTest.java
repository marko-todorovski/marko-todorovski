package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.RepositoryRepository;
import com.example.aidiagramgenerator.repository.RepositoryScanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage13RepositoryControllerTest {

    private static final String PASSWORD = "correct-password";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationUserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryScanRepository repositoryScanRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        repositoryScanRepository.deleteAll();
        repositoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void uploadingAZipScansItAndReportsMetadataWhileIgnoringNoiseDirectories() throws Exception {
        ApplicationUser owner = saveUser("repo-owner@example.com");
        MockHttpSession session = loginSession(owner.getEmail());

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("package.json", "{\"name\":\"sample-app\",\"dependencies\":{\"react\":\"^18.0.0\"}}");
        entries.put("src/index.tsx", "export const App = () => null;");
        entries.put("src/components/Widget.tsx", "export const Widget = () => null;");
        entries.put("node_modules/left-pad/index.js", "module.exports = function () {};");
        entries.put(".git/HEAD", "ref: refs/heads/main");
        byte[] zipBytes = buildZip(entries);
        MockMultipartFile file = new MockMultipartFile("file", "sample-app.zip", "application/zip", zipBytes);

        MvcResult createResult = mockMvc.perform(multipart("/api/repositories/zip")
                        .file(file)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("ZIP_UPLOAD"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn();
        UUID repositoryId = UUID.fromString(read(createResult).path("id").asText());

        mockMvc.perform(get("/api/repositories/{id}/scans/latest", repositoryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        MvcResult scansResult = mockMvc.perform(get("/api/repositories/{id}/scans", repositoryId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode scans = read(scansResult);
        assertThat(scans).hasSize(1);
        JsonNode scan = scans.get(0);
        assertThat(scan.path("primaryLanguage").asText()).isEqualTo("TYPESCRIPT");
        assertThat(scan.path("framework").asText()).isEqualTo("React");
        assertThat(scan.path("projectName").asText()).isEqualTo("sample-app");
        // node_modules and .git contents must never be counted.
        assertThat(scan.path("fileCount").asInt()).isEqualTo(3);
    }

    @Test
    void invalidGithubUrlIsRejectedBeforeAnyNetworkCall() throws Exception {
        ApplicationUser owner = saveUser("repo-owner-2@example.com");
        MockHttpSession session = loginSession(owner.getEmail());

        mockMvc.perform(post("/api/repositories/github")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("githubUrl", "https://evil.example.com/owner/repo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPOSITORY"));

        assertThat(repositoryRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId())).isEmpty();
    }

    @Test
    void zipSlipEntriesAreRejectedAndNoRepositoryIsLeftReady() throws Exception {
        ApplicationUser owner = saveUser("repo-owner-3@example.com");
        MockHttpSession session = loginSession(owner.getEmail());

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../../etc/passwd", "not-a-real-secret");
        byte[] zipBytes = buildZip(entries);
        MockMultipartFile file = new MockMultipartFile("file", "malicious.zip", "application/zip", zipBytes);

        MvcResult result = mockMvc.perform(multipart("/api/repositories/zip")
                        .file(file)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID repositoryId = UUID.fromString(read(result).path("id").asText());

        mockMvc.perform(get("/api/repositories/{id}/scans/latest", repositoryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void ownershipIsEnforcedForGetDeleteAndRescan() throws Exception {
        ApplicationUser owner = saveUser("repo-owner-4@example.com");
        ApplicationUser stranger = saveUser("repo-stranger@example.com");
        MockHttpSession ownerSession = loginSession(owner.getEmail());
        MockHttpSession strangerSession = loginSession(stranger.getEmail());

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("README.md", "hello");
        byte[] zipBytes = buildZip(entries);
        MockMultipartFile file = new MockMultipartFile("file", "readme-only.zip", "application/zip", zipBytes);
        MvcResult createResult = mockMvc.perform(multipart("/api/repositories/zip")
                        .file(file)
                        .session(ownerSession)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID repositoryId = UUID.fromString(read(createResult).path("id").asText());

        mockMvc.perform(get("/api/repositories/{id}", repositoryId).session(strangerSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/repositories/{id}", repositoryId).session(strangerSession).with(csrf()))
                .andExpect(status().isNotFound());

        // Rescan is GitHub-only; a ZIP-sourced repository must reject it even for its owner.
        mockMvc.perform(post("/api/repositories/{id}/scans", repositoryId).session(ownerSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPOSITORY"));

        mockMvc.perform(delete("/api/repositories/{id}", repositoryId).session(ownerSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/repositories/{id}", repositoryId).session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/repositories"))
                .andExpect(status().isUnauthorized());
    }

    private byte[] buildZip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
