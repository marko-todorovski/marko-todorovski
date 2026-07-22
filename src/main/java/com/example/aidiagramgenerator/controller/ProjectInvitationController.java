package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.dto.response.InvitationDecisionResponse;
import com.example.aidiagramgenerator.dto.response.PublicInvitationMetadataResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.ProjectCollaborationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations/{token}")
public class ProjectInvitationController {

    private final CurrentUser currentUser;
    private final ProjectCollaborationService collaborationService;

    public ProjectInvitationController(CurrentUser currentUser, ProjectCollaborationService collaborationService) {
        this.currentUser = currentUser;
        this.collaborationService = collaborationService;
    }

    @GetMapping
    public PublicInvitationMetadataResponse metadata(@PathVariable String token) {
        return PublicInvitationMetadataResponse.from(collaborationService.getInvitationMetadata(token));
    }

    @PostMapping("/accept")
    public InvitationDecisionResponse accept(@PathVariable String token) {
        UUID userId = currentUser.requireCurrentUserId();
        return InvitationDecisionResponse.from(collaborationService.acceptInvitation(token, userId));
    }

    @PostMapping("/reject")
    public InvitationDecisionResponse reject(@PathVariable String token) {
        UUID userId = currentUser.requireCurrentUserId();
        return InvitationDecisionResponse.from(collaborationService.rejectInvitation(token, userId));
    }
}
