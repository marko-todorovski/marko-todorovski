package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.request.ExplainDiagramRequest;
import com.example.aidiagramgenerator.dto.request.ModifyDiagramRequest;
import com.example.aidiagramgenerator.dto.request.SuggestDiagramImprovementsRequest;
import com.example.aidiagramgenerator.dto.response.DiagramExplanationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramModificationResponse;
import com.example.aidiagramgenerator.dto.response.DiagramSuggestionsResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.DiagramAiAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/diagrams/{diagramId}/ai")
public class DiagramAiAssistantController {

    private final CurrentUser currentUser;
    private final DiagramAiAssistantService assistantService;

    public DiagramAiAssistantController(CurrentUser currentUser, DiagramAiAssistantService assistantService) {
        this.currentUser = currentUser;
        this.assistantService = assistantService;
    }

    @PostMapping("/explain")
    public DiagramExplanationResponse explain(
            @PathVariable UUID diagramId,
            @Valid @RequestBody ExplainDiagramRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return assistantService.explainDiagram(diagramId, ownerId, request.sourceCode());
    }

    @PostMapping("/suggestions")
    public DiagramSuggestionsResponse suggestions(
            @PathVariable UUID diagramId,
            @Valid @RequestBody SuggestDiagramImprovementsRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return assistantService.suggestImprovements(
                diagramId,
                ownerId,
                request.sourceCode(),
                request.diagramType(),
                request.focus());
    }

    @PostMapping("/modify")
    public DiagramModificationResponse modify(
            @PathVariable UUID diagramId,
            @Valid @RequestBody ModifyDiagramRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return assistantService.modifyDiagram(
                diagramId,
                ownerId,
                request.instruction(),
                request.sourceCode(),
                request.diagramType());
    }
}
