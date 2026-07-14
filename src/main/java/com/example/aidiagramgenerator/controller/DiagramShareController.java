package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.dto.request.CreateDiagramShareRequest;
import com.example.aidiagramgenerator.dto.response.CreatedDiagramShareResponse;
import com.example.aidiagramgenerator.dto.response.DiagramShareListResponse;
import com.example.aidiagramgenerator.dto.response.DiagramShareSummaryResponse;
import com.example.aidiagramgenerator.dto.response.RevokedDiagramShareResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.DiagramShareService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/diagrams/{diagramId}/shares")
public class DiagramShareController {

    private final CurrentUser currentUser;
    private final DiagramShareService shareService;
    private final Clock clock;

    public DiagramShareController(CurrentUser currentUser, DiagramShareService shareService, Clock clock) {
        this.currentUser = currentUser;
        this.shareService = shareService;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<CreatedDiagramShareResponse> createShare(
            @PathVariable UUID diagramId,
            @Valid @RequestBody CreateDiagramShareRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        DiagramShareService.CreatedShare created = shareService.createShare(diagramId, ownerId, request);
        String publicUrl = "/#/share/" + created.rawToken();
        return ResponseEntity.created(URI.create("/api/workspace/diagrams/" + diagramId + "/shares/" + created.share().getId()))
                .body(CreatedDiagramShareResponse.from(created.share(), created.rawToken(), publicUrl, clock));
    }

    @GetMapping
    public DiagramShareListResponse listShares(@PathVariable UUID diagramId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return new DiagramShareListResponse(shareService.listShares(diagramId, ownerId).stream()
                .map(share -> DiagramShareSummaryResponse.from(share, clock))
                .toList());
    }

    @PostMapping("/{shareId}/revoke")
    public RevokedDiagramShareResponse revokeShare(
            @PathVariable UUID diagramId,
            @PathVariable UUID shareId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        DiagramShare share = shareService.revokeShare(diagramId, shareId, ownerId);
        return RevokedDiagramShareResponse.from(share);
    }
}
