package com.example.aidiagramgenerator.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Stage11SharingFrontendTest {

    @Test
    void staticFrontendContainsSharingContractsWithoutTokenStorageOrNewSvgInjection() throws Exception {
        String index = read("index.html");
        String routing = read("js/routing.jsx");
        String api = read("js/api.jsx");
        String sharing = read("js/sharing.jsx");
        String editor = read("js/editor.jsx");
        String app = read("js/app.jsx");
        String css = read("css/editor.css") + read("css/responsive.css");
        String frontend = index + routing + api + sharing + editor + app + css;

        assertThat(index)
                .contains("/js/sharing.jsx?v=11")
                .contains("/js/ai-assistant.jsx?v=10")
                .contains("/js/app.jsx?v=9");
        assertThat(index.indexOf("/js/sharing.jsx?v=11"))
                .isLessThan(index.indexOf("/js/editor.jsx?v=9"));

        assertThat(routing)
                .contains("parts[0] === 'share'")
                .contains("#/share/${params.token}");
        assertThat(app)
                .contains("PublicShareView")
                .contains("currentView.name === 'share'")
                .doesNotContain("currentView.name === 'share') { content = requireAuth");

        assertThat(api)
                .contains("/api/workspace/diagrams/${id}/shares")
                .contains("/api/public/shares/${encodeURIComponent(token)}")
                .contains("credentials: 'same-origin'");

        assertThat(sharing)
                .contains("Share Diagram")
                .contains("Version to share")
                .contains("Expiration")
                .contains("Allow PNG, SVG, and Draw.io downloads")
                .contains("Copy Link")
                .contains("Save this link now. It cannot be displayed again.")
                .contains("This shared diagram is unavailable or has expired.")
                .contains("Unsaved editor changes are not included")
                .contains("Shared read-only diagram")
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("versionNumber")
                .doesNotContain("editorSource")
                .doesNotContain("console.log")
                .doesNotContain("dangerouslySetInnerHTML");

        assertThat(editor)
                .contains("ShareDiagramModal")
                .contains("dirty={dirty}")
                .contains("setSharingOpen(true)");
        assertThat(css)
                .contains(".share-modal")
                .contains(".public-share-shell");

        assertThat(frontend)
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("jwt")
                .doesNotContain("jjwt")
                .doesNotContain("nimbus");
        assertThat(countOccurrences(readAllStaticFrontend(), "dangerouslySetInnerHTML")).isEqualTo(1);
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/resources/static").resolve(relativePath));
    }

    private static String readAllStaticFrontend() throws Exception {
        StringBuilder source = new StringBuilder(read("index.html"));
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/main/resources/static"))) {
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

    private static long countOccurrences(String source, String needle) {
        long count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
