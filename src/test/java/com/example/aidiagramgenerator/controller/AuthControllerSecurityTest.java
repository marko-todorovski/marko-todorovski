package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class AuthControllerSecurityTest {

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
    void validRegistrationCreatesUserWithEncodedPasswordAndAuthenticatedSession() throws Exception {
        MvcResult result = register("RegisterUser@example.com", "correct-password", "  Ada  ", "  Lovelace  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("registeruser@example.com"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("correct-password"))))
                .andReturn();

        ApplicationUser user = userRepository.findByEmailIgnoreCase("REGISTERUSER@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("correct-password");
        assertThat(passwordEncoder.matches("correct-password", user.getPasswordHash())).isTrue();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("registeruser@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register("duplicate@example.com", "correct-password", "Grace", "Hopper")
                .andExpect(status().isCreated());

        register("DUPLICATE@example.com", "another-password", "Grace", "Murray")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void invalidRegistrationRequestsAreRejected() throws Exception {
        register("not-an-email", "correct-password", "Alan", "Turing")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_REQUEST"));

        register("blank-password@example.com", "", "Alan", "Turing")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_REQUEST"));

        register("short-password@example.com", "short", "Alan", "Turing")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_REQUEST"));
    }

    @Test
    void validLoginIsCaseInsensitiveAndCreatesAuthenticatedSession() throws Exception {
        saveUser("login@example.com", "correct-password");

        MvcResult result = login("LOGIN@example.com", "correct-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("correct-password"))))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameGenericResponse() throws Exception {
        saveUser("known@example.com", "correct-password");

        login("known@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        login("unknown@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void repeatedFailedLoginsAreRateLimitedWithoutRevealingAccountExistence() throws Exception {
        saveUser("throttled@example.com", "correct-password");

        for (int i = 0; i < 5; i++) {
            login("throttled@example.com", "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        login("throttled@example.com", "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        // Even the correct password is now throttled - the response gives no hint that the
        // account exists or that the password would otherwise have been accepted.
        login("throttled@example.com", "correct-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        // An unknown account behind the same IP is throttled identically after five attempts -
        // the rate-limit response is indistinguishable from the known-account case above.
        for (int i = 0; i < 5; i++) {
            login("never-registered@example.com", "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login("never-registered@example.com", "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    void successfulLoginResetsFailedAttemptCounter() throws Exception {
        saveUser("resets@example.com", "correct-password");

        for (int i = 0; i < 4; i++) {
            login("resets@example.com", "wrong-password")
                    .andExpect(status().isUnauthorized());
        }

        login("resets@example.com", "correct-password")
                .andExpect(status().isOk());

        // Counter was cleared by the successful login, so four more failures are still allowed
        // rather than being immediately throttled.
        for (int i = 0; i < 4; i++) {
            login("resets@example.com", "wrong-password")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void logoutInvalidatesSessionAndUnauthenticatedLogoutRequiresAuthentication() throws Exception {
        saveUser("logout@example.com", "correct-password");
        MockHttpSession session = loginSession("logout@example.com", "correct-password");

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityRulesPreservePublicFrontendAndGenerationButProtectCurrentAndFutureRoutes() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/projects").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/diagrams").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/diagram/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagramType\":\"CLASS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void csrfCookieIsExposedAndUnsafeAuthenticatedRequestsRequireToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());

        saveUser("csrf@example.com", "correct-password");
        MockHttpSession session = loginSession("csrf@example.com", "correct-password");

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        session = loginSession("csrf@example.com", "correct-password");
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void sessionFixationProtectionChangesSessionIdOnLogin() throws Exception {
        saveUser("fixation@example.com", "correct-password");
        MockHttpSession existingSession = new MockHttpSession(null, "pre-auth-session");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(existingSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "fixation@example.com",
                                "password", "correct-password"))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.getId()).isNotEqualTo("pre-auth-session");
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email,
            String password,
            String firstName,
            String lastName) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password,
                        "firstName", firstName,
                        "lastName", lastName))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))));
    }

    private MockHttpSession loginSession(String email, String password) throws Exception {
        MvcResult result = login(email, password)
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ApplicationUser saveUser(String email, String rawPassword) {
        ApplicationUser user = new ApplicationUser(email, passwordEncoder.encode(rawPassword));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setFirstName("Test" + suffix);
        user.setLastName("User" + suffix);
        return userRepository.saveAndFlush(user);
    }
}
