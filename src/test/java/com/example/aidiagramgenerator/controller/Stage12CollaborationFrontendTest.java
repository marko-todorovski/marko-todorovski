package com.example.aidiagramgenerator.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Stage12CollaborationFrontendTest {

    private static final Path STATIC_ROOT = Path.of("src/main/resources/static");

    @Test
    void collaborationModuleAndInvitationRouteArePresentWithoutBrowserTokenStorage() throws Exception {
        String index = read("index.html");
        String routing = read("js/routing.jsx");
        String api = read("js/api.jsx");
        String auth = read("js/auth.jsx");
        String collaboration = read("js/collaboration.jsx");
        String projects = read("js/projects.jsx");
        String editor = read("js/editor.jsx");
        String app = read("js/app.jsx");
        String frontend = readAllStaticFrontend();

        assertThat(index)
                .contains("/js/collaboration.jsx?v=12")
                .contains("/js/sharing.jsx?v=11")
                .contains("/js/app.jsx?v=9");
        assertThat(index.indexOf("/js/collaboration.jsx?v=12"))
                .isGreaterThan(index.indexOf("/js/sharing.jsx?v=11"))
                .isLessThan(index.indexOf("/js/editor.jsx?v=9"));

        assertThat(routing)
                .contains("parts[0] === 'invitations'")
                .contains("#/invitations/${params.token}");
        assertThat(api)
                .contains("/api/projects/${projectId}/members")
                .contains("/api/projects/${projectId}/invitations")
                .contains("/api/invitations/${encodeURIComponent(token)}")
                .contains("credentials: 'same-origin'");
        assertThat(auth)
                .contains("namespace.returnAfterAuth")
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage");
        assertThat(app)
                .contains("InvitationLandingView")
                .contains("currentView.name === 'invitation'");

        assertThat(collaboration)
                .contains("ProjectCollaborationPanel")
                .contains("InviteMemberModal")
                .contains("InvitationLandingView")
                .contains("Copy this invitation link now. It cannot be displayed again.")
                .contains("Accept Invitation")
                .contains("Reject")
                .contains("roleLabel")
                .doesNotContain("console.log")
                .doesNotContain("tokenHash");
        assertThat(projects)
                .contains("ProjectCollaborationPanel")
                .contains("canEdit(project.currentUserRole)")
                .contains("roleLabel(project.currentUserRole)");
        assertThat(editor)
                .contains("You have {roleLabel(projectRole)} access. This diagram is read-only.")
                .contains("canEditDiagram")
                .contains("canShareDiagram")
                .contains("Load into Editor")
                .contains("AiAssistantPanel");

        assertThat(frontend)
                .doesNotContain("localStorage")
                .doesNotContain("sessionStorage")
                .doesNotContain("ownerId")
                .doesNotContain("tokenHash")
                .doesNotContain("jwt")
                .doesNotContain("jjwt")
                .doesNotContain("nimbus");
        assertThat(countOccurrences(frontend, "dangerouslySetInnerHTML")).isEqualTo(1);
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(STATIC_ROOT.resolve(relativePath));
    }

    private static String readAllStaticFrontend() throws Exception {
        StringBuilder source = new StringBuilder(read("index.html"));
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
