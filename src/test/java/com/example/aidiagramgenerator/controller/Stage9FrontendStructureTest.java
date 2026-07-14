package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage9FrontendStructureTest {

    private static final Path STATIC_ROOT = Path.of("src/main/resources/static");

    @Autowired
    private WebApplicationContext context;

    @Test
    void indexIsThinAndLoadsExtractedFrontendModulesInOrder() throws Exception {
        String html = Files.readString(STATIC_ROOT.resolve("index.html"));

        assertThat(html.lines().count()).isLessThan(150);
        assertThat(html)
                .contains("<div id=\"root\"></div>")
                .contains("id=\"startup-error\"")
                .contains("window.AiDiagramApp")
                .contains("/css/app.css?v=9")
                .contains("/css/auth.css?v=9")
                .contains("/css/workspace.css?v=9")
                .contains("/css/editor.css?v=9")
                .contains("/css/responsive.css?v=9")
                .contains("/js/api.jsx?v=9")
                .contains("/js/shared.jsx?v=9")
                .contains("/js/routing.jsx?v=9")
                .contains("/js/auth.jsx?v=9")
                .contains("/js/projects.jsx?v=9")
                .contains("/js/ai-assistant.jsx?v=10")
                .contains("/js/editor.jsx?v=9")
                .contains("/js/generator.jsx?v=9")
                .contains("/js/app.jsx?v=9")
                .doesNotContain("function App")
                .doesNotContain("const AuthApi")
                .doesNotContain("<style>");

        assertInOrder(html, List.of(
                "/js/api.jsx?v=9",
                "/js/shared.jsx?v=9",
                "/js/routing.jsx?v=9",
                "/js/auth.jsx?v=9",
                "/js/projects.jsx?v=9",
                "/js/ai-assistant.jsx?v=10",
                "/js/editor.jsx?v=9",
                "/js/generator.jsx?v=9",
                "/js/app.jsx?v=9"
        ));
    }

    @Test
    void referencedStaticFilesExistAndAreServedToAnonymousUsers() throws Exception {
        String html = Files.readString(STATIC_ROOT.resolve("index.html"));
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Matcher matcher = Pattern.compile("\"(/(?:css|js)/[^\"]+\\.(?:css|jsx))\\?v=\\d+\"").matcher(html);
        int referenceCount = 0;

        while (matcher.find()) {
            referenceCount++;
            String staticPath = matcher.group(1);
            assertThat(Files.exists(STATIC_ROOT.resolve(staticPath.substring(1)))).isTrue();
            mockMvc.perform(get(staticPath))
                    .andExpect(status().isOk());
        }

        assertThat(referenceCount).isEqualTo(14);
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isEqualTo("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/app.jsx?v=9")));
        mockMvc.perform(get("/js/missing.jsx"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modulesKeepExpectedResponsibilitiesAndDoNotUseClientSideIdentityStorage() throws Exception {
        String api = read("js/api.jsx");
        String auth = read("js/auth.jsx");
        String projects = read("js/projects.jsx");
        String aiAssistant = read("js/ai-assistant.jsx");
        String editor = read("js/editor.jsx");
        String generator = read("js/generator.jsx");
        String app = read("js/app.jsx");
        String allFrontend = readAllStaticFrontend();

        assertThat(api)
                .contains("fetchCsrfToken")
                .contains("apiFetch")
                .contains("apiFetchBlob")
                .contains("/api/workspace/preview")
                .contains("credentials: 'same-origin'")
                .contains("namespace.modules.api = AuthApi");
        assertThat(auth)
                .contains("LoginView")
                .contains("RegisterView")
                .contains("namespace.modules.auth");
        assertThat(projects)
                .contains("DashboardView")
                .contains("ProjectDetailsView")
                .contains("SaveDiagramDialog")
                .contains("namespace.modules.projects");
        assertThat(aiAssistant)
                .contains("AiAssistantPanel")
                .contains("namespace.modules.aiAssistant");
        assertThat(editor)
                .contains("DiagramEditorView")
                .contains("beforeunload")
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("window.setTimeout(() => renderPreview(editorSource), 650)")
                .contains("spellCheck=\"false\"")
                .contains("restoreVersion")
                .contains("Editing and live preview are currently available for PlantUML diagrams only");
        assertThat(generator)
                .contains("GeneratorView")
                .contains("ResultMetadata")
                .contains("dangerouslySetInnerHTML");
        assertThat(app)
                .contains("requireModule('api')")
                .contains("requireModule('generator')")
                .contains("showStartupError")
                .contains("ReactDOM.createRoot");

        assertThat(allFrontend)
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("csrf(csrf -> csrf.disable())")
                .doesNotContain("jjwt")
                .doesNotContain("nimbus");
        assertThat(countOccurrences(allFrontend, "dangerouslySetInnerHTML")).isEqualTo(1);
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(STATIC_ROOT.resolve(relativePath));
    }

    private String readAllStaticFrontend() throws Exception {
        StringBuilder source = new StringBuilder(Files.readString(STATIC_ROOT.resolve("index.html")));
        try (java.util.stream.Stream<Path> files = Files.walk(STATIC_ROOT)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".css") || path.toString().endsWith(".jsx"))
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

    private void assertInOrder(String source, List<String> snippets) {
        int previous = -1;
        for (String snippet : snippets) {
            int next = source.indexOf(snippet);
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }

    private long countOccurrences(String source, String needle) {
        long count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
