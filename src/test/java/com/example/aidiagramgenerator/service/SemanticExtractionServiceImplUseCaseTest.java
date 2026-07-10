package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.SemanticModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticExtractionServiceImplUseCaseTest {

    private final SemanticExtractionServiceImpl service = new SemanticExtractionServiceImpl(new AiModelService() {
        @Override
        public String getModelName() {
            return "fallback-test";
        }

        @Override
        public String generateStructuredResponse(String prompt) {
            throw new AiServiceException("force heuristic fallback");
        }
    });

    @Test
    void extractsEducationalUseCaseActorsActionsAndRelationships() {
        String text = """
                Create a use case diagram for an education system.
                Actors include Student, Administrator, Guest, and Professor.
                The student can login, download materials, view grades, and search news.
                Download materials includes login.
                Search news extends view grades when the student wants announcements.
                Download materials must login first.
                View grades might send notification.
                """;

        SemanticModel model = service.extract(text);

        assertTrue(model.getEntities().stream().anyMatch(e -> e.getName().equals("Student")));
        assertTrue(model.getEntities().stream().anyMatch(e -> e.getName().equals("Administrator")));
        assertTrue(model.getActions().contains("login"));
        assertTrue(model.getActions().contains("download materials"));
        assertTrue(model.getActions().contains("view grades"));
        assertTrue(model.getActions().contains("search news"));
        assertTrue(model.getRelationships().stream().anyMatch(r ->
                r.getSource().equals("Student")
                        && r.getTarget().equals("download materials")
                        && r.getType().equals("association")));
        assertTrue(model.getRelationships().stream().anyMatch(r ->
                r.getSource().equals("download materials")
                        && r.getTarget().equals("login")
                        && r.getType().equals("include")));
        assertTrue(model.getRelationships().stream().anyMatch(r ->
                r.getSource().equals("search news")
                        && r.getTarget().equals("view grades")
                        && r.getType().equals("extend")));
        assertTrue(model.getRelationships().stream().anyMatch(r ->
                r.getSource().equals("download materials")
                        && r.getTarget().equals("login")
                        && r.getType().equals("include")));
        assertTrue(model.getRelationships().stream().anyMatch(r ->
                r.getSource().equals("view grades")
                        && r.getTarget().equals("send notification")
                        && r.getType().equals("extend")));
    }
}
