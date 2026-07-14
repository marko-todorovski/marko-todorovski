package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.dto.request.CreateDiagramVersionRequest;
import com.example.aidiagramgenerator.dto.response.DiagramVersionSummaryResponse;
import com.example.aidiagramgenerator.dto.response.RestoreVersionResponse;
import com.example.aidiagramgenerator.dto.response.WorkspaceDiagramVersionResponse;
import com.example.aidiagramgenerator.exception.InvalidDiagramVersionException;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.DiagramVersionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/diagrams/{diagramId}/versions")
public class DiagramVersionController {

    private final CurrentUser currentUser;
    private final DiagramVersionService diagramVersionService;

    public DiagramVersionController(CurrentUser currentUser, DiagramVersionService diagramVersionService) {
        this.currentUser = currentUser;
        this.diagramVersionService = diagramVersionService;
    }

    @GetMapping
    public List<DiagramVersionSummaryResponse> listVersions(@PathVariable UUID diagramId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return diagramVersionService.getVersionHistory(diagramId, ownerId).stream()
                .map(DiagramVersionSummaryResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<WorkspaceDiagramVersionResponse> createVersion(
            @PathVariable UUID diagramId,
            @Valid @RequestBody CreateDiagramVersionRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Optional<DiagramVersion> version = diagramVersionService.createVersionIfChanged(
                diagramId,
                ownerId,
                request.prompt(),
                request.sourceCode(),
                request.sourceFormat(),
                request.changeType() == null ? DiagramChangeType.EDITED : request.changeType(),
                request.modelUsed());
        return version
                        .map(saved -> ResponseEntity.created(URI.create(
                                "/api/workspace/diagrams/" + diagramId + "/versions/" + saved.getVersionNumber()))
                        .body(WorkspaceDiagramVersionResponse.from(saved)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{versionNumber}")
    public WorkspaceDiagramVersionResponse getVersion(
            @PathVariable UUID diagramId,
            @PathVariable int versionNumber) {
        validateVersionNumber(versionNumber);
        UUID ownerId = currentUser.requireCurrentUserId();
        return WorkspaceDiagramVersionResponse.from(diagramVersionService.getVersion(diagramId, ownerId, versionNumber));
    }

    @PostMapping("/{versionNumber}/restore")
    public RestoreVersionResponse restoreVersion(
            @PathVariable UUID diagramId,
            @PathVariable int versionNumber) {
        validateVersionNumber(versionNumber);
        UUID ownerId = currentUser.requireCurrentUserId();
        return RestoreVersionResponse.from(diagramVersionService.restoreVersion(diagramId, ownerId, versionNumber));
    }

    private static void validateVersionNumber(int versionNumber) {
        if (versionNumber <= 0) {
            throw new InvalidDiagramVersionException("Version number must be positive");
        }
    }
}
