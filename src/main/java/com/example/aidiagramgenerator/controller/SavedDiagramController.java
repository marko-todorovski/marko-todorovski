package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.dto.request.UpdateDiagramMetadataRequest;
import com.example.aidiagramgenerator.dto.response.WorkspaceDiagramResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.SavedDiagramService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/diagrams")
public class SavedDiagramController {

    private final CurrentUser currentUser;
    private final SavedDiagramService savedDiagramService;

    public SavedDiagramController(CurrentUser currentUser, SavedDiagramService savedDiagramService) {
        this.currentUser = currentUser;
        this.savedDiagramService = savedDiagramService;
    }

    @GetMapping("/{diagramId}")
    public WorkspaceDiagramResponse getDiagram(@PathVariable UUID diagramId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return WorkspaceDiagramResponse.from(savedDiagramService.getDiagramForOwner(diagramId, ownerId));
    }

    @PutMapping("/{diagramId}/metadata")
    public WorkspaceDiagramResponse updateDiagramMetadata(
            @PathVariable UUID diagramId,
            @Valid @RequestBody UpdateDiagramMetadataRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Diagram diagram = savedDiagramService.updateDiagramMetadata(
                diagramId,
                ownerId,
                request.name(),
                request.description());
        return WorkspaceDiagramResponse.from(diagram);
    }

    @DeleteMapping("/{diagramId}")
    public ResponseEntity<Void> deleteDiagram(@PathVariable UUID diagramId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        savedDiagramService.deleteDiagram(diagramId, ownerId);
        return ResponseEntity.noContent().build();
    }
}
