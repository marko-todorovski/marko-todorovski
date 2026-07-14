package com.example.aidiagramgenerator.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Stage10AiAssistantFrontendTest {

    @Test
    void staticFrontendContainsAiAssistantContractsWithoutUnsafeClientPatterns() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));
        String api = Files.readString(Path.of("src/main/resources/static/js/api.jsx"));
        String assistant = Files.readString(Path.of("src/main/resources/static/js/ai-assistant.jsx"));
        String editor = Files.readString(Path.of("src/main/resources/static/js/editor.jsx"));
        String generator = Files.readString(Path.of("src/main/resources/static/js/generator.jsx"));
        String css = Files.readString(Path.of("src/main/resources/static/css/editor.css"))
                + Files.readString(Path.of("src/main/resources/static/css/responsive.css"));
        String frontend = index + api + assistant + editor + generator + css;

        assertThat(index).contains("/js/ai-assistant.jsx?v=10");
        assertThat(index.indexOf("/js/ai-assistant.jsx?v=10"))
                .isLessThan(index.indexOf("/js/editor.jsx?v=9"));

        assertThat(api)
                .contains("/api/workspace/diagrams/${id}/ai/explain")
                .contains("/api/workspace/diagrams/${id}/ai/suggestions")
                .contains("/api/workspace/diagrams/${id}/ai/modify")
                .contains("credentials: 'same-origin'");

        assertThat(assistant)
                .contains("Explain Diagram")
                .contains("Suggest Improvements")
                .contains("Modify with AI")
                .contains("sourceCode: editorSource")
                .contains("AuthApi.previewDiagram(sourceCode)")
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("Apply to Editor")
                .contains("Save as New Version")
                .contains("setEditorSource(proposal.sourceCode)")
                .contains("Proposal preview rendered.")
                .doesNotContain("dangerouslySetInnerHTML");

        assertThat(editor)
                .contains("AiAssistantPanel")
                .contains("saveAiVersion")
                .contains("changeType: 'AI_MODIFIED'");

        assertThat(css)
                .contains(".ai-comparison")
                .contains("grid-template-columns: 1fr");

        assertThat(frontend)
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("jwt")
                .doesNotContain("jjwt")
                .doesNotContain("nimbus");
        assertThat(countOccurrences(frontend, "dangerouslySetInnerHTML")).isEqualTo(1);
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
